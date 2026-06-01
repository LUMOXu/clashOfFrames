package com.lumoxu.cof.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Formats in-memory game logs as a single chronological text block with elapsed timestamps
 * from match start ({@code mm:ss:SSS}).
 */
public final class GameLogTextFormatter {

    private GameLogTextFormatter() {
    }

    public static String format(Game game) {
        if (game == null || game.logs == null || game.logs.isEmpty()) {
            return "";
        }
        long base = game.startedAt != null ? game.startedAt : game.createdAt;
        List<GameLog> ordered = new ArrayList<>(game.logs);
        ordered.sort(Comparator.comparingLong(log -> log.at));
        StringBuilder out = new StringBuilder();
        for (GameLog log : ordered) {
            if (log == null || log.text == null) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(formatElapsed(log.at - base)).append(" | ").append(log.text.trim());
        }
        return out.toString();
    }

    static String formatElapsed(long elapsedMs) {
        if (elapsedMs < 0) {
            elapsedMs = 0;
        }
        long minutes = elapsedMs / 60_000;
        long seconds = (elapsedMs % 60_000) / 1_000;
        long millis = elapsedMs % 1_000;
        return String.format("%02d:%02d:%03d", minutes, seconds, millis);
    }
}
