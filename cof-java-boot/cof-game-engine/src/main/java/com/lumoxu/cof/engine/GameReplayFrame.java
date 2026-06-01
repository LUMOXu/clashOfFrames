package com.lumoxu.cof.engine;

public class GameReplayFrame {

    /** Milliseconds since match start ({@link Game#startedAt} or {@link Game#createdAt}). */
    public long t;
    public PublicGame state;
}
