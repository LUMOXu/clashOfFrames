package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.domain.entity.CofComputerPlayer;
import com.lumoxu.cof.domain.mapper.CofComputerPlayerMapper;
import com.lumoxu.cof.service.model.ComputerPlayerDto;
import com.lumoxu.cof.service.support.TestRedisSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComputerPlayerServiceTest {

    @Mock
    private CofComputerPlayerMapper playerMapper;

    private ComputerPlayerService computerPlayerService;

    @BeforeEach
    void setUp() {
        computerPlayerService = new ComputerPlayerService(
                playerMapper,
                TestRedisSupport.memoryJsonRedis(new ObjectMapper()));
    }

    @Test
    void loadsPlayersFromDatabase() {
        CofComputerPlayer row = new CofComputerPlayer();
        row.computerId = "computer_easy";
        row.name = "Test Bot";
        row.playDelayMeanSeconds = 2.0;
        row.playDelayStdSeconds = 1.0;
        row.reactionMeanSeconds = 2.0;
        row.reactionStdSeconds = 1.0;
        row.matchDetectionProbability = 0.5;
        row.falseRingProbability = 0.1;
        when(playerMapper.selectList(null)).thenReturn(List.of(row));

        List<ComputerPlayerDto> players = computerPlayerService.listPlayers();
        assertEquals(1, players.size());
        assertEquals("computer_easy", players.get(0).id);
    }
}
