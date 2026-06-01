package com.lumoxu.cof.engine;

import java.util.ArrayList;

/**
 * Captures {@link PublicGame} snapshots during a match for visual replay.
 */
public final class GameReplayRecorder {

    private static final int MAX_FRAMES = 500;

    private GameReplayRecorder() {
    }

    public static void record(Game game, long now) {
        if (game == null || !shouldRecord(game)) {
            return;
        }
        if (game.replayFrames == null) {
            game.replayFrames = new ArrayList<>();
        }
        while (game.replayFrames.size() >= MAX_FRAMES) {
            game.replayFrames.remove(0);
        }
        GameReplayFrame frame = new GameReplayFrame();
        frame.t = elapsedMs(game, now);
        frame.state = GameCore.publicGame(game);
        game.replayFrames.add(frame);
    }

    public static GameReplayTimeline buildTimeline(Game game) {
        GameReplayTimeline timeline = new GameReplayTimeline();
        if (game == null) {
            return timeline;
        }
        timeline.startedAt = game.startedAt != null ? game.startedAt : game.createdAt;
        timeline.defaultViewerId = pickViewerId(game);
        if (game.replayFrames != null) {
            timeline.frames.addAll(game.replayFrames);
        }
        return timeline;
    }

    private static boolean shouldRecord(Game game) {
        return "playing".equals(game.status) || "finished".equals(game.status);
    }

    private static long elapsedMs(Game game, long now) {
        long base = game.startedAt != null ? game.startedAt : game.createdAt;
        return Math.max(0, now - base);
    }

    private static String pickViewerId(Game game) {
        if (game.players == null) {
            return null;
        }
        for (Player player : game.players) {
            if (player != null && !player.isComputer && player.clientId != null) {
                return player.clientId;
            }
        }
        for (Player player : game.players) {
            if (player != null && player.clientId != null) {
                return player.clientId;
            }
        }
        return null;
    }
}
