package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.api.auth.AuthContext;
import com.lumoxu.cof.api.auth.RequireAuth;
import com.lumoxu.cof.api.ws.WsBroadcastService;
import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.service.GameRuntimeService;
import com.lumoxu.cof.service.RoomService;
import com.lumoxu.cof.service.model.RoomState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rooms")
@RequireAuth
public class RoomController {

    private final RoomService roomService;
    private final GameRuntimeService gameRuntimeService;
    private final WsBroadcastService broadcastService;

    public RoomController(
            RoomService roomService,
            GameRuntimeService gameRuntimeService,
            WsBroadcastService broadcastService) {
        this.roomService = roomService;
        this.gameRuntimeService = gameRuntimeService;
        this.broadcastService = broadcastService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(value = "all", required = false) String all) {
        String clientId = AuthContext.get().clientId.toString();
        return ApiResponse.ok(Map.of("rooms", roomService.listRooms(clientId, "1".equals(all))));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) throws Exception {
        String clientId = AuthContext.get().clientId.toString();
        String username = AuthContext.get().username;
        GameSettings settings = body.get("settings") instanceof Map<?, ?> map
                ? mapToSettings(map)
                : GameSettings.defaultSettings();
        @SuppressWarnings("unchecked")
        List<String> computerIds = body.get("computerIds") instanceof List<?> list
                ? (List<String>) list
                : List.of();
        RoomState room = roomService.createRoom(clientId, username, settings, computerIds);
        broadcastService.broadcastRoom(room);
        return ApiResponse.ok(Map.of("room", roomService.summary(room)));
    }

