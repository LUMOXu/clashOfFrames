package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.api.auth.AuthContext;
import com.lumoxu.cof.api.auth.RequireAuth;
import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameSettings;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rooms")
@RequireAuth
public class RoomController {

    private final RoomService roomService;
    private final GameRuntimeService gameRuntimeService;

    public RoomController(RoomService roomService, GameRuntimeService gameRuntimeService) {
        this.roomService = roomService;
        this.gameRuntimeService = gameRuntimeService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(value = "all", required = false) String all) {
        String clientId = AuthContext.get().clientId.toString();
        return ApiResponse.ok(Map.of("rooms", roomService.listRooms(clientId, "1".equals(all))));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String clientId = AuthContext.get().clientId.toString();
        GameSettings settings = body.get("settings") instanceof Map<?, ?> map
                ? mapToSettings(map)
                : GameSettings.defaultSettings();
        @SuppressWarnings("unchecked")
        List<String> computerIds = body.get("computerIds") instanceof List<?> list
                ? (List<String>) list
                : List.of();
        RoomState room = roomService.createRoom(clientId, settings, computerIds);
        return ApiResponse.ok(Map.of("room", roomService.summary(room)));
    }

    @PostMapping("/{roomId}/join")
    public ApiResponse<Map<String, Object>> join(@PathVariable("roomId") String roomId) {
        RoomState room = roomService.getRequired(roomId);
        return ApiResponse.ok(roomService.join(room, AuthContext.get().clientId.toString(), AuthContext.get().username));
    }

    @PatchMapping("/{roomId}/settings")
    public ApiResponse<Map<String, Object>> updateSettings(
            @PathVariable("roomId") String roomId,
            @RequestBody Map<String, Object> body) {
        RoomState room = roomService.getRequired(roomId);
        roomService.ensureHost(room, AuthContext.get().clientId.toString());
        GameSettings settings = body.get("settings") instanceof Map<?, ?> map
                ? mapToSettings(map)
                : room.settings;
        room = roomService.updateSettings(room, settings);
        return ApiResponse.ok(Map.of("room", roomService.summary(room)));
    }

    @PostMapping("/{roomId}/start")
    public ApiResponse<Map<String, Object>> start(@PathVariable("roomId") String roomId) {
        RoomState room = roomService.getRequired(roomId);
        Game game = roomService.startGame(room, AuthContext.get().clientId.toString());
        return ApiResponse.ok(Map.of(
                "room", roomService.summary(room),
                "game", gameRuntimeService.toPublicGame(game)));
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
        return settings;
    }
}
