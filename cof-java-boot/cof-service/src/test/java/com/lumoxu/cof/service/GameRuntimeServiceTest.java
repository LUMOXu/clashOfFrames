package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.engine.Card;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.engine.Room;
import com.lumoxu.cof.service.support.TestRedisSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GameRuntimeServiceTest {

    private GameRuntimeService gameRuntimeService;

    @BeforeEach
    void setUp() {
        gameRuntimeService = new GameRuntimeService(TestRedisSupport.memoryJsonRedis(new ObjectMapper()));
    }

    @Test
    void savesAndLoadsGame() {
        Room room = new Room();
        room.id = "r1";
        room.settings = GameSettings.defaultSettings();
        Player player = new Player();
        player.clientId = "p1";
        player.username = "p1";
        List<Card> cards = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Card card = new Card();
            card.id = "c" + i;
            card.pmvId = 1;
            cards.add(card);
        }
        Game game = gameRuntimeService.createGame(room, List.of(player), cards);
        assertNotNull(gameRuntimeService.getRequired(game.id).game);
    }
}
