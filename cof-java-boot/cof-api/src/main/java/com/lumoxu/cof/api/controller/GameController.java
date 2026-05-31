package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.api.auth.AuthContext;
import com.lumoxu.cof.api.auth.RequireAuth;
import com.lumoxu.cof.api.ws.WsBroadcastService;
import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.service.GameRuntimeService;
import com.lumoxu.cof.service.RoomService;
import com.lumoxu.cof.service.model.RoomState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/games")
@RequireAuth
public class GameController {

    private final GameRuntimeService gameRuntimeService;
    private final RoomService roomService;
    private final WsBroadcastService broadcastService;

    public GameController(
            GameRuntimeService gameRuntimeService,
            RoomService roomService,
            WsBroadcastService broadcastService) {
        this.gameRuntimeService = gameRuntimeService;
        this.roomService = roomService;
        this.broadcastService = broadcastService;
    }

    @GetMapping("/{gameId}")
    public ApiResponse<PublicGame> get(@PathVariable("gameId") String gameId) {
        return ApiResponse.ok(gameRuntimeService.toPublicGame(gameRuntimeService.getRequired(gameId).game));
    }

    @PostMapping("/{gameId}/play-card")
    public ApiResponse<PublicGame> playCard(@PathVariable("gameId") String gameId) throws Exception {
        PublicGame game = gameRuntimeService.playCard(gameId, AuthContext.get().clientId.toString());
        broadcastService.broadcastGameSync(game);
        broadcastService.broadcastAudio(game.roomId, game.id, "play-card");
        return ApiResponse.ok(game);
    }

    @PostMapping("/{gameId}/ring-bell")
    public ApiResponse<PublicGame> ringBell(@PathVariable("gameId") String gameId) throws Exception {
        PublicGame game = gameRuntimeService.ringBell(gameId, AuthContext.get().clientId.toString());
        broadcastService.broadcastGameSync(game);
        broadcastService.broadcastAudio(game.roomId, game.id, "ring-bell");
        return ApiResponse.ok(game);
    }

    @PostMapping("/{gameId}/continue")
    public ApiResponse<Map<String, Object>> continueGame(@PathVariable("gameId") String gameId) throws Exception {
        RoomState room = roomService.getRequired(
                gameRuntimeService.getRequired(gameId).game.roomId);
        PublicGame game = gameRuntimeService.continueGame(
                gameId,
                AuthContext.get().clientId.toString(),
                room);
        roomService.save(room);
        broadcastService.broadcastRoom(room);
        broadcastService.broadcastGameSync(game);
        return ApiResponse.ok(Map.of("room", roomService.summary(room), "game", game));
    }
}
