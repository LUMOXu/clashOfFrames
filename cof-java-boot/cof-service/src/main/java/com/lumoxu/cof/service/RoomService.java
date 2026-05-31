package com.lumoxu.cof.service;

import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameCore;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.engine.Room;
import com.lumoxu.cof.service.model.CardLibraryDto;
import com.lumoxu.cof.service.model.ComputerPlayerDto;
import com.lumoxu.cof.service.model.RoomChatMessage;
import com.lumoxu.cof.service.model.RoomState;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import com.lumoxu.cof.service.redis.RedisKeys;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private final JsonRedisOps redis;
    private final MetaService metaService;
    private final DeckCatalogService deckCatalogService;
    private final GameRuntimeService gameRuntimeService;
    private final ComputerPlayerService computerPlayerService;
    private final UserStatsService userStatsService;
    private final PlayerRoomService playerRoomService;

    public RoomService(
            JsonRedisOps redis,
            MetaService metaService,
            DeckCatalogService deckCatalogService,
            GameRuntimeService gameRuntimeService,
            ComputerPlayerService computerPlayerService,
            UserStatsService userStatsService,
            PlayerRoomService playerRoomService) {
        this.redis = redis;
        this.metaService = metaService;
        this.deckCatalogService = deckCatalogService;
        this.gameRuntimeService = gameRuntimeService;
        this.computerPlayerService = computerPlayerService;
        this.userStatsService = userStatsService;
        this.playerRoomService = playerRoomService;
    }

    public RoomState createRoom(String hostId, String hostUsername, GameSettings settings, List<String> computerIds) {
        RoomState room = new RoomState();
        room.id = allocateRoomId();
        room.hostId = hostId;
        room.players.add(hostId);
        rememberUsername(room, hostId, hostUsername);
        room.settings = normalizeSettings(settings);
        room.createdAt = System.currentTimeMillis();
        if (computerIds != null) {
            for (String computerId : computerIds) {
                addComputer(room, computerId);
            }
        }
        deckCatalogService.warmRoomCatalog(room.id, room.settings);
        save(room);
        playerRoomService.bind(hostId, room.id);
        return room;
    }

    public RoomState updateSettings(RoomState room, GameSettings settings) {
        room.settings = normalizeSettings(settings);
        deckCatalogService.warmRoomCatalog(room.id, room.settings);
        save(room);
        return room;
    }

    public RoomState getRequired(String roomId) {
        return get(roomId).orElseThrow(() -> new CofException(ErrorCode.NOT_FOUND, "房间不存在。"));
    }

    public java.util.Optional<RoomState> get(String roomId) {
        return redis.get(RedisKeys.room(roomId), RoomState.class);
    }

    public List<Map<String, Object>> listRooms(String clientId, boolean includePrivate) {
        return redis.setMembers(RedisKeys.ROOM_INDEX).stream()
                .map(this::get)
                .flatMap(java.util.Optional::stream)
                .filter(room -> includePrivate || room.settings == null || room.settings.isPublic)
                .filter(room -> "waiting".equals(room.status) || "loading".equals(room.status))
                .map(this::summary)
                .collect(Collectors.toList());
    }

    public Map<String, Object> join(RoomState room, String clientId, String username) {
        if ("waiting".equals(room.status)) {
            if (!room.players.contains(clientId)) {
                if (room.players.size() >= room.settings.maxPlayers) {
                    throw new CofException(ErrorCode.CONFLICT, "房间人数已满。");
                }
                room.players.add(clientId);
                rememberUsername(room, clientId, username);
                playerRoomService.bind(clientId, room.id);
            }
            room.spectators.remove(clientId);
            deckCatalogService.warmRoomCatalog(room.id, room.settings);
            save(room);
            Map<String, Object> result = new HashMap<>();
            result.put("room", summary(room));
            result.put("spectator", false);
            return result;
        }
        if (room.gameId != null) {
            Game game = gameRuntimeService.getRequired(room.gameId).game;
            boolean inGame = game.players.stream().anyMatch(p -> clientId.equals(p.clientId));
            if (inGame) {
                playerRoomService.bind(clientId, room.id);
                Map<String, Object> result = new HashMap<>();
                result.put("room", summary(room));
                result.put("game", gameRuntimeService.toPublicGame(game));
                result.put("spectator", false);
                return result;
            }
        }
        if (!room.spectators.contains(clientId)) {
            room.spectators.add(clientId);
        }
        save(room);
        Map<String, Object> result = new HashMap<>();
        result.put("room", summary(room));
        result.put("spectator", true);
        return result;
    }

    public Game startGame(RoomState room, String hostId) {
        ensureHost(room, hostId);
        deckCatalogService.warmRoomCatalog(room.id, room.settings);
        List<Player> players = new ArrayList<>();
        for (String playerId : room.players) {
            Player player = new Player();
            player.clientId = playerId;
            if (playerId.startsWith("computer:")) {
                String computerId = playerId.substring(playerId.lastIndexOf(':') + 1);
                ComputerPlayerDto profile = computerPlayerService.findRequired(computerId);
                player.username = profile.name;
                player.isComputer = true;
                player.computerId = computerId;
                player.statsId = "computer:" + computerId;
            } else {
                player.username = displayName(room, playerId);
                player.statsId = playerId;
            }
            players.add(player);
        }
        Room engineRoom = new Room();
        engineRoom.id = room.id;
        engineRoom.settings = room.settings;
        Game game = gameRuntimeService.createGame(
                engineRoom,
                players,
                deckCatalogService.expandedCardsFromRoom(room.id, room.settings));
        deckCatalogService.attachCatalogToGame(game.id, room.id);
        room.status = "loading";
        room.gameId = game.id;
        save(room);
        return game;
    }

    public void addComputer(RoomState room, String computerId) {
        if (room.players.size() >= room.settings.maxPlayers) {
            throw new CofException(ErrorCode.CONFLICT, "房间人数已满。");
        }
        ComputerPlayerDto profile = computerPlayerService.findRequired(computerId);
        String clientId = "computer:" + room.id + ":" + profile.id;
        if (room.players.stream().anyMatch(id -> id.endsWith(":" + computerId))) {
            throw new CofException(ErrorCode.CONFLICT, "该人机已经在房间中。");
        }
        room.players.add(clientId);
        userStatsService.ensureStats("computer:" + computerId, profile.name, true, computerId);
        save(room);
    }

    public Map<String, Object> summary(RoomState room) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", room.id);
        map.put("hostId", room.hostId);
        map.put("players", room.players);
        map.put("spectators", room.spectators);
        map.put("status", room.status);
        map.put("settings", room.settings);
        map.put("gameId", room.gameId);
        map.put("startVotes", room.startVotes);
        map.put("startAt", room.startAt);
        map.put("createdAt", room.createdAt);
        map.put("chatMessages", room.chatMessages != null ? room.chatMessages : List.of());
        List<Map<String, Object>> playerRows = room.players.stream().map(id -> {
            Map<String, Object> row = new HashMap<>();
            row.put("clientId", id);
            row.put("username", displayName(room, id));
            row.put("isComputer", id.startsWith("computer:"));
            if (id.startsWith("computer:")) {
                row.put("computerId", id.substring(id.lastIndexOf(':') + 1));
            }
            return row;
        }).collect(Collectors.toList());
        map.put("playerDetails", playerRows);
        return map;
    }

    public String displayName(RoomState room, String clientId) {
        if (room.usernames != null && room.usernames.containsKey(clientId)) {
            return room.usernames.get(clientId);
        }
        if (clientId.startsWith("computer:")) {
            String computerId = clientId.substring(clientId.lastIndexOf(':') + 1);
            try {
                return computerPlayerService.findRequired(computerId).name;
            } catch (CofException ex) {
                return computerId;
            }
        }
        return clientId;
    }

    public Map<String, Object> assetManifest(RoomState room) {
        List<String> libraryIds = room.settings.libraryIds != null ? room.settings.libraryIds : List.of();
        List<CardLibraryDto> full = deckCatalogService.listFullLibraries().stream()
                .filter(lib -> libraryIds.isEmpty() || libraryIds.contains(lib.id))
                .collect(Collectors.toList());
        java.util.LinkedHashSet<String> assets = new java.util.LinkedHashSet<>();
        assets.add("/assets/bell.png");
        assets.add("/assets/logo.png");
        assets.add("/audio/ding.wav");
        assets.add("/audio/sendcard.mp3");
        List<Map<String, Object>> libraries = new ArrayList<>();
        for (CardLibraryDto lib : full) {
            if (lib.backUrl != null) {
                assets.add(lib.backUrl);
            }
            List<Map<String, Object>> cards = new ArrayList<>();
            if (lib.cards != null) {
                for (var card : lib.cards) {
                    if (card.imageUrl != null) {
                        assets.add(card.imageUrl);
                    }
                    Map<String, Object> c = new HashMap<>();
                    c.put("id", card.id);
                    c.put("imageUrl", card.imageUrl);
                    c.put("backUrl", card.backUrl);
                    cards.add(c);
                }
            }
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", lib.id);
            entry.put("backUrl", lib.backUrl);
            entry.put("cards", cards);
            libraries.add(entry);
        }
        String key = "room-assets-" + Integer.toHexString(libraries.hashCode());
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("key", key);
        manifest.put("assets", new ArrayList<>(assets));
        manifest.put("libraries", libraries);
        return manifest;
    }

    public void save(RoomState room) {
        redis.set(RedisKeys.room(room.id), room, null);
        redis.setAdd(RedisKeys.ROOM_INDEX, room.id);
    }

    public void deleteRoom(RoomState room) {
        redis.delete(RedisKeys.room(room.id));
        redis.setRemove(RedisKeys.ROOM_INDEX, room.id);
        for (String playerId : room.players) {
            if (!playerId.startsWith("computer:")) {
                playerRoomService.unbind(playerId);
            }
        }
    }

    public Map<String, Object> leave(RoomState room, String clientId) {
        room.players.remove(clientId);
        room.spectators.remove(clientId);
        room.startVotes.remove(clientId);
        if (room.hostId.equals(clientId) && !room.players.isEmpty()) {
            room.hostId = room.players.get(0);
        }
        playerRoomService.unbind(clientId);
        if (room.players.isEmpty()) {
            deleteRoom(room);
            return Map.of("left", true, "disbanded", true);
        }
        save(room);
        return Map.of("left", true, "room", summary(room));
    }

    public void disband(RoomState room, String hostId) {
        ensureHost(room, hostId);
        if (room.gameId != null) {
            gameRuntimeService.get(room.gameId).ifPresent(bundle -> {
                bundle.game.status = "aborted";
                gameRuntimeService.save(bundle.game);
            });
        }
        deleteRoom(room);
    }

    public RoomState addStartVote(RoomState room, String clientId) {
        if (!room.startVotes.contains(clientId)) {
            room.startVotes.add(clientId);
        }
        save(room);
        return room;
    }

    public RoomState cancelStartVote(RoomState room, String clientId) {
        room.startVotes.remove(clientId);
        save(room);
        return room;
    }

    public RoomState transferHost(RoomState room, String hostId, String newHostId) {
        ensureHost(room, hostId);
        if (!room.players.contains(newHostId)) {
            throw new CofException(ErrorCode.CONFLICT, "目标玩家不在房间中。");
        }
        room.hostId = newHostId;
        save(room);
        return room;
    }

    public void removeComputer(RoomState room, String hostId, String computerId) {
        ensureHost(room, hostId);
        room.players.removeIf(id -> id.endsWith(":" + computerId));
        save(room);
    }

    public RoomChatMessage postChat(RoomState room, String clientId, String username, String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.length() > 40) {
            throw new CofException(ErrorCode.BAD_REQUEST, "消息长度需在 1–40 字。");
        }
        RoomChatMessage message = new RoomChatMessage();
        message.clientId = clientId;
        message.username = username;
        message.text = trimmed;
        message.at = System.currentTimeMillis();
        if (room.chatMessages == null) {
            room.chatMessages = new ArrayList<>();
        }
        room.chatMessages.add(message);
        if (room.chatMessages.size() > 100) {
            room.chatMessages.remove(0);
        }
        save(room);
        return message;
    }

    public Optional<RoomState> findRoomForPlayer(String clientId) {
        return playerRoomService.findRoomId(clientId).flatMap(this::get);
    }

    private void rememberUsername(RoomState room, String clientId, String username) {
        if (username != null && !username.isBlank()) {
            if (room.usernames == null) {
                room.usernames = new HashMap<>();
            }
            room.usernames.put(clientId, username);
        }
    }

    public void ensureHost(RoomState room, String clientId) {
        if (!room.hostId.equals(clientId)) {
            throw new CofException(ErrorCode.FORBIDDEN, "只有房主可以执行此操作。");
        }
    }

    private GameSettings normalizeSettings(GameSettings input) {
        List<CardLibraryDto> libraries = metaService.listLibraries();
        List<String> ids = libraries.stream().map(l -> l.id).toList();
        Map<String, Integer> counts = libraries.stream()
                .collect(Collectors.toMap(l -> l.id, l -> l.cardCount));
        return GameCore.normalizeSettings(input, ids, counts);
    }

    private String allocateRoomId() {
        return GameCore.randomId("r");
    }
}
