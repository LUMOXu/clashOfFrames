package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.service.UserStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LeaderboardController.class)
@MvcTestConfig
class LeaderboardControllerTest extends ControllerTestSupport {

    @MockBean
    private UserStatsService userStatsService;

    @Test
    void leaderboardOk() throws Exception {
        when(userStatsService.leaderboard()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/leaderboard")).andExpect(status().isOk());
    }
}