    @PostMapping("/{roomId}/join")
    public ApiResponse<Map<String, Object>> join(@PathVariable("roomId") String roomId) throws Exception {
        RoomState room = roomService.getRequired(roomId);
        Map<String, Object> result = roomService.join(
                room,
                AuthContext.get().clientId.toString(),
                AuthContext.get().username);
        broadcastService.broadcastRoom(room);
        if (result.get("game") instanceof PublicGame game) {
            broadcastService.broadcastGameSync(game);
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/{roomId}/leave")
    public ApiResponse<Map<String, Object>> leave(@PathVariable("roomId") String roomId) throws Exception {
        RoomState room = roomService.getRequired(roomId);
        Map<String, Object> result = roomService.leave(room, AuthContext.get().clientId.toString());
        if (!Boolean.TRUE.equals(result.get("disbanded"))) {
            broadcastService.broadcastRoom(room);
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/{roomId}/disband")
    public ApiResponse<Map<String, Object>> disband(@PathVariable("roomId") String roomId) throws Exception {
        RoomState room = roomService.getRequired(roomId);
        roomService.disband(room, AuthContext.get().clientId.toString());
        return ApiResponse.ok(Map.of("disbanded", true));
    }

    @PostMapping("/{roomId}/start-vote")
    public ApiResponse<Map<String, Object>> startVote(@PathVariable("roomId") String roomId) throws Exception {
        RoomState room = roomService.getRequired(roomId);
        room = roomService.addStartVote(room, AuthContext.get().clientId.toString());
        broadcastService.broadcastRoom(room);
        return ApiResponse.ok(Map.of("room", roomService.summary(room)));
    }

    @PostMapping("/{roomId}/cancel-start-vote")
    public ApiResponse<Map<String, Object>> cancelStartVote(@PathVariable("roomId") String roomId) throws Exception {
        RoomState room = roomService.getRequired(roomId);
        room = roomService.cancelStartVote(room, AuthContext.get().clientId.toString());
        broadcastService.broadcastRoom(room);
        return ApiResponse.ok(Map.of("room", roomService.summary(room)));
    }

    @PostMapping("/{roomId}/add-computer")
    public ApiResponse<Map<String, Object>> addComputer(
            @PathVariable("roomId") String roomId,
            @RequestBody Map<String, Object> body) throws Exception {
        RoomState room = roomService.getRequired(roomId);
        roomService.ensureHost(room, AuthContext.get().clientId.toString());
        String computerId = String.valueOf(body.get("computerId"));
        roomService.addComputer(room, computerId);
        broadcastService.broadcastRoom(room);
        return ApiResponse.ok(Map.of("room", roomService.summary(room)));
    }

    @PostMapping("/{roomId}/remove-computer")
    public ApiResponse<Map<String, Object>> removeComputer(
            @PathVariable("roomId") String roomId,
            @RequestBody Map<String, Object> body) throws Exception {
        RoomState room = roomService.getRequired(roomId);
        roomService.removeComputer(room, AuthContext.get().clientId.toString(), String.valueOf(body.get("computerId")));
        broadcastService.broadcastRoom(room);
        return ApiResponse.ok(Map.of("room", roomService.summary(room)));
    }

    @PostMapping("/{roomId}/chat")
    public ApiResponse<Map<String, Object>> chat(
            @PathVariable("roomId") String roomId,
            @RequestBody Map<String, Object> body) throws Exception {
        RoomState room = roomService.getRequired(roomId);
        var message = roomService.postChat(
                room,
                AuthContext.get().clientId.toString(),
                AuthContext.get().username,
                String.valueOf(body.get("message")));
        broadcastService.broadcastRoom(room);
        return ApiResponse.ok(Map.of("message", message));
    }

    @PostMapping("/{roomId}/transfer-host")
    public ApiResponse<Map<String, Object>> transferHost(
            @PathVariable("roomId") String roomId,
            @RequestBody Map<String, Object> body) throws Exception {
        RoomState room = roomService.getRequired(roomId);
        room = roomService.transferHost(
                room,
                AuthContext.get().clientId.toString(),
                String.valueOf(body.get("newHostId")));
        broadcastService.broadcastRoom(room);
        return ApiResponse.ok(Map.of("room", roomService.summary(room)));
    }

    @PatchMapping("/{roomId}/settings")
    public ApiResponse<Map<String, Object>> updateSettings(
            @PathVariable("roomId") String roomId,
            @RequestBody Map<String, Object> body) throws Exception {
        RoomState room = roomService.getRequired(roomId);
        roomService.ensureHost(room, AuthContext.get().clientId.toString());
        GameSettings settings = body.get("settings") instanceof Map<?, ?> map
                ? mapToSettings(map)
                : room.settings;
        room = roomService.updateSettings(room, settings);
        broadcastService.broadcastRoom(room);
        return ApiResponse.ok(Map.of("room", roomService.summary(room)));
    }

    @PostMapping("/{roomId}/start")
    public ApiResponse<Map<String, Object>> start(@PathVariable("roomId") String roomId) throws Exception {
        RoomState room = roomService.getRequired(roomId);
        Game game = roomService.startGame(room, AuthContext.get().clientId.toString());
        PublicGame publicGame = gameRuntimeService.toPublicGame(game);
        broadcastService.broadcastRoom(room);
        broadcastService.broadcastGameSync(publicGame);
        return ApiResponse.ok(Map.of(
                "room", roomService.summary(room),
                "game", publicGame));
    }

    @PostMapping("/{roomId}/loading-progress")
    public ApiResponse<Map<String, Object>> loadingProgress(
            @PathVariable("roomId") String roomId,
            @RequestBody Map<String, Object> body) throws Exception {
        RoomState room = roomService.getRequired(roomId);
        if (room.gameId == null) {
            return ApiResponse.ok(Map.of("room", roomService.summary(room)));
        }
        String statusBefore = gameRuntimeService.getRequired(room.gameId).game.status;
        PublicGame game = gameRuntimeService.updateLoadingProgress(
                room.gameId,
                AuthContext.get().clientId.toString(),
                body,
                room);
        roomService.save(room);
        safeBroadcast(room, game, statusBefore);
        return ApiResponse.ok(Map.of("room", roomService.summary(room), "game", game));
    }

    private void safeBroadcast(RoomState room, PublicGame game, String statusBefore) {
        try {
            broadcastService.broadcastRoom(room);
            if (game != null) {
                broadcastService.broadcastGameSync(game);
                if ("loading".equals(statusBefore) && "playing".equals(game.status)) {
                    broadcastService.broadcastAudio(room.id, game.id, "new-game");
                }
            }
        } catch (Exception ex) {
            // 广播失败不应阻断加载进度 API
        }
    }

    private GameSettings mapToSettings(Map<?, ?> map) {
        GameSettings settings = GameSettings.defaultSettings();
        if (map.get("minPlayers") instanceof Number n) {
            settings.minPlayers = n.intValue();
        }
        if (map.get("maxPlayers") instanceof Number n) {
            settings.maxPlayers = n.intValue();
        }
        if (map.get("isPublic") instanceof Boolean b) {
            settings.isPublic = b;
        }
        if (map.get("libraryIds") instanceof List<?> list) {
            settings.libraryIds = list.stream().map(String::valueOf).toList();
        }
        if (map.get("libraryCopies") instanceof Map<?, ?> copies) {
            Map<String, Integer> copyMap = new HashMap<>();
            copies.forEach((k, v) -> {
                if (v instanceof Number num) {
                    copyMap.put(String.valueOf(k), num.intValue());
                }
            });
            settings.libraryCopies = copyMap;
        }
        if (map.get("startVoteThresholdMode") instanceof String mode) {
            settings.startVoteThresholdMode = mode;
        }
        if (map.get("startVoteThreshold") instanceof Number n) {
            settings.startVoteThreshold = n.intValue();
        }
        if (map.get("allowEmptyBell") instanceof Boolean b) {
            settings.allowEmptyBell = b;
        }
        if (map.get("randomBacks") instanceof Boolean b) {
            settings.randomBacks = b;
        }
        if (map.get("conflictResolution") instanceof Boolean b) {
            settings.conflictResolution = b;
        }
        if (map.get("disconnectProtection") instanceof Boolean b) {
            settings.disconnectProtection = b;
        }
        return settings;
    }
}
