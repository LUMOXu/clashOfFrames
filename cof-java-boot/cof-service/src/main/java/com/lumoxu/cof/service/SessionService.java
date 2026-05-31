package com.lumoxu.cof.service;

import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.common.auth.TokenPayload;
import com.lumoxu.cof.service.model.SessionRecord;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import com.lumoxu.cof.service.redis.RedisKeys;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    public static final Duration SESSION_TTL = Duration.ofDays(7);

    private final JsonRedisOps redis;
    private final PlayerPresenceService playerPresenceService;
    private final SecureRandom random = new SecureRandom();

    public SessionService(JsonRedisOps redis, PlayerPresenceService playerPresenceService) {
        this.redis = redis;
        this.playerPresenceService = playerPresenceService;
    }

    public String createSession(UUID clientId, String username) {
        String token = newToken();
        long now = System.currentTimeMillis();
        SessionRecord record = new SessionRecord(clientId, username, now, now);
        redis.set(RedisKeys.session(token), record, SESSION_TTL);
        return token;
    }

    public TokenPayload requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new CofException(ErrorCode.UNAUTHORIZED, "请先登录。");
        }
        SessionRecord record = redis.get(RedisKeys.session(token), SessionRecord.class)
                .orElseThrow(() -> new CofException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录。"));
        long now = System.currentTimeMillis();
        record.lastSeenAt = now;
        redis.set(RedisKeys.session(token), record, SESSION_TTL);
        playerPresenceService.touchConnected(record.clientId.toString());
        return new TokenPayload(token, record.clientId, record.username, record.createdAt, record.lastSeenAt);
    }

    public Optional<TokenPayload> optionalToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(requireToken(token));
        } catch (CofException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(RedisKeys.session(token));
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
