package com.lumoxu.cof.service;

public class ContinueRoomResult {

    public enum Kind {
        NONE,
        COUNTDOWN_STARTED,
        RETURNED_TO_WAITING
    }

    public final Kind kind;

    private ContinueRoomResult(Kind kind) {
        this.kind = kind;
    }

    public static ContinueRoomResult none() {
        return new ContinueRoomResult(Kind.NONE);
    }

    public static ContinueRoomResult countdownStarted() {
        return new ContinueRoomResult(Kind.COUNTDOWN_STARTED);
    }

    public static ContinueRoomResult returnedToWaiting() {
        return new ContinueRoomResult(Kind.RETURNED_TO_WAITING);
    }

    public boolean changed() {
        return kind != Kind.NONE;
    }
}
