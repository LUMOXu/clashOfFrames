package com.lumoxu.cof.service;

import com.lumoxu.cof.engine.Card;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameCore;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.engine.Room;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTickOrchestratorTest {

    @Test
    void autoPlaysWhenTurnDeadlinePassed() {
        Game game = samplePlayingGame();
        Player human = game.players.get(0);
        game.turnIndex = 0;
        long now = 10_000L;
        GameCore.setTurnTiming(game, now - 9000L, 0);
        game.turnDeadlineAt = now - 1;

        assertTrue(GameTickOrchestrator.applyTurnTimeoutIfDue(game, now));
        assertEquals(1, game.playCount);
        assertTrue(game.logs.stream().anyMatch(l -> l.text != null && l.text.contains("自动出牌")));
    }

    private static Game samplePlayingGame() {
        Room room = new Room();
        room.id = "room1";
        room.settings = GameSettings.defaultSettings();
        List<Player> players = new ArrayList<>();
        Player human = new Player();
        human.clientId = "human";
        human.username = "Human";
        human.connected = true;
        human.drawPile = new ArrayList<>(List.of(new Card()));
        players.add(human);
        Player bot = new Player();
        bot.clientId = "computer:computer_normal";
        bot.username = "Bot";
        bot.isComputer = true;
        bot.computerId = "computer_normal";
        bot.drawPile = new ArrayList<>(List.of(new Card()));
        players.add(bot);
        Game game = GameCore.createGame(room, players, List.of(new Card(), new Card(), new Card()));
        GameCore.startPlaying(game, 1000L);
        return game;
    }
}
