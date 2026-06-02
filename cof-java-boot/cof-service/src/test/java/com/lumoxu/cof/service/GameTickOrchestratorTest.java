package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.engine.Card;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameCore;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.engine.Room;
import com.lumoxu.cof.service.model.RoomState;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import com.lumoxu.cof.service.support.TestRedisSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void autoStartFromVoteCountdownBroadcastsRoomAndGameStateTogether() {
        JsonRedisOps redis = TestRedisSupport.memoryJsonRedis(new ObjectMapper());
        MetaService metaService = mock(MetaService.class);
        DeckCatalogService deckCatalogService = mock(DeckCatalogService.class);
        ComputerPlayerService computerPlayerService = mock(ComputerPlayerService.class);
        UserStatsService userStatsService = mock(UserStatsService.class);
        GameRuntimeService gameRuntimeService = new GameRuntimeService(redis);
        PlayerPresenceService presenceService = new PlayerPresenceService(redis);
        RoomService roomService = new RoomService(
                redis,
                metaService,
                deckCatalogService,
                gameRuntimeService,
                computerPlayerService,
                userStatsService,
                new PlayerRoomService(redis),
                presenceService,
                new RoomStartVoteService());
        RoomMaintenanceService maintenanceService = new RoomMaintenanceService(roomService, presenceService);
        ComputerPlayerAdvanceService computerAdvanceService = mock(ComputerPlayerAdvanceService.class);
        when(computerAdvanceService.advance(any(Game.class), anyLong())).thenReturn(ComputerTickOutcome.none());
        GameTickBroadcaster broadcaster = mock(GameTickBroadcaster.class);

        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Card card = new Card();
            card.id = "card-" + i;
            cards.add(card);
        }
        when(deckCatalogService.expandedCardsFromRoom(anyString(), any(GameSettings.class))).thenReturn(cards);

        RoomState room = new RoomState();
        room.id = "vote-room";
        room.hostId = "host";
        room.players = new ArrayList<>(List.of("host", "guest"));
        room.startVotes = new ArrayList<>(List.of("host", "guest"));
        room.settings = GameSettings.defaultSettings();
        room.startCountdownStartedAt = 1L;
        room.startAt = 1L;
        roomService.save(room);

        GameTickOrchestrator orchestrator = new GameTickOrchestrator(
                redis,
                gameRuntimeService,
                roomService,
                computerAdvanceService,
                maintenanceService,
                userStatsService,
                Optional.of(broadcaster));

        orchestrator.tick();

        verify(broadcaster).onContinueStateChanged(any(RoomState.class), any(PublicGame.class));
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
