package com.lumoxu.cof.service.model;

public class PlayerPresence {

    public boolean connected = true;
    public long lastSeenAt;

    public PlayerPresence() {
    }

    public PlayerPresence(boolean connected, long lastSeenAt) {
        this.connected = connected;
        this.lastSeenAt = lastSeenAt;
    }
}
