package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.api.auth.AuthContext;
import com.lumoxu.cof.api.auth.RequireAuth;
import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.service.UserStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
@RequireAuth
public class ProfileController {

    private final UserStatsService userStatsService;

    public ProfileController(UserStatsService userStatsService) {
        this.userStatsService = userStatsService;
    }

    @GetMapping("/{clientId}")
    public ApiResponse<Map<String, Object>> profile(@PathVariable("clientId") String clientId) {
        String self = AuthContext.get().clientId.toString();
        if (!self.equals(clientId)) {
            throw new CofException(ErrorCode.FORBIDDEN, "只能查看自己的个人信息。");
        }
        return ApiResponse.ok(Map.of("profile", userStatsService.profileFor(clientId)));
    }
}
