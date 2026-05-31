package com.lumoxu.cof.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GameTickScheduler {

    private final GameTickOrchestrator orchestrator;

    public GameTickScheduler(GameTickOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedRate = 100)
    public void tick() {
        orchestrator.tick();
    }
}
