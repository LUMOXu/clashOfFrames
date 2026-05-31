package com.lumoxu.cof.service;

import com.lumoxu.cof.domain.mapper.CofUserMapper;
import com.lumoxu.cof.domain.mapper.CofUserStatsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CofUserMapper userMapper;
    @Mock
    private CofUserStatsMapper statsMapper;
    @Mock
    private SessionService sessionService;
    @Mock
    private UserStatsService userStatsService;

    @InjectMocks
    private AuthService authService;

    @Test
    void cleanNameTrimsWhitespace() {
        assertEquals("bob", AuthService.cleanName("  bob "));
    }
}
