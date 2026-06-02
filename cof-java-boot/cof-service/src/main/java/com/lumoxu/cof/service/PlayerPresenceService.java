package com.lumoxu.cof.service;

import com.lumoxu.cof.service.model.PlayerPresence;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import com.lumoxu.cof.service.redis.RedisKeys;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class PlayerPresenceService {

    public static final Duration PRESENCE_TTL = Duration.ofDays(7);
    public static final long ALL_HUMANS_OFFLINE_DISBAND_MS = 10L * 60L * 1000L;

    private final JsonRedisOps redis;

    public PlayerPresenceService(JsonRedisOps redis) {
        this.redis = redis;
    }

    public void touchConnected(String clientId) {
        if (clientId == null || clientId.isBlank() || isComputerClient(clientId)) {
            return;
        }
        long now = System.currentTimeMillis();
        PlayerPresence presence = get(clientId).orElse(new PlayerPresence());
        presence.connected = true;
        presence.lastSeenAt = now;
        save(clientId, presence);
    }

    public void markConnected(String clientId, boolean connected) {
        if (clientId == null || clientId.isBlank() || isComputerClient(clientId)) {
            return;
        }
        long now = System.currentTimeMillis();
        PlayerPresence presence = get(clientId).orElse(new PlayerPresence());
        presence.connected = connected;
        presence.lastSeenAt = now;
        save(clientId, presence);
    }

    public Optional<PlayerPresence> get(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        return redis.get(RedisKeys.playerPresence(clientId), PlayerPresence.class);
    }

    public boolean isOfflineTooLong(String clientId, long now) {
        if (isComputerClient(clientId)) {
            return false;
        }
        Optional<PlayerPresence> presence = get(clientId);
        // 尚无 presence（刚建房/仅 REST 未走 WS）不应视为长期离线，否则首 tick 会解散房间、人机永不推进
        if (presence.isEmpty()) {
            return false;
        }
        PlayerPresence record = presence.get();
        if (record.connected) {
            return false;
        }
        return now - record.lastSeenAt >= ALL_HUMANS_OFFLINE_DISBAND_MS;
    }

    public static boolean isComputerClient(String clientId) {
        return clientId != null && clientId.startsWith("computer:");
    }

    private void save(String clientId, PlayerPresence presence) {
        redis.set(RedisKeys.playerPresence(clientId), presence, PRESENCE_TTL);
    }
}
