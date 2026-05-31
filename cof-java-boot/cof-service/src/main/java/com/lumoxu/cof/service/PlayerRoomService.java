package com.lumoxu.cof.service;

import com.lumoxu.cof.service.redis.JsonRedisOps;
import com.lumoxu.cof.service.redis.RedisKeys;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PlayerRoomService {

    private final JsonRedisOps redis;

    public PlayerRoomService(JsonRedisOps redis) {
        this.redis = redis;
    }

    public void bind(String clientId, String roomId) {
        redis.set(RedisKeys.playerRoom(clientId), roomId, null);
    }

    public void unbind(String clientId) {
        redis.delete(RedisKeys.playerRoom(clientId));
    }

    public Optional<String> findRoomId(String clientId) {
        return redis.get(RedisKeys.playerRoom(clientId), String.class);
    }
}
