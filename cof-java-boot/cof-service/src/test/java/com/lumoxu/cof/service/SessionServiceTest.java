package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.common.auth.TokenPayload;
import com.lumoxu.cof.service.support.TestRedisSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SessionServiceTest {

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(TestRedisSupport.memoryJsonRedis(new ObjectMapper()));
    }

    @Test
    void createsAndValidatesSession() {
        UUID clientId = UUID.randomUUID();
        String token = sessionService.createSession(clientId, "alice");
        TokenPayload payload = sessionService.requireToken(token);
        assertEquals(clientId, payload.clientId);
        assertEquals("alice", payload.username);
        assertNotNull(payload.token);
    }
}
