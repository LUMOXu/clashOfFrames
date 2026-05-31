package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.service.ComputerPlayerImportService;
import com.lumoxu.cof.service.DeckCatalogImportService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@Profile("local")
public class AdminImportController {

    private final DeckCatalogImportService deckCatalogImportService;
    private final ComputerPlayerImportService computerPlayerImportService;

    public AdminImportController(
            DeckCatalogImportService deckCatalogImportService,
            ComputerPlayerImportService computerPlayerImportService) {
        this.deckCatalogImportService = deckCatalogImportService;
        this.computerPlayerImportService = computerPlayerImportService;
    }

    @PostMapping("/import-decks")
    public ApiResponse<Map<String, Object>> importDecks() throws Exception {
        int count = deckCatalogImportService.importAllDecks();
        return ApiResponse.ok(Map.of("importedDecks", count));
    }

    @PostMapping("/import-computers")
    public ApiResponse<Map<String, Object>> importComputers() throws Exception {
        int count = computerPlayerImportService.importFromDefaultLocations();
        return ApiResponse.ok(Map.of("importedPlayers", count));
    }
}
