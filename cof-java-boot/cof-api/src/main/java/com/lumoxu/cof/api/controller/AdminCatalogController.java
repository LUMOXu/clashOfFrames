package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.service.DeckCatalogReviewService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Cache and publication helpers for operators (e.g. after {@code deploy/review_submissions.py}).
 */
@RestController
@RequestMapping("/api/v1/admin/catalog")
public class AdminCatalogController {

    private final DeckCatalogReviewService deckCatalogReviewService;

    public AdminCatalogController(DeckCatalogReviewService deckCatalogReviewService) {
        this.deckCatalogReviewService = deckCatalogReviewService;
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refreshCaches() {
        deckCatalogReviewService.refreshCaches();
        return ApiResponse.ok(Map.of("refreshed", true));
    }

    @PostMapping("/reconcile")
    public ApiResponse<Map<String, Object>> reconcile() {
        deckCatalogReviewService.reconcileAndRefreshCaches();
        return ApiResponse.ok(Map.of("reconciled", true));
    }
}
