package com.lumoxu.cof.service;

import com.lumoxu.cof.engine.Card;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.service.model.CardLibraryDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetaService {

    private final DeckCatalogService deckCatalogService;
    private final Path resourceRoot;

    public MetaService(
            DeckCatalogService deckCatalogService,
            @Value("${cof.resource-root:../cof-resource}") String resourceRoot) {
        this.deckCatalogService = deckCatalogService;
        this.resourceRoot = Path.of(resourceRoot).toAbsolutePath().normalize();
    }

    public List<CardLibraryDto> listLibraries() {
        return deckCatalogService.listFullLibraries();
    }

    public List<Card> expandedCards(GameSettings settings) {
        return deckCatalogService.expandedCardsFromRoom("", settings);
    }

    public List<Card> expandedCardsForGame(String gameId, GameSettings settings) {
        return deckCatalogService.expandedCards(gameId, settings);
    }

    public Map<String, Object> publicLibrariesPayload() {
        return Map.of("libraries", buildPublicSummaries());
    }

    private List<Map<String, Object>> buildPublicSummaries() {
        List<Map<String, Object>> libraries = new java.util.ArrayList<>();
        for (CardLibraryDto library : deckCatalogService.listPublicSummaries()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", library.id);
            item.put("name", library.name);
            item.put("title", library.title);
            item.put("curator", library.curator);
            item.put("description", library.description);
            item.put("version", library.version);
            item.put("link", library.link);
            item.put("backUrl", library.backUrl);
            item.put("cardCount", library.cardCount);
            item.put("pmvCount", library.pmvCount);
            libraries.add(item);
        }
        return libraries;
    }

    public List<Map<String, Object>> pmvIndex() {
        return deckCatalogService.buildPmvIndex();
    }

    public Path getResourceRoot() {
        return resourceRoot;
    }
}
