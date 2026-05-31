package com.lumoxu.cof.service;

import com.lumoxu.cof.service.model.CardLibraryDto;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CardViewerService {

    private final DeckCatalogService deckCatalogService;

    public CardViewerService(DeckCatalogService deckCatalogService) {
        this.deckCatalogService = deckCatalogService;
    }

    public Map<String, Object> buildViewerPayload(List<String> libraryIds) {
        Set<String> selected = libraryIds == null || libraryIds.isEmpty()
                ? null
                : Set.copyOf(libraryIds);
        List<CardLibraryDto> libraries = deckCatalogService.listFullLibraries().stream()
                .filter(lib -> selected == null || selected.contains(lib.id))
                .collect(Collectors.toList());
        List<String> assets = new ArrayList<>();
        assets.add("/assets/bell.png");
        assets.add("/assets/logo.png");
        List<Map<String, Object>> libraryPayload = new ArrayList<>();
        for (CardLibraryDto library : libraries) {
            if (library.backUrl != null) {
                assets.add(library.backUrl);
            }
            Map<String, Map<String, Object>> pmvGroups = new LinkedHashMap<>();
            if (library.cards != null) {
                for (var card : library.cards) {
                    if (card.imageUrl != null) {
                        assets.add(card.imageUrl);
                    }
                    String pmvKey = String.valueOf(card.pmvId);
                    pmvGroups.computeIfAbsent(pmvKey, k -> {
                        Map<String, Object> group = new HashMap<>();
                        group.put("pmvId", card.pmvId);
                        group.put("pmvName", card.pmvName);
                        group.put("cards", new ArrayList<Map<String, Object>>());
                        return group;
                    });
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> cards = (List<Map<String, Object>>) pmvGroups.get(pmvKey).get("cards");
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", card.id);
                    entry.put("imageUrl", card.imageUrl);
                    entry.put("shot", card.shot);
                    cards.add(entry);
                }
            }
            Map<String, Object> lib = new HashMap<>();
            lib.put("id", library.id);
            lib.put("name", library.name);
            lib.put("backUrl", library.backUrl);
            lib.put("cardCount", library.cardCount);
            lib.put("pmvs", new ArrayList<>(pmvGroups.values()));
            libraryPayload.add(lib);
        }
        String fingerprint = fingerprint(libraries);
        Map<String, Object> result = new HashMap<>();
        result.put("key", "card-viewer-" + fingerprint);
        result.put("assets", assets.stream().distinct().toList());
        result.put("libraries", libraryPayload);
        return result;
    }

    private static String fingerprint(List<CardLibraryDto> libraries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (CardLibraryDto library : libraries) {
                digest.update(library.id.getBytes(StandardCharsets.UTF_8));
                if (library.cards != null) {
                    for (var card : library.cards) {
                        if (card.imageUrl != null) {
                            digest.update(card.imageUrl.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, 20);
        } catch (NoSuchAlgorithmException ex) {
            return "default";
        }
    }
}
