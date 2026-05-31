package com.lumoxu.cof.boot.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public final class ContractRedisSupport {

    private ContractRedisSupport() {
    }

    public static JsonRedisOps memoryJsonRedis(ObjectMapper objectMapper) {
        Map<String, String> store = new HashMap<>();
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
        return redis;
    }
}
