package com.lumoxu.cof.service.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public final class TestRedisSupport {

    private TestRedisSupport() {
    }

    public static JsonRedisOps memoryJsonRedis(ObjectMapper objectMapper) {
        Map<String, String> store = new HashMap<>();
        Map<String, Set<String>> sets = new HashMap<>();
        JsonRedisOps redis = mock(JsonRedisOps.class);
        Answer<Optional<?>> getAnswer = invocation -> {
            String key = invocation.getArgument(0);
            String raw = store.get(key);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            Object typeArg = invocation.getArgument(1);
            try {
                if (typeArg instanceof TypeReference<?> ref) {
                    return Optional.of(objectMapper.readValue(raw, ref));
                }
                Class<?> clazz = (Class<?>) typeArg;
                return Optional.of(objectMapper.readValue(raw, clazz));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        };
        lenient().when(redis.get(anyString(), any(Class.class))).thenAnswer(getAnswer);
        lenient().when(redis.get(anyString(), any(TypeReference.class))).thenAnswer(getAnswer);
        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = invocation.getArgument(1);
            store.put(key, objectMapper.writeValueAsString(value));
            return null;
        }).when(redis).set(anyString(), any(), any());
        lenient().doAnswer(invocation -> {
            store.remove(invocation.getArgument(0));
            return null;
        }).when(redis).delete(anyString());
        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String member = invocation.getArgument(1);
            sets.computeIfAbsent(key, k -> new HashSet<>()).add(member);
            return null;
        }).when(redis).setAdd(anyString(), anyString());
        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String member = invocation.getArgument(1);
            Set<String> set = sets.get(key);
            if (set != null) {
                set.remove(member);
            }
            return null;
        }).when(redis).setRemove(anyString(), anyString());
        lenient().when(redis.setMembers(anyString())).thenAnswer(invocation -> {
            Set<String> set = sets.get(invocation.getArgument(0));
            return set == null ? Collections.emptySet() : Set.copyOf(set);
        });
        return redis;
    }
}
