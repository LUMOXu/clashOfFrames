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

/**
 * 真人出牌后轮到人机时，人机应在有限 tick 内完成出牌（回归：状态机单步+Redis 竞态导致卡死）。
 */
@ExtendWith(MockitoExtension.class)
class ComputerPlayerHumanHandoffTest {

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
    void botPlaysSoonAfterHumanPlay() {
        Room room = new Room();
        room.id = "room-handoff";
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

        Card c1 = card("c1");
        Card c2 = card("c2");
        Card c3 = card("c3");
        Card c4 = card("c4");
        Game game = GameCore.createGame(room, List.of(human, bot), List.of(c1, c2, c3, c4));
        GameCore.startPlaying(game, 1000L);
        game.turnIndex = 0;
        game.turnAvailableAt = 0;
        game.turnDeadlineAt = System.currentTimeMillis() + 60_000L;

        long t0 = System.currentTimeMillis();
        var humanPlay = GameCore.performPlayCard(game, human.clientId, t0, false);
        assertTrue(humanPlay.ok);
        assertTrue(game.turnIndex == 1, "turn should pass to bot");

        int playCountAfterHuman = game.playCount;
        boolean botPlayed = false;
        for (int tick = 0; tick < 30; tick++) {
            long now = t0 + 500L + tick * 120L;
            ComputerTickOutcome outcome = advanceService.advance(game, now);
            if (outcome.played || game.playCount > playCountAfterHuman) {
                botPlayed = true;
                break;
            }
        }
        assertTrue(botPlayed, "bot should play within ~3.6s simulated ticks");
    }

    private static Card card(String id) {
        Card c = new Card();
        c.id = id;
        return c;
    }
}
