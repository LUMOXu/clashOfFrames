package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.api.auth.AuthContext;
import com.lumoxu.cof.api.auth.RequireAuth;
import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.service.GameRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/games")
@RequireAuth
public class GameController {

    private final GameRuntimeService gameRuntimeService;

    public GameController(GameRuntimeService gameRuntimeService) {
        this.gameRuntimeService = gameRuntimeService;
    }

    @GetMapping("/{gameId}")
    public ApiResponse<PublicGame> get(@PathVariable("gameId") String gameId) {
        return ApiResponse.ok(gameRuntimeService.toPublicGame(gameRuntimeService.getRequired(gameId).game));
    }

    @PostMapping("/{gameId}/play-card")
    public ApiResponse<PublicGame> playCard(@PathVariable("gameId") String gameId) {
        return ApiResponse.ok(gameRuntimeService.playCard(gameId, AuthContext.get().clientId.toString()));
    }

    @PostMapping("/{gameId}/ring-bell")
    public ApiResponse<PublicGame> ringBell(@PathVariable("gameId") String gameId) {
        return ApiResponse.ok(gameRuntimeService.ringBell(gameId, AuthContext.get().clientId.toString()));
    }
}
