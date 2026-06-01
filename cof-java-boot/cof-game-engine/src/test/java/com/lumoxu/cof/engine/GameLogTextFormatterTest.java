package com.lumoxu.cof.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLogTextFormatterTest {

    @Test
    void formatElapsedFromZero() {
        assertEquals("00:00:000", GameLogTextFormatter.formatElapsed(0));
        assertEquals("00:01:500", GameLogTextFormatter.formatElapsed(1_500));
        assertEquals("02:03:045", GameLogTextFormatter.formatElapsed(123_045));
    }

    @Test
    void formatOrdersLogsChronologicallyWithRelativeTimestamps() {
        Game game = new Game();
        game.createdAt = 1_000L;
        game.startedAt = 10_000L;
        GameLog first = new GameLog();
        first.text = "开局";
        first.at = 10_100L;
        GameLog second = new GameLog();
        second.text = "出牌";
        second.at = 12_250L;
        game.logs = List.of(second, first);

        String text = GameLogTextFormatter.format(game);
        assertTrue(text.contains("00:00:100 | 开局"));
        assertTrue(text.contains("00:02:250 | 出牌"));
        assertTrue(text.indexOf("开局") < text.indexOf("出牌"));
    }
}
