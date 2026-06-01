package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.service.UserStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProfileController.class)
@MvcTestConfig
class ProfileControllerTest extends ControllerTestSupport {

    @MockBean
    private UserStatsService userStatsService;

    @Test
    void profileUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/profile/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileOk() throws Exception {
        when(userStatsService.profileFor(anyString())).thenReturn(Map.of("username", "alice"));
        mockMvc.perform(get("/api/v1/profile/00000000-0000-0000-0000-000000000001")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void replayOk() throws Exception {
        when(userStatsService.matchReplayFor(anyString(), anyString()))
                .thenReturn(Map.of("gameId", "game-1", "logText", "00:00:000 | start"));
        mockMvc.perform(get("/api/v1/profile/00000000-0000-0000-0000-000000000001/games/game-1/replay")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
    }
}
