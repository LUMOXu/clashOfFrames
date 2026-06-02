package com.lumoxu.cof.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.common.catalog.ReviewStatus;
import com.lumoxu.cof.domain.entity.CofCard;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.entity.CofDeckPmv;
import com.lumoxu.cof.domain.mapper.CofCardMapper;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.domain.mapper.CofDeckPmvMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class DeckCatalogImportService {

    private static final Pattern CARD_FILE = Pattern.compile("^(\\d+)([a-z])\\.(png|jpe?g)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOT_FILE = Pattern.compile("^([a-z])\\.(png|jpe?g)$", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;
    private final Path resourceRoot;
    private final CofDeckMapper deckMapper;
    private final CofDeckPmvMapper deckPmvMapper;
    private final CofCardMapper cardMapper;
    private final ResourcePathMigration pathMigration;
    private final DeckCatalogService deckCatalogService;

    public DeckCatalogImportService(
            ObjectMapper objectMapper,
            @Value("${cof.resource-root:../cof-resource}") String resourceRoot,
            CofDeckMapper deckMapper,
            CofDeckPmvMapper deckPmvMapper,
            CofCardMapper cardMapper,
            ResourcePathMigration pathMigration,
            DeckCatalogService deckCatalogService) {
        this.objectMapper = objectMapper;
        this.resourceRoot = Path.of(resourceRoot).toAbsolutePath().normalize();
        this.deckMapper = deckMapper;
        this.deckPmvMapper = deckPmvMapper;
        this.cardMapper = cardMapper;
        this.pathMigration = pathMigration;
        this.deckCatalogService = deckCatalogService;
    }

    @Transactional
    public int importAllDecks() throws IOException {
        Path cardsRoot = resourceRoot.resolve("cards");
        if (!Files.isDirectory(cardsRoot)) {
            return 0;
        }
        int count = 0;
        try (Stream<Path> dirs = Files.list(cardsRoot)) {
            List<Path> deckDirs = dirs.filter(Files::isDirectory)
                    .filter(p -> !"backs".equals(p.getFileName().toString()))
                    .filter(p -> !isNumericFolder(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path deckDir : deckDirs) {
                if (importDeck(deckDir) > 0) {
                    count++;
                }
            }
        }
        deckCatalogService.bustCaches();
        return count;
    }

    @Transactional
    public int importDeck(Path libraryDir) throws IOException {
        String folderName = libraryDir.getFileName().toString();
        if (isNumericFolder(folderName)) {
            return 0;
        }
        Path manifestJson = libraryDir.resolve("manifest.json");
        Path manifestTxt = libraryDir.resolve("manifest.txt");
        Path back = libraryDir.resolve("back.png");
        Path manifest = Files.exists(manifestJson) ? manifestJson : manifestTxt;
        if (!Files.exists(manifest) || !Files.exists(back)) {
            return 0;
        }
        ManifestData manifestData = parseManifest(manifest, folderName);

        long now = System.currentTimeMillis();
        CofDeck deck = deckMapper.findByFolderName(folderName);
        if (deck == null) {
            deck = new CofDeck();
        }
        deck.folderName = folderName;
        deck.name = manifestData.name != null ? manifestData.name : folderName;
        deck.curator = manifestData.curator;
        deck.description = manifestData.description;
        deck.version = manifestData.version;
        deck.link = manifestData.link;
        deck.enabled = true;
        deck.reviewStatus = ReviewStatus.APPROVED;
        deck.submitterClientId = null;
        deck.updatedAt = now;
        if (deck.id == null) {
            deck.backUrl = ResourcePathMigration.backUrl(0);
            deckMapper.insert(deck);
        } else {
            deckMapper.updateById(deck);
        }

        long deckId = deck.id;
        pathMigration.copyDeckBack(back, deckId);
        deck.backUrl = ResourcePathMigration.backUrl(deckId);
        deckMapper.updateById(deck);

        List<ResourcePathMigration.LegacyImportedCard> importedImages =
                pathMigration.importLegacyDeckImages(libraryDir, deckId);
        Map<String, ResourcePathMigration.LegacyImportedCard> importedByKey = new HashMap<>();
        for (ResourcePathMigration.LegacyImportedCard card : importedImages) {
            importedByKey.put(card.matchId() + ":" + card.shot(), card);
        }

        deckPmvMapper.delete(new QueryWrapper<CofDeckPmv>().eq("deck_id", deckId));
        Map<Integer, Long> pmvPkByMatchId = new HashMap<>();
        for (PmvEntry entry : manifestData.pmvs) {
            CofDeckPmv pmv = new CofDeckPmv();
            pmv.deckId = deckId;
            pmv.matchId = entry.matchId;
            pmv.name = entry.name;
            pmv.author = entry.author;
            pmv.description = entry.description;
            pmv.link = entry.link;
            pmv.reviewStatus = ReviewStatus.APPROVED;
            pmv.submitterClientId = null;
            deckPmvMapper.insert(pmv);
            pmvPkByMatchId.put(entry.matchId, pmv.pmvId);
        }

        cardMapper.delete(new QueryWrapper<CofCard>().eq("deck_id", deckId));
        List<CofCard> cards = buildCardsFromImports(deckId, importedByKey, pmvPkByMatchId);
        if (cards.isEmpty()) {
            cards = discoverCardsFromLegacyFiles(libraryDir, deckId, importedByKey, pmvPkByMatchId);
        }
        for (CofCard card : cards) {
            cardMapper.insert(card);
        }

        deck.cardCount = cards.size();
        deck.pmvCount = pmvPkByMatchId.size();
        deckMapper.updateById(deck);
        return 1;
    }

    private List<CofCard> buildCardsFromImports(
            long deckId,
            Map<String, ResourcePathMigration.LegacyImportedCard> importedByKey,
            Map<Integer, Long> pmvPkByMatchId) {
        List<CofCard> cards = new ArrayList<>();
        for (ResourcePathMigration.LegacyImportedCard imported : importedByKey.values()) {
            Long pmvPk = pmvPkByMatchId.get(imported.matchId());
            if (pmvPk == null) {
                continue;
            }
            CofCard card = new CofCard();
            card.deckId = deckId;
            card.pmvId = pmvPk;
            card.shot = imported.shot();
            card.fileName = imported.fileName();
            card.imageUrl = imported.imageUrl();
            card.cardUid = deckId + "/" + imported.matchId() + "/" + imported.shot();
            card.reviewStatus = ReviewStatus.APPROVED;
            card.submitterClientId = null;
            cards.add(card);
        }
        return cards;
    }

    private List<CofCard> discoverCardsFromLegacyFiles(
            Path libraryDir,
            long deckId,
            Map<String, ResourcePathMigration.LegacyImportedCard> importedByKey,
            Map<Integer, Long> pmvPkByMatchId) throws IOException {
        List<CofCard> cards = new ArrayList<>();
        for (Map.Entry<String, ResourcePathMigration.LegacyImportedCard> entry : importedByKey.entrySet()) {
            cards.add(toCard(deckId, entry.getValue(), pmvPkByMatchId));
        }
        if (!cards.isEmpty()) {
            return cards;
        }
        Path legacyCardsDir = libraryDir.resolve("cards");
        if (Files.isDirectory(legacyCardsDir)) {
            try (Stream<Path> legacyFiles = Files.list(legacyCardsDir)) {
                for (Path cardPath : legacyFiles.filter(Files::isRegularFile).toList()) {
                    CofCard card = buildCardFromLegacyPath(cardPath, deckId, importedByKey, pmvPkByMatchId);
                    if (card != null) {
                        cards.add(card);
                    }
                }
            }
        }
        try (Stream<Path> entries = Files.list(libraryDir)) {
            for (Path pmvDir : entries.filter(Files::isDirectory).toList()) {
                String dirName = pmvDir.getFileName().toString();
                if ("cards".equals(dirName)) {
                    continue;
                }
                int matchId;
                try {
                    matchId = Integer.parseInt(dirName);
                } catch (NumberFormatException ex) {
                    continue;
                }
                try (Stream<Path> cardFiles = Files.list(pmvDir)) {
                    for (Path cardPath : cardFiles.filter(Files::isRegularFile).toList()) {
                        CofCard card = buildCardFromLegacyPath(cardPath, deckId, importedByKey, pmvPkByMatchId, matchId);
                        if (card != null) {
                            cards.add(card);
                        }
                    }
                }
            }
        }
        return cards;
    }

    private CofCard buildCardFromLegacyPath(
            Path cardPath,
            long deckId,
            Map<String, ResourcePathMigration.LegacyImportedCard> importedByKey,
            Map<Integer, Long> pmvPkByMatchId) throws IOException {
        return buildCardFromLegacyPath(cardPath, deckId, importedByKey, pmvPkByMatchId, -1);
    }

    private CofCard buildCardFromLegacyPath(
            Path cardPath,
            long deckId,
            Map<String, ResourcePathMigration.LegacyImportedCard> importedByKey,
            Map<Integer, Long> pmvPkByMatchId,
            int defaultMatchId) throws IOException {
        String fileName = cardPath.getFileName().toString();
        Matcher matcher = CARD_FILE.matcher(fileName);
        String shot;
        int matchId = defaultMatchId;
        if (matcher.matches()) {
            matchId = Integer.parseInt(matcher.group(1));
            shot = matcher.group(2).toLowerCase(Locale.ROOT);
        } else {
            Matcher shotMatcher = SHOT_FILE.matcher(fileName);
            if (!shotMatcher.matches() || matchId < 0) {
                return null;
            }
            shot = shotMatcher.group(1).toLowerCase(Locale.ROOT);
        }
        String key = matchId + ":" + shot;
        ResourcePathMigration.LegacyImportedCard imported = importedByKey.get(key);
        if (imported == null) {
            ResourcePathMigration.StoredCardImage stored = pathMigration.storeCardFile(cardPath);
            imported = new ResourcePathMigration.LegacyImportedCard(
                    deckId, matchId, shot, stored.fileName(), stored.imageUrl());
            importedByKey.put(key, imported);
        }
        return toCard(deckId, imported, pmvPkByMatchId);
    }

    private static CofCard toCard(
            long deckId,
            ResourcePathMigration.LegacyImportedCard imported,
            Map<Integer, Long> pmvPkByMatchId) {
        Long pmvPk = pmvPkByMatchId.get(imported.matchId());
        if (pmvPk == null) {
            return null;
        }
        CofCard card = new CofCard();
        card.deckId = deckId;
        card.pmvId = pmvPk;
        card.shot = imported.shot();
        card.fileName = imported.fileName();
        card.imageUrl = imported.imageUrl();
        card.cardUid = deckId + "/" + imported.matchId() + "/" + imported.shot();
        card.reviewStatus = ReviewStatus.APPROVED;
        card.submitterClientId = null;
        return card;
    }

    private ManifestData parseManifest(Path manifest, String folderName) throws IOException {
        ManifestData data = new ManifestData();
        data.name = folderName;
        String text = Files.readString(manifest);
        if (text.trim().startsWith("{")) {
            JsonNode root = objectMapper.readTree(text);
            data.name = root.path("name").asText(folderName);
            data.curator = root.path("curator").asText(null);
            data.description = root.path("description").asText(null);
            data.version = root.path("version").asText(null);
            data.link = root.path("link").asText(null);
            JsonNode entries = root.has("pmvs") ? root.get("pmvs") : root.get("entries");
            if (entries != null && entries.isArray()) {
                for (JsonNode entry : entries) {
                    int matchId = entry.path("pmv_id").asInt(entry.path("pmvId").asInt(-1));
                    if (matchId < 0) {
                        continue;
                    }
                    PmvEntry pmv = new PmvEntry();
                    pmv.matchId = matchId;
                    pmv.name = entry.path("name").asText("PMV " + matchId);
                    pmv.author = entry.path("author").asText(null);
                    pmv.description = entry.path("description").asText(null);
                    pmv.link = entry.path("link").asText(null);
                    data.pmvs.add(pmv);
                    data.pmvNames.put(matchId, pmv.name);
                }
            }
        } else if (text.trim().startsWith("[")) {
            JsonNode array = objectMapper.readTree(text);
            for (JsonNode entry : array) {
                int matchId = entry.path("pmv_id").asInt(entry.path("pmvId").asInt(-1));
                if (matchId < 0) {
                    continue;
                }
                PmvEntry pmv = new PmvEntry();
                pmv.matchId = matchId;
                pmv.name = entry.path("name").asText("PMV " + matchId);
                data.pmvs.add(pmv);
                data.pmvNames.put(matchId, pmv.name);
            }
        } else {
            for (String line : text.split("\\R")) {
                int comma = line.indexOf(',');
                if (comma > 0) {
                    int matchId = Integer.parseInt(line.substring(0, comma).trim());
                    String name = line.substring(comma + 1).trim();
                    PmvEntry pmv = new PmvEntry();
                    pmv.matchId = matchId;
                    pmv.name = name;
                    data.pmvs.add(pmv);
                    data.pmvNames.put(matchId, name);
                }
            }
        }
        return data;
    }

    private static boolean isNumericFolder(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static final class ManifestData {
        String name;
        String curator;
        String description;
        String version;
        String link;
        final List<PmvEntry> pmvs = new ArrayList<>();
        final Map<Integer, String> pmvNames = new HashMap<>();
    }

    private static final class PmvEntry {
        int matchId;
        String name;
        String author;
        String description;
        String link;
    }
}
