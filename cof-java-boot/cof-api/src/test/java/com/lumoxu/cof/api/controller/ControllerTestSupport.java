package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.api.auth.AuthInterceptor;
import com.lumoxu.cof.api.config.ApiWebConfig;
import com.lumoxu.cof.api.config.GlobalExceptionHandler;
import com.lumoxu.cof.common.auth.TokenPayload;
import com.lumoxu.cof.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

abstract class ControllerTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @MockBean
    protected SessionService sessionService;

    @BeforeEach
    void authDefaults() {
        when(sessionService.optionalToken(anyString())).thenReturn(Optional.empty());
        when(sessionService.requireToken("good-token")).thenReturn(
                new TokenPayload("good-token", UUID.fromString("00000000-0000-0000-0000-000000000001"), "alice", 1, 2));
    }

    protected static String bearer() {
        return "Bearer good-token";
    }
}
