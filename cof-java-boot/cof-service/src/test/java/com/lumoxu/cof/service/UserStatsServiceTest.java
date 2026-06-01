package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.domain.entity.CofMatchHistory;
import com.lumoxu.cof.domain.entity.CofUserStats;
import com.lumoxu.cof.domain.mapper.CofMatchHistoryMapper;
import com.lumoxu.cof.domain.mapper.CofUserStatsMapper;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameCore;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.engine.Room;
import com.lumoxu.cof.service.support.TestRedisSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserStatsServiceTest {

    @Mock
    private CofUserStatsMapper statsMapper;

    @Mock
    private CofMatchHistoryMapper matchHistoryMapper;

    private UserStatsService userStatsService;

    @BeforeEach
    void setUp() {
        userStatsService = new UserStatsService(
                statsMapper,
                matchHistoryMapper,
                TestRedisSupport.memoryJsonRedis(new ObjectMapper()),
                new ObjectMapper());
    }

    @Test
    void recordFinishedGamePersistsStatsAndHistory() {
        Room room = new Room();
        room.id = "room-stats";
        room.settings = GameSettings.defaultSettings();
        Player human = new Player();
        human.clientId = "human-stats";
        human.username = "Human";
        human.statsId = "human-stats";
        human.rank = 1;
        Game game = GameCore.createGame(room, List.of(human), List.of(new com.lumoxu.cof.engine.Card(), new com.lumoxu.cof.engine.Card()));
        GameCore.startPlaying(game, 1000L);
        game.status = "finished";
        game.winnerId = "human-stats";
        game.finishedAt = 2000L;

        when(statsMapper.selectById("human-stats")).thenReturn(null);
        when(matchHistoryMapper.insert(any(CofMatchHistory.class))).thenReturn(1);

        assertTrue(userStatsService.recordFinishedGame(game));
        verify(statsMapper).insert(any(CofUserStats.class));
        verify(matchHistoryMapper).insert(any(CofMatchHistory.class));
        assertTrue(Boolean.TRUE.equals(game.statsSaved));
    }

    @Test
    void ensureStatsInsertsWhenMissing() {
        when(statsMapper.selectById("u1")).thenReturn(null);
        CofUserStats stats = userStatsService.ensureStats("u1", "Alice", false, null);
        assertEquals("u1", stats.statsId);
        verify(statsMapper).insert(any(CofUserStats.class));
    }
}
