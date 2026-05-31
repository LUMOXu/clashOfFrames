package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.service.model.PlayerPresence;
import com.lumoxu.cof.service.model.RoomState;
import com.lumoxu.cof.service.redis.RedisKeys;
import com.lumoxu.cof.service.support.TestRedisSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomMaintenanceServiceTest {

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

    private com.lumoxu.cof.service.redis.JsonRedisOps redis;
    private RoomService roomService;
    private PlayerPresenceService playerPresenceService;
    private RoomMaintenanceService roomMaintenanceService;

    @BeforeEach
    void setUp() {
        redis = TestRedisSupport.memoryJsonRedis(new ObjectMapper());
        PlayerRoomService playerRoomService = new PlayerRoomService(redis);
        playerPresenceService = new PlayerPresenceService(redis);
        roomService = new RoomService(
                redis,
                metaService,
                deckCatalogService,
                gameRuntimeService,
                computerPlayerService,
                userStatsService,
                playerRoomService,
                playerPresenceService);
        roomMaintenanceService = new RoomMaintenanceService(roomService, playerPresenceService);
    }

    @Test
    void disbandsWhenOnlyComputersRemain() {
        RoomState room = new RoomState();
        room.id = "bots-only";
        room.hostId = "computer:bots-only:bot";
        room.players.add("computer:bots-only:bot");
        room.settings = GameSettings.defaultSettings();
        roomService.save(room);

        assertTrue(roomMaintenanceService.maintain(room, System.currentTimeMillis()));
        assertTrue(roomService.get(room.id).isEmpty());
    }

    @Test
    void disbandsWhenAllHumansOfflineForTenMinutes() {
        when(metaService.listLibraries()).thenReturn(List.of());
        RoomState room = roomService.createRoom("host-1", "Host", GameSettings.defaultSettings(), List.of());
        long now = System.currentTimeMillis();
        PlayerPresence offline = new PlayerPresence(false, now - PlayerPresenceService.ALL_HUMANS_OFFLINE_DISBAND_MS - 1);
        redis.set(RedisKeys.playerPresence("host-1"), offline, PlayerPresenceService.PRESENCE_TTL);

        assertTrue(roomMaintenanceService.shouldAutoDisband(room, now));
        assertTrue(roomMaintenanceService.maintain(room, now));
        assertTrue(roomService.get(room.id).isEmpty());
    }

    @Test
    void keepsRoomWhenOneHumanIsStillOnline() {
        when(metaService.listLibraries()).thenReturn(List.of());
        RoomState room = roomService.createRoom("host-1", "Host", GameSettings.defaultSettings(), List.of());
        RoomState joined = roomService.getRequired(room.id);
        joined.players.add("guest-2");
        roomService.save(joined);

        long now = System.currentTimeMillis();
        PlayerPresence offline = new PlayerPresence(false, now - PlayerPresenceService.ALL_HUMANS_OFFLINE_DISBAND_MS - 1);
        redis.set(RedisKeys.playerPresence("guest-2"), offline, PlayerPresenceService.PRESENCE_TTL);
        playerPresenceService.touchConnected("host-1");

        assertFalse(roomMaintenanceService.shouldAutoDisband(joined, now));
    }
}
