package com.lumoxu.cof.common.auth;

import java.util.UUID;

public class TokenPayload {

    public String token;
    public UUID clientId;
    public String username;
    public long createdAt;
    public long lastSeenAt;

    public TokenPayload() {
    }

    public TokenPayload(String token, UUID clientId, String username, long createdAt, long lastSeenAt) {
        this.token = token;
        this.clientId = clientId;
        this.username = username;
        this.createdAt = createdAt;
        this.lastSeenAt = lastSeenAt;
    }
}
