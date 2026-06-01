package com.lumoxu.cof.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameReplayRecorderTest {

    @Test
    void recordsFramesDuringPlay() {
        Room room = new Room();
        room.id = "room-replay";
        room.settings = GameSettings.defaultSettings();
        Player human = new Player();
        human.clientId = "p1";
        human.username = "P1";
        Card c1 = new Card();
        c1.id = "c1";
        Card c2 = new Card();
        c2.id = "c2";
        Game game = GameCore.createGame(room, List.of(human), List.of(c1, c2));
        GameCore.startPlaying(game, 10_000L);
        GameCore.performPlayCard(game, "p1", 10_500L, false);

        GameReplayTimeline timeline = GameReplayRecorder.buildTimeline(game);
        assertFalse(timeline.frames.isEmpty());
        assertTrue(timeline.frames.size() >= 2);
        assertTrue(timeline.frames.get(0).t <= timeline.frames.get(timeline.frames.size() - 1).t);
        assertTrue(timeline.defaultViewerId.equals("p1"));
    }
}
