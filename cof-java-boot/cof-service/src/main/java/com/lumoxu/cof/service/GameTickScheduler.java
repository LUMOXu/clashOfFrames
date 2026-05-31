package com.lumoxu.cof.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GameTickScheduler {

    private final GameRuntimeService gameRuntimeService;

    public GameTickScheduler(GameRuntimeService gameRuntimeService) {
        this.gameRuntimeService = gameRuntimeService;
    }

    @Scheduled(fixedRate = 100)
    public void tick() {
        gameRuntimeService.tickAll();
    }
}
