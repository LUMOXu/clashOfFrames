package com.lumoxu.cof.engine;

public final class GameConstants {

    public static final int TURN_TIMEOUT_MS = 8000;
    public static final int DISCONNECTED_TURN_TIMEOUT_MS = 2000;
    public static final long MANUAL_TURN_TIMEOUT_MS = 24L * 60 * 60 * 1000;
    public static final int SUCCESS_HIGHLIGHT_MS = 3000;
    public static final int SUCCESS_MOVE_MS = 1200;
    public static final int FAIL_MOVE_MS = 900;
    public static final int FAIL_STAGGER_MS = 300;
    public static final int PUBLIC_LOG_LIMIT = 40;

    private GameConstants() {
    }
}
