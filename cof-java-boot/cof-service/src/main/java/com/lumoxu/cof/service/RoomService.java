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
import com.lumoxu.cof.service.model.RoomState;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import com.lumoxu.cof.service.redis.RedisKeys;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public RoomService(
            JsonRedisOps redis,
            MetaService metaService,
            DeckCatalogService deckCatalogService,
            GameRuntimeService gameRuntimeService,
            ComputerPlayerService computerPlayerService,
            UserStatsService userStatsService) {
        this.redis = redis;
        this.metaService = metaService;
        this.deckCatalogService = deckCatalogService;
        this.gameRuntimeService = gameRuntimeService;
        this.computerPlayerService = computerPlayerService;
        this.userStatsService = userStatsService;
    }

    public RoomState createRoom(String hostId, GameSettings settings, List<String> computerIds) {
        RoomState room = new RoomState();
        room.id = allocateRoomId();
        room.hostId = hostId;
        room.players.add(hostId);
        room.settings = normalizeSettings(settings);
        room.createdAt = System.currentTimeMillis();
        if (computerIds != null) {
            for (String computerId : computerIds) {
                addComputer(room, computerId);
            }
        }
        deckCatalogService.warmRoomCatalog(room.id, room.settings);
        save(room);
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
        return List.of();
    }

    public Map<String, Object> join(RoomState room, String clientId, String username) {
        if ("waiting".equals(room.status)) {
            if (!room.players.contains(clientId)) {
                if (room.players.size() >= room.settings.maxPlayers) {
                    throw new CofException(ErrorCode.CONFLICT, "房间人数已满。");
                }
                room.players.add(clientId);
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
                player.username = playerId;
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
        map.put("createdAt", room.createdAt);
        return map;
    }

    public Map<String, Object> assetManifest(RoomState room) {
        Set<String> selected = room.settings.libraryIds != null
                ? Set.copyOf(room.settings.libraryIds)
                : Set.of();
        List<Map<String, Object>> libraries = metaService.listLibraries().stream()
                .filter(lib -> selected.isEmpty() || selected.contains(lib.id))
                .map(lib -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", lib.id);
                    entry.put("backUrl", lib.backUrl);
                    entry.put("cards", lib.cards.stream().map(card -> {
                        Map<String, Object> c = new HashMap<>();
                        c.put("id", card.id);
                        c.put("imageUrl", card.imageUrl);
                        c.put("backUrl", card.backUrl);
                        return c;
                    }).collect(Collectors.toList()));
                    return entry;
                })
                .collect(Collectors.toList());
        return Map.of("libraries", libraries);
    }

    public void save(RoomState room) {
        redis.set(RedisKeys.room(room.id), room, null);
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
