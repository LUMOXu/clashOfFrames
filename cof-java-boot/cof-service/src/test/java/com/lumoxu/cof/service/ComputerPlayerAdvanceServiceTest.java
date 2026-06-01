package com.lumoxu.cof.service;

import com.lumoxu.cof.engine.Card;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameCore;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.engine.Room;
import com.lumoxu.cof.service.model.ComputerPlayerDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComputerPlayerAdvanceServiceTest {

    @Mock
    private ComputerPlayerService computerPlayerService;

    private ComputerPlayerAdvanceService advanceService;

    @BeforeEach
    void setUp() {
        advanceService = new ComputerPlayerAdvanceService(computerPlayerService);
        ComputerPlayerDto profile = new ComputerPlayerDto();
        profile.id = "computer_normal";
        profile.playDelayMeanSeconds = 0;
        profile.playDelayStdSeconds = 0;
        profile.reactionMeanSeconds = 0;
        profile.reactionStdSeconds = 0;
        profile.matchDetectionProbability = 0;
        profile.falseRingProbability = 0;
        when(computerPlayerService.findRequired(anyString())).thenReturn(profile);
    }

    @Test
    void computerPlaysWhenItIsTheirTurn() {
        Room room = new Room();
        room.id = "room-1";
        room.settings = GameSettings.defaultSettings();
        Player human = new Player();
        human.clientId = "human";
        human.username = "Human";
        Player bot = new Player();
        bot.clientId = "computer:1";
        bot.username = "Bot";
        bot.isComputer = true;
        bot.computerId = "computer_normal";
        bot.ready = true;
        Card c1 = new Card();
        c1.id = "c1";
        Card c2 = new Card();
        c2.id = "c2";
        Card c3 = new Card();
        c3.id = "c3";
        Game game = GameCore.createGame(room, List.of(human, bot), List.of(c1, c2, c3));
        GameCore.startPlaying(game, 1000L);
        game.turnIndex = 1;
        game.turnAvailableAt = 0;
        game.turnDeadlineAt = System.currentTimeMillis() + 60_000L;

        long now = System.currentTimeMillis();
        advanceService.advance(game, now);
        advanceService.advance(game, now + 500);
        ComputerTickOutcome outcome = advanceService.advance(game, now + 10_000);

        assertTrue(outcome.played || game.playCount > 0);
    }
}
