package com.lumoxu.cof.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.common.catalog.ReviewStatus;
import com.lumoxu.cof.domain.entity.CofCard;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.entity.CofPmv;
import com.lumoxu.cof.domain.mapper.CofCardMapper;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.domain.mapper.CofPmvMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Imports built-in card libraries from {@code cof-resource/cards} into the rebuilt catalog schema.
 * Deck ids 1 and 2 are reserved for 基础包 / 拓展包.
 */
@Service
public class DeckCatalogImportService {

    private static final Pattern CARD_FILE = Pattern.compile("^(\\d+)([a-z])\\.(png|jpe?g)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOT_FILE = Pattern.compile("^([a-z])\\.(png|jpe?g)$", Pattern.CASE_INSENSITIVE);

    private static final List<DefaultDeck> DEFAULT_DECKS = List.of(
            new DefaultDeck(1L, "基础包@ThePMVPanel'25", "基础包"),
            new DefaultDeck(2L, "拓展包@ThePMVPanel'25", "拓展包"));

    private final ObjectMapper objectMapper;
    private final Path resourceRoot;
    private final CofDeckMapper deckMapper;
    private final CofPmvMapper pmvMapper;
    private final CofCardMapper cardMapper;
    private final ResourcePathMigration pathMigration;
    private final DeckCatalogService deckCatalogService;

    public DeckCatalogImportService(
            ObjectMapper objectMapper,
            @Value("${cof.resource-root:../cof-resource}") String resourceRoot,
            CofDeckMapper deckMapper,
            CofPmvMapper pmvMapper,
            CofCardMapper cardMapper,
            ResourcePathMigration pathMigration,
            DeckCatalogService deckCatalogService) {
        this.objectMapper = objectMapper;
        this.resourceRoot = Path.of(resourceRoot).toAbsolutePath().normalize();
        this.deckMapper = deckMapper;
        this.pmvMapper = pmvMapper;
        this.cardMapper = cardMapper;
        this.pathMigration = pathMigration;
        this.deckCatalogService = deckCatalogService;
    }

    /**
     * Ensures default playable decks 1 (基础包) and 2 (拓展包) exist. Idempotent when already imported.
     */
    @Transactional
    public int bootstrapDefaultDecks() throws IOException {
        int count = 0;
        for (DefaultDeck spec : DEFAULT_DECKS) {
            if (isDefaultDeckReady(spec)) {
                continue;
            }
            Path libraryDir = resourceRoot.resolve("cards").resolve(spec.folderName());
            if (!Files.isDirectory(libraryDir)) {
                continue;
            }
            if (importDeck(spec.deckId(), libraryDir, spec.displayName()) > 0) {
                count++;
            }
        }
        if (count > 0) {
            deckCatalogService.bustCaches();
        }
        return count;
    }

    @Transactional
    public int importAllDecks() throws IOException {
        Path cardsRoot = resourceRoot.resolve("cards");
        if (!Files.isDirectory(cardsRoot)) {
            return 0;
        }
        int count = bootstrapDefaultDecks();
        try (Stream<Path> dirs = Files.list(cardsRoot)) {
            List<Path> deckDirs = dirs.filter(Files::isDirectory)
                    .filter(p -> !"backs".equals(p.getFileName().toString()))
                    .filter(p -> !isNumericFolder(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path deckDir : deckDirs) {
                String folder = deckDir.getFileName().toString();
                boolean reserved = DEFAULT_DECKS.stream().anyMatch(d -> d.folderName().equals(folder));
                if (reserved) {
                    continue;
                }
                if (importDeck(null, deckDir, null) > 0) {
                    count++;
                }
            }
        }
        deckCatalogService.bustCaches();
        return count;
    }

    @Transactional
    public int importDeck(Path libraryDir) throws IOException {
        return importDeck(null, libraryDir, null);
    }

    private boolean isDefaultDeckReady(DefaultDeck spec) {
        CofDeck deck = deckMapper.selectById(spec.deckId());
        if (deck == null || deck.deletedAt != null) {
            return false;
        }
        if (!spec.displayName().equals(deck.name)) {
            return false;
        }
        if (!ReviewStatus.isApproved(deck.reviewStatus) || !Boolean.TRUE.equals(deck.enabled)) {
            return false;
        }
        long cards = cardMapper.countAliveByDeckId(spec.deckId());
        return cards >= 40;
    }

    private int importDeck(Long fixedDeckId, Path libraryDir, String displayNameOverride) throws IOException {
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
        String deckName = displayNameOverride != null ? displayNameOverride : manifestData.name;
        if (deckName == null || deckName.isBlank()) {
            deckName = folderName;
        }

        Instant now = CatalogRevisionHelper.now();
        CofDeck deck = fixedDeckId != null ? deckMapper.selectById(fixedDeckId) : null;
        boolean isNew = deck == null;
        if (isNew) {
            deck = new CofDeck();
            deck.createdAt = now;
            if (fixedDeckId != null) {
                deck.id = fixedDeckId;
            }
        }
        deck.name = deckName;
        deck.description = manifestData.description;
        deck.enabled = true;
        deck.reviewStatus = ReviewStatus.APPROVED;
        deck.pendingReviewStatus = null;
        deck.pendingName = null;
        deck.pendingDescription = null;
        deck.pendingBackUrl = null;
        deck.submitterClientId = null;
        deck.updatedAt = now;
        deck.deletedAt = null;
        if (isNew) {
            deck.backUrl = ResourcePathMigration.backUrl(deck.id != null ? deck.id : 0);
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

        for (PmvEntry entry : manifestData.pmvs) {
            upsertApprovedPmv(entry, now);
        }

        cardMapper.delete(new QueryWrapper<CofCard>().eq("deck_id", deckId));
        List<CofCard> cards = buildCardsFromImports(deckId, importedByKey, manifestData);
        if (cards.isEmpty()) {
            cards = discoverCardsFromLegacyFiles(libraryDir, deckId, importedByKey, manifestData);
        }
        for (CofCard card : cards) {
            cardMapper.insert(card);
        }
        return 1;
    }

    private void upsertApprovedPmv(PmvEntry entry, Instant now) {
        long pmvId = entry.matchId;
        CofPmv pmv = pmvMapper.selectById(pmvId);
        boolean isNew = pmv == null;
        if (isNew) {
            pmv = new CofPmv();
            pmv.id = pmvId;
            pmv.createdAt = now;
        }
        pmv.name = entry.name;
        pmv.author = entry.author;
        pmv.description = entry.description;
        pmv.link = entry.link;
        pmv.reviewStatus = ReviewStatus.APPROVED;
        pmv.pendingReviewStatus = null;
        pmv.submitterClientId = null;
        pmv.updatedAt = now;
        pmv.deletedAt = null;
        if (isNew) {
            pmvMapper.insert(pmv);
        } else {
            pmvMapper.updateById(pmv);
        }
    }

    private List<CofCard> buildCardsFromImports(
            long deckId,
            Map<String, ResourcePathMigration.LegacyImportedCard> importedByKey,
            ManifestData manifestData) {
        List<CofCard> cards = new ArrayList<>();
        Instant now = CatalogRevisionHelper.now();
        for (ResourcePathMigration.LegacyImportedCard imported : importedByKey.values()) {
            if (!manifestData.knownMatchIds.contains(imported.matchId())) {
                continue;
            }
            cards.add(toCard(deckId, imported, manifestData, now));
        }
        return cards;
    }

    private List<CofCard> discoverCardsFromLegacyFiles(
            Path libraryDir,
            long deckId,
            Map<String, ResourcePathMigration.LegacyImportedCard> importedByKey,
            ManifestData manifestData) throws IOException {
        List<CofCard> cards = new ArrayList<>();
        Instant now = CatalogRevisionHelper.now();
        for (Map.Entry<String, ResourcePathMigration.LegacyImportedCard> entry : importedByKey.entrySet()) {
            ResourcePathMigration.LegacyImportedCard imported = entry.getValue();
            if (manifestData.knownMatchIds.contains(imported.matchId())) {
                cards.add(toCard(deckId, imported, manifestData, now));
            }
        }
        if (!cards.isEmpty()) {
            return cards;
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
                if (!manifestData.knownMatchIds.contains(matchId)) {
                    continue;
                }
                try (Stream<Path> cardFiles = Files.list(pmvDir)) {
                    for (Path cardPath : cardFiles.filter(Files::isRegularFile).toList()) {
                        CofCard card = buildCardFromLegacyPath(cardPath, deckId, importedByKey, manifestData, matchId, now);
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
            ManifestData manifestData,
            int defaultMatchId,
            Instant now) throws IOException {
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
        if (!manifestData.knownMatchIds.contains(matchId)) {
            return null;
        }
        String key = matchId + ":" + shot;
        ResourcePathMigration.LegacyImportedCard imported = importedByKey.get(key);
        if (imported == null) {
            ResourcePathMigration.StoredCardImage stored = pathMigration.storeCardFile(cardPath);
            imported = new ResourcePathMigration.LegacyImportedCard(
                    deckId, matchId, shot, stored.fileName(), stored.imageUrl());
        }
        return toCard(deckId, imported, manifestData, now);
    }

    private static CofCard toCard(
            long deckId,
            ResourcePathMigration.LegacyImportedCard imported,
            ManifestData manifestData,
            Instant now) {
        CofCard card = new CofCard();
        card.deckId = deckId;
        card.pmvId = (long) imported.matchId();
        card.name = manifestData.pmvNames.getOrDefault(imported.matchId(), "PMV " + imported.matchId())
                + " " + imported.shot().toUpperCase(Locale.ROOT);
        card.imageUrl = imported.imageUrl();
        card.reviewStatus = ReviewStatus.APPROVED;
        card.submitterClientId = null;
        card.createdAt = now;
        card.updatedAt = now;
        return card;
    }

    private ManifestData parseManifest(Path manifest, String folderName) throws IOException {
        ManifestData data = new ManifestData();
        String text = Files.readString(manifest);
        if (manifest.getFileName().toString().endsWith(".json")) {
            JsonNode root = objectMapper.readTree(text);
            data.name = root.path("name").asText(folderName);
            data.description = root.path("description").asText(null);
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
                    data.knownMatchIds.add(matchId);
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

    private record DefaultDeck(long deckId, String folderName, String displayName) {}

    private static final class ManifestData {
        String name;
        String description;
        final List<PmvEntry> pmvs = new ArrayList<>();
        final Map<Integer, String> pmvNames = new HashMap<>();
        final java.util.Set<Integer> knownMatchIds = new java.util.HashSet<>();
    }

    private static final class PmvEntry {
        int matchId;
        String name;
        String author;
        String description;
        String link;
    }
}
