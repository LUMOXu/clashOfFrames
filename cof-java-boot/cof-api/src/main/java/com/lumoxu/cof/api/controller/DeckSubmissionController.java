package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.api.auth.AuthContext;
import com.lumoxu.cof.api.auth.RequireAuth;
import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.service.DeckSubmissionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/submissions")
@RequireAuth
public class DeckSubmissionController {

    private final DeckSubmissionService submissionService;

    public DeckSubmissionController(DeckSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @GetMapping("/pmvs")
    public ApiResponse<Map<String, Object>> listPmvs() {
        String clientId = AuthContext.get().clientId.toString();
        List<Map<String, Object>> pmvs = submissionService.listPmvsForPicker(clientId);
        return ApiResponse.ok(Map.of("pmvs", pmvs));
    }

    @GetMapping("/mine")
    public ApiResponse<Map<String, Object>> mine() {
        String clientId = AuthContext.get().clientId.toString();
        List<Map<String, Object>> decks = submissionService.listMine(clientId);
        return ApiResponse.ok(Map.of("decks", decks));
    }

    @PostMapping("/decks")
    public ApiResponse<Map<String, Object>> createDeck(@RequestBody Map<String, Object> body) {
        String clientId = AuthContext.get().clientId.toString();
        return ApiResponse.ok(Map.of("deck", submissionService.createDeck(clientId, body)));
    }

    @PatchMapping("/decks/{deckId}")
    public ApiResponse<Map<String, Object>> updateDeck(
            @PathVariable("deckId") long deckId,
            @RequestBody Map<String, Object> body) {
        String clientId = AuthContext.get().clientId.toString();
        return ApiResponse.ok(Map.of("deck", submissionService.updateDeck(clientId, deckId, body)));
    }

    @PostMapping("/decks/{deckId}/back")
    public ApiResponse<Map<String, Object>> uploadBack(
            @PathVariable("deckId") long deckId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "cropX", required = false) Integer cropX,
            @RequestParam(value = "cropY", required = false) Integer cropY,
            @RequestParam(value = "cropWidth", required = false) Integer cropWidth,
            @RequestParam(value = "cropHeight", required = false) Integer cropHeight) throws Exception {
        String clientId = AuthContext.get().clientId.toString();
        return ApiResponse.ok(Map.of(
                "deck",
                submissionService.uploadDeckBack(
                        clientId, deckId, file.getInputStream(), file.getSize(), cropX, cropY, cropWidth, cropHeight)));
    }

    @PostMapping("/pmvs")
    public ApiResponse<Map<String, Object>> createPmv(@RequestBody Map<String, Object> body) {
        String clientId = AuthContext.get().clientId.toString();
        return ApiResponse.ok(Map.of("pmv", submissionService.createPmv(clientId, body)));
    }

    @PatchMapping("/pmvs/{pmvId}")
    public ApiResponse<Map<String, Object>> updatePmv(
            @PathVariable("pmvId") long pmvId,
            @RequestBody Map<String, Object> body) {
        String clientId = AuthContext.get().clientId.toString();
        return ApiResponse.ok(Map.of("pmv", submissionService.updatePmv(clientId, pmvId, body)));
    }

    @PostMapping("/decks/{deckId}/cards")
    public ApiResponse<Map<String, Object>> addCard(
            @PathVariable("deckId") long deckId,
            @RequestParam("pmvId") long pmvId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "cropX", required = false) Integer cropX,
            @RequestParam(value = "cropY", required = false) Integer cropY,
            @RequestParam(value = "cropWidth", required = false) Integer cropWidth,
            @RequestParam(value = "cropHeight", required = false) Integer cropHeight) throws Exception {
        String clientId = AuthContext.get().clientId.toString();
        return ApiResponse.ok(Map.of(
                "card",
                submissionService.addCard(
                        clientId,
                        deckId,
                        pmvId,
                        name,
                        description,
                        file.getInputStream(),
                        file.getSize(),
                        cropX,
                        cropY,
                        cropWidth,
                        cropHeight)));
    }

    @PatchMapping("/cards/{cardId}")
    public ApiResponse<Map<String, Object>> updateCard(
            @PathVariable("cardId") long cardId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "cropX", required = false) Integer cropX,
            @RequestParam(value = "cropY", required = false) Integer cropY,
            @RequestParam(value = "cropWidth", required = false) Integer cropWidth,
            @RequestParam(value = "cropHeight", required = false) Integer cropHeight) throws Exception {
        String clientId = AuthContext.get().clientId.toString();
        return ApiResponse.ok(Map.of(
                "card",
                submissionService.updateCard(
                        clientId,
                        cardId,
                        name,
                        description,
                        file != null ? file.getInputStream() : null,
                        file != null ? file.getSize() : 0,
                        cropX,
                        cropY,
                        cropWidth,
                        cropHeight)));
    }

    @DeleteMapping("/decks/{deckId}")
    public ApiResponse<Map<String, Object>> deleteDeck(@PathVariable("deckId") long deckId) {
        String clientId = AuthContext.get().clientId.toString();
        submissionService.deleteDeck(clientId, deckId);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @DeleteMapping("/pmvs/{pmvId}")
    public ApiResponse<Map<String, Object>> deletePmv(@PathVariable("pmvId") long pmvId) {
        String clientId = AuthContext.get().clientId.toString();
        submissionService.deletePmv(clientId, pmvId);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @DeleteMapping("/cards/{cardId}")
    public ApiResponse<Map<String, Object>> deleteCard(@PathVariable("cardId") long cardId) {
        String clientId = AuthContext.get().clientId.toString();
        submissionService.deleteCard(clientId, cardId);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
