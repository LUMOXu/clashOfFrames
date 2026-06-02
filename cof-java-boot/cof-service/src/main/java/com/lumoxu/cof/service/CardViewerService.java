package com.lumoxu.cof.service;

import com.lumoxu.cof.domain.entity.CofDeckPmv;
import com.lumoxu.cof.domain.mapper.CofDeckPmvMapper;
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
    private final CofDeckPmvMapper deckPmvMapper;

    public CardViewerService(DeckCatalogService deckCatalogService, CofDeckPmvMapper deckPmvMapper) {
        this.deckCatalogService = deckCatalogService;
        this.deckPmvMapper = deckPmvMapper;
    }

    public Map<String, Object> buildViewerPayload(List<String> libraryIds) {
        return buildViewerPayload(libraryIds, null);
    }

    public Map<String, Object> buildViewerPayload(List<String> libraryIds, String viewerClientId) {
        Set<String> selected = libraryIds == null || libraryIds.isEmpty()
                ? null
                : Set.copyOf(libraryIds);
        List<CardLibraryDto> libraries = deckCatalogService.listFullLibrariesForViewer(viewerClientId).stream()
                .filter(lib -> selected == null || deckCatalogService.isLibrarySelected(lib, List.copyOf(selected)))
                .collect(Collectors.toList());
        List<String> assets = new ArrayList<>();
        assets.add("/assets/bell.png");
        assets.add("/assets/logo.png");
        List<Map<String, Object>> libraryPayload = new ArrayList<>();
        for (CardLibraryDto library : libraries) {
            if (library.backUrl != null) {
                assets.add(library.backUrl);
            }
            Long deckId = deckCatalogService.resolveDeckId(library.id);
            Map<Integer, CofDeckPmv> pmvMeta = new HashMap<>();
            if (deckId != null) {
                for (CofDeckPmv pmv : deckPmvMapper.listByDeckId(deckId)) {
                    pmvMeta.put(pmv.matchId, pmv);
                }
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
                        CofDeckPmv meta = pmvMeta.get(card.pmvId);
                        group.put("name", meta != null && meta.name != null ? meta.name : card.pmvName);
                        group.put("pmvName", card.pmvName);
                        if (meta != null) {
                            group.put("author", meta.author);
                            group.put("description", meta.description);
                            group.put("link", meta.link);
                        }
                        group.put("shots", new ArrayList<Map<String, Object>>());
                        return group;
                    });
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> shots = (List<Map<String, Object>>) pmvGroups.get(pmvKey).get("shots");
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", card.id);
                    entry.put("imageUrl", card.imageUrl);
                    entry.put("shot", card.shot);
                    shots.add(entry);
                }
            }
            Map<String, Object> lib = new HashMap<>();
            lib.put("id", library.id);
            lib.put("name", library.name);
            lib.put("folderName", library.folderName);
            lib.put("curator", library.curator);
            lib.put("description", library.description);
            lib.put("version", library.version);
            lib.put("link", library.link);
            lib.put("backUrl", library.backUrl);
            lib.put("cardCount", library.cardCount);
            lib.put("pmvCount", library.pmvCount);
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
