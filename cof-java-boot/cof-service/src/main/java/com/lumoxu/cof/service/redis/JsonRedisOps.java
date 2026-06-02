package com.lumoxu.cof.service.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class JsonRedisOps {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public JsonRedisOps(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        String raw = redis.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, type));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize " + key, ex);
        }
    }

    public <T> Optional<T> get(String key, TypeReference<T> type) {
        String raw = redis.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, type));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize " + key, ex);
        }
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (ttl == null) {
                redis.opsForValue().set(key, json);
            } else {
                redis.opsForValue().set(key, json, ttl);
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize " + key, ex);
        }
    }

    public void delete(String key) {
        redis.delete(key);
    }

    /** Removes all keys starting with {@code prefix} (Redis KEYS scan). */
    public void deleteByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        Set<String> keys = redis.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    public void setAdd(String key, String member) {
        redis.opsForSet().add(key, member);
    }

    public void setRemove(String key, String member) {
        redis.opsForSet().remove(key, member);
    }

    public Set<String> setMembers(String key) {
        Set<String> members = redis.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        return members.stream().filter(m -> m != null && !m.isBlank()).collect(Collectors.toSet());
    }
}
