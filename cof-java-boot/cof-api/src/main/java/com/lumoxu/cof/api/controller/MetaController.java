package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.service.ComputerPlayerService;
import com.lumoxu.cof.service.MetaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final MetaService metaService;
    private final ComputerPlayerService computerPlayerService;

    public MetaController(MetaService metaService, ComputerPlayerService computerPlayerService) {
        this.metaService = metaService;
        this.computerPlayerService = computerPlayerService;
    }

    @GetMapping("/card-libraries")
    public ApiResponse<Map<String, Object>> cardLibraries() {
        return ApiResponse.ok(metaService.publicLibrariesPayload());
    }

    @GetMapping("/computer-players")
    public ApiResponse<Map<String, Object>> computerPlayers() {
        return ApiResponse.ok(Map.of("players", computerPlayerService.publicPlayers()));
    }

    @GetMapping("/pmv-index")
    public ApiResponse<List<Map<String, Object>>> pmvIndex() {
        return ApiResponse.ok(metaService.pmvIndex());
    }
}
