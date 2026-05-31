package com.lumoxu.cof.service.model;

import com.lumoxu.cof.engine.Game;

public class GameStateBundle {

    public Game game;
    public long updatedAt;

    public GameStateBundle() {
    }

    public GameStateBundle(Game game, long updatedAt) {
        this.game = game;
        this.updatedAt = updatedAt;
    }
}
