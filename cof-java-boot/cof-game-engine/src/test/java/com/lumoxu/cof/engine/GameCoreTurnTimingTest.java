package com.lumoxu.cof.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameCoreTurnTimingTest {

    @Test
    void manualOnlyUsesShortTimeoutForComputerTurn() {
        Game game = new Game();
        game.settings = GameSettings.defaultSettings();
        Player bot = new Player();
        bot.clientId = "computer:1";
        bot.isComputer = true;
        bot.connected = true;
        game.players = new ArrayList<>(List.of(bot));
        game.turnIndex = 0;

        long now = 50_000L;
        GameCore.setTurnTiming(game, now, 1000, true);

        assertTrue(game.turnDeadlineAt < now + GameConstants.MANUAL_TURN_TIMEOUT_MS / 2);
        assertEquals(now + GameConstants.TURN_TIMEOUT_MS, game.turnDeadlineAt);
    }

    @Test
    void manualOnlyUsesLongTimeoutForHumanTurn() {
        Game game = new Game();
        game.settings = GameSettings.defaultSettings();
        Player human = new Player();
        human.clientId = "human";
        human.connected = true;
        game.players = new ArrayList<>(List.of(human));
        game.turnIndex = 0;

        long now = 50_000L;
        GameCore.setTurnTiming(game, now, 1000, true);

        assertEquals(now + GameConstants.MANUAL_TURN_TIMEOUT_MS, game.turnDeadlineAt);
    }
}
