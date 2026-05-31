package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.service.CardViewerService;
import com.lumoxu.cof.service.ComputerPlayerService;
import com.lumoxu.cof.service.MetaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final MetaService metaService;
    private final ComputerPlayerService computerPlayerService;
    private final CardViewerService cardViewerService;

    public MetaController(
            MetaService metaService,
            ComputerPlayerService computerPlayerService,
            CardViewerService cardViewerService) {
        this.metaService = metaService;
        this.computerPlayerService = computerPlayerService;
        this.cardViewerService = cardViewerService;
    }

    @GetMapping("/card-libraries")
    public ApiResponse<Map<String, Object>> cardLibraries() {
        return ApiResponse.ok(metaService.publicLibrariesPayload());
    }

    @GetMapping("/computer-players")
    public ApiResponse<Map<String, Object>> computerPlayers() {
        return ApiResponse.ok(Map.of("players", computerPlayerService.listPlayers()));
    }

    @GetMapping("/pmv-index")
    public ApiResponse<List<Map<String, Object>>> pmvIndex() {
        return ApiResponse.ok(metaService.pmvIndex());
    }

    @GetMapping("/card-viewer")
    public ApiResponse<Map<String, Object>> cardViewer(@RequestParam(value = "libraryIds", required = false) String libraryIds) {
        List<String> ids = libraryIds == null || libraryIds.isBlank()
                ? List.of()
                : Arrays.stream(libraryIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return ApiResponse.ok(cardViewerService.buildViewerPayload(ids));
    }
}
