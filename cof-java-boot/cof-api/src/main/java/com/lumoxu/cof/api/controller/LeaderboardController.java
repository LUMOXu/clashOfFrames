package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.service.UserStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/leaderboard")
public class LeaderboardController {

    private final UserStatsService userStatsService;

    public LeaderboardController(UserStatsService userStatsService) {
        this.userStatsService = userStatsService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> leaderboard() {
        return ApiResponse.ok(userStatsService.leaderboard());
    }
}
