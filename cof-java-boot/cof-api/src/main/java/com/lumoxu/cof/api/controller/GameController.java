package com.lumoxu.cof.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumoxu.cof.api.auth.AuthContext;
import com.lumoxu.cof.api.auth.RequireAuth;
import com.lumoxu.cof.api.ws.GameSyncTracker;
import com.lumoxu.cof.api.ws.WsBroadcastService;
import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.service.GameRuntimeService;
import com.lumoxu.cof.service.RoomService;
import com.lumoxu.cof.service.UserStatsService;
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
    private final GameSyncTracker syncTracker;
    private final UserStatsService userStatsService;

    public GameController(
            GameRuntimeService gameRuntimeService,
            RoomService roomService,
            WsBroadcastService broadcastService,
            GameSyncTracker syncTracker,
            UserStatsService userStatsService) {
        this.gameRuntimeService = gameRuntimeService;
        this.roomService = roomService;
        this.broadcastService = broadcastService;
        this.syncTracker = syncTracker;
        this.userStatsService = userStatsService;
    }

    private void persistIfFinished(String statusBefore, String gameId) {
        var bundle = gameRuntimeService.getRequired(gameId);
        if (justFinished(statusBefore, bundle.game.status)
                && userStatsService.recordFinishedGame(bundle.game)) {
            gameRuntimeService.save(bundle.game);
        }
    }

    @GetMapping("/{gameId}")
    public ApiResponse<Map<String, Object>> get(@PathVariable("gameId") String gameId) {
        String clientId = AuthContext.get().clientId.toString();
        PublicGame game = gameRuntimeService.toPublicGame(gameRuntimeService.getRequired(gameId).game);
        broadcastService.resetClientSync(clientId, gameId);
        JsonNode sync = syncTracker.publishForClient(clientId, gameId, game);
        return ApiResponse.ok(Map.of("game", game, "sync", sync));
    }

    @PostMapping("/{gameId}/play-card")
    public ApiResponse<Map<String, Object>> playCard(@PathVariable("gameId") String gameId) throws Exception {
        String clientId = AuthContext.get().clientId.toString();
        String statusBefore = gameRuntimeService.getRequired(gameId).game.status;
        PublicGame game = gameRuntimeService.playCard(gameId, clientId);
        JsonNode sync = syncTracker.publishForClient(clientId, gameId, game);
        broadcastService.broadcastGameSync(game);
        broadcastService.broadcastAudio(game.roomId, game.id, "play-card");
        if (justFinished(statusBefore, game.status)) {
            broadcastService.broadcastAudio(game.roomId, game.id, "end-game");
            persistIfFinished(statusBefore, gameId);
        }
        return ApiResponse.ok(Map.of("sync", sync));
    }

    @PostMapping("/{gameId}/ring-bell")
    public ApiResponse<Map<String, Object>> ringBell(@PathVariable("gameId") String gameId) throws Exception {
        String clientId = AuthContext.get().clientId.toString();
        String statusBefore = gameRuntimeService.getRequired(gameId).game.status;
        PublicGame game = gameRuntimeService.ringBell(gameId, clientId);
        JsonNode sync = syncTracker.publishForClient(clientId, gameId, game);
        broadcastService.broadcastGameSync(game);
        broadcastService.broadcastAudio(game.roomId, game.id, "ring-bell");
        if (justFinished(statusBefore, game.status)) {
            broadcastService.broadcastAudio(game.roomId, game.id, "end-game");
            persistIfFinished(statusBefore, gameId);
        }
        return ApiResponse.ok(Map.of("sync", sync));
    }

    private static boolean justFinished(String before, String after) {
        return !"finished".equals(before) && "finished".equals(after);
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
