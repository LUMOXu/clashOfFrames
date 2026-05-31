package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.service.model.ComputerPlayerDto;
import com.lumoxu.cof.service.model.RoomState;
import com.lumoxu.cof.service.support.TestRedisSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
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
                playerRoomService,
                new PlayerPresenceService(redis));
        when(metaService.listLibraries()).thenReturn(List.of());
    }

    @Test
    void createRoomSetsHost() {
        RoomState room = roomService.createRoom("host-1", "Host", GameSettings.defaultSettings(), List.of());
        assertEquals("host-1", room.hostId);
        assertEquals("waiting", room.status);
    }

    @Test
    void transferHostRejectsComputer() {
        when(metaService.listLibraries()).thenReturn(List.of());
        RoomState room = roomService.createRoom("host-1", "Host", GameSettings.defaultSettings(), List.of());
        ComputerPlayerDto bot = new ComputerPlayerDto();
        bot.id = "bot-1";
        bot.name = "Bot";
        when(computerPlayerService.findRequired("bot-1")).thenReturn(bot);
        roomService.addComputer(room, "bot-1");
        String computerClientId = roomService.getRequired(room.id).players.stream()
                .filter(PlayerPresenceService::isComputerClient)
                .findFirst()
                .orElseThrow();

        assertThrows(CofException.class, () -> roomService.transferHost(room, "host-1", computerClientId));
    }

    @Test
    void leavePromotesHumanHostWhenComputersAreFirstInList() {
        when(metaService.listLibraries()).thenReturn(List.of());
        ComputerPlayerDto bot = new ComputerPlayerDto();
        bot.id = "bot-1";
        bot.name = "Bot";
        when(computerPlayerService.findRequired(anyString())).thenReturn(bot);

        RoomState room = roomService.createRoom("host-1", "Host", GameSettings.defaultSettings(), List.of("bot-1"));
        room = roomService.getRequired(room.id);
        String computerClientId = room.players.stream()
                .filter(PlayerPresenceService::isComputerClient)
                .findFirst()
                .orElseThrow();
        room.players.remove(computerClientId);
        room.players.add(0, computerClientId);
        room.players.add("guest-2");
        room.usernames.put("guest-2", "Guest");
        roomService.save(room);

        roomService.leave(room, "host-1");
        RoomState updated = roomService.getRequired(room.id);
        assertEquals("guest-2", updated.hostId);
    }
}
