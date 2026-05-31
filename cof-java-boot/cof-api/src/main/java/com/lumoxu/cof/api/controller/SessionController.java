package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.api.auth.AuthContext;
import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.common.auth.TokenPayload;
import com.lumoxu.cof.service.ComputerPlayerService;
import com.lumoxu.cof.service.MetaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/session")
public class SessionController {

    private final MetaService metaService;
    private final ComputerPlayerService computerPlayerService;

    public SessionController(MetaService metaService, ComputerPlayerService computerPlayerService) {
        this.metaService = metaService;
        this.computerPlayerService = computerPlayerService;
    }

    @GetMapping("/bootstrap")
    public ApiResponse<Map<String, Object>> bootstrap(HttpServletRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("libraries", metaService.publicLibrariesPayload().get("libraries"));
        payload.put("computerPlayers", computerPlayerService.publicPlayers());
        TokenPayload auth = AuthContext.get();
        if (auth != null) {
            Map<String, Object> me = new HashMap<>();
            me.put("clientId", auth.clientId.toString());
            me.put("username", auth.username);
            payload.put("player", me);
        }
        return ApiResponse.ok(payload);
    }
}
