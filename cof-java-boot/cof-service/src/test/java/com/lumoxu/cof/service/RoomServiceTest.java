package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.service.model.RoomState;
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
class RoomServiceTest {

    @Mock
    private MetaService metaService;
    @Mock
    private DeckCatalogService deckCatalogService;
    @Mock
    private GameRuntimeService gameRuntimeService;
    @Mock
    private ComputerPlayerService computerPlayerService;
    @Mock
    private UserStatsService userStatsService;
    private PlayerRoomService playerRoomService;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        var redis = TestRedisSupport.memoryJsonRedis(new ObjectMapper());
        playerRoomService = new PlayerRoomService(redis);
        roomService = new RoomService(
                redis,
                metaService,
                deckCatalogService,
                gameRuntimeService,
                computerPlayerService,
                userStatsService,
                playerRoomService);
        when(metaService.listLibraries()).thenReturn(List.of());
    }

    @Test
    void createRoomSetsHost() {
        RoomState room = roomService.createRoom("host-1", "Host", GameSettings.defaultSettings(), List.of());
        assertEquals("host-1", room.hostId);
        assertEquals("waiting", room.status);
    }
}
