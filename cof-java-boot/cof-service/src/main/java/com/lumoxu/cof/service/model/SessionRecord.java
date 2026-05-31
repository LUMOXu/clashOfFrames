package com.lumoxu.cof.service.model;

import java.util.UUID;

public class SessionRecord {

    public UUID clientId;
    public String username;
    public long createdAt;
    public long lastSeenAt;

    public SessionRecord() {
    }

    public SessionRecord(UUID clientId, String username, long createdAt, long lastSeenAt) {
        this.clientId = clientId;
        this.username = username;
        this.createdAt = createdAt;
        this.lastSeenAt = lastSeenAt;
    }
}
