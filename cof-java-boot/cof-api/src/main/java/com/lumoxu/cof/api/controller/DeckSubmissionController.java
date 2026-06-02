package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.api.auth.AuthContext;
import com.lumoxu.cof.api.auth.RequireAuth;
import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.service.DeckSubmissionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/decks/editable")
    public ApiResponse<Map<String, Object>> editableDecks() {
        String clientId = AuthContext.get().clientId.toString();
        return ApiResponse.ok(Map.of("decks", submissionService.listEditableDecks(clientId)));
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

    @PostMapping("/decks/{deckId}/pmvs")
    public ApiResponse<Map<String, Object>> addPmv(
            @PathVariable("deckId") long deckId,
            @RequestBody Map<String, Object> body) {
        String clientId = AuthContext.get().clientId.toString();
        return ApiResponse.ok(Map.of("pmv", submissionService.addPmv(clientId, deckId, body)));
    }

    @PostMapping("/decks/{deckId}/cards")
    public ApiResponse<Map<String, Object>> addCard(
            @PathVariable("deckId") long deckId,
            @RequestParam("pmvId") int pmvId,
            @RequestParam("shot") String shot,
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
                        shot,
                        file.getInputStream(),
                        file.getSize(),
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

    @DeleteMapping("/decks/{deckId}/pmvs/{pmvId}")
    public ApiResponse<Map<String, Object>> deletePmv(
            @PathVariable("deckId") long deckId,
            @PathVariable("pmvId") int pmvId) {
        String clientId = AuthContext.get().clientId.toString();
        submissionService.deletePmv(clientId, deckId, pmvId);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @DeleteMapping("/decks/{deckId}/pmvs/{pmvId}/cards/{shot}")
    public ApiResponse<Map<String, Object>> deleteCard(
            @PathVariable("deckId") long deckId,
            @PathVariable("pmvId") int pmvId,
            @PathVariable("shot") String shot) {
        String clientId = AuthContext.get().clientId.toString();
        submissionService.deleteCard(clientId, deckId, pmvId, shot);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
