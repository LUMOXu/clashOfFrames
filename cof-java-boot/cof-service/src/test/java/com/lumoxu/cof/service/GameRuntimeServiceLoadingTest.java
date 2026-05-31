package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.engine.Room;
import com.lumoxu.cof.service.model.GameStateBundle;
import com.lumoxu.cof.service.model.RoomState;
import com.lumoxu.cof.service.support.TestRedisSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameRuntimeServiceLoadingTest {

    private GameRuntimeService gameRuntimeService;
    private com.lumoxu.cof.service.redis.JsonRedisOps redis;

    @BeforeEach
    void setUp() {
        redis = TestRedisSupport.memoryJsonRedis(new ObjectMapper());
        gameRuntimeService = new GameRuntimeService(redis);
    }

    @Test
    void updateLoadingProgressStartsGameWhenAllHumansReady() {
        RoomState room = new RoomState();
        room.id = "r1";
        room.status = "loading";
        room.players = new ArrayList<>(List.of("human-1", "computer:r1:computer_easy"));
        room.settings = GameSettings.defaultSettings();
        room.settings.minPlayers = 2;

        Room engineRoom = new Room();
        engineRoom.id = room.id;
        engineRoom.settings = room.settings;

        List<Player> players = new ArrayList<>();
        Player human = new Player();
        human.clientId = "human-1";
        human.username = "Human";
        players.add(human);
        Player bot = new Player();
        bot.clientId = "computer:r1:computer_easy";
        bot.username = "Bot";
        bot.isComputer = true;
        players.add(bot);

        Game game = gameRuntimeService.createGame(engineRoom, players, List.of());
        room.gameId = game.id;

        gameRuntimeService.updateLoadingProgress(
                game.id,
                "human-1",
                Map.of("loaded", 10, "total", 10, "done", true),
                room);

        Game saved = redis.get(com.lumoxu.cof.service.redis.RedisKeys.game(game.id), GameStateBundle.class)
                .orElseThrow()
                .game;
        assertEquals("playing", saved.status);
        assertEquals("playing", room.status);
    }
}
