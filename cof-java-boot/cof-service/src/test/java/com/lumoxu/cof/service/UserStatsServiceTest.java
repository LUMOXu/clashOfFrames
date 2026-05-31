package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.domain.entity.CofUserStats;
import com.lumoxu.cof.domain.mapper.CofUserStatsMapper;
import com.lumoxu.cof.service.support.TestRedisSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserStatsServiceTest {

    @Mock
    private CofUserStatsMapper statsMapper;

    private UserStatsService userStatsService;

    @BeforeEach
    void setUp() {
        userStatsService = new UserStatsService(
                statsMapper,
                TestRedisSupport.memoryJsonRedis(new ObjectMapper()),
                new ObjectMapper());
    }

    @Test
    void ensureStatsInsertsWhenMissing() {
        when(statsMapper.selectById("u1")).thenReturn(null);
        CofUserStats stats = userStatsService.ensureStats("u1", "Alice", false, null);
        assertEquals("u1", stats.statsId);
        verify(statsMapper).insert(any(CofUserStats.class));
    }
}
