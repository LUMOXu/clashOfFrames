package com.lumoxu.cof.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        deck.updatedAt = now;
        if (deck.id == null) {
            deckMapper.insert(deck);
        } else {
            deckMapper.updateById(deck);
        }

        long deckId = deck.id;
        deck.backUrl = ResourcePathMigration.backUrl(deckId);
        deckMapper.updateById(deck);

        Path targetDeckDir = resourceRoot.resolve("cards").resolve(String.valueOf(deckId));
        List<ResourcePathMigration.MigratedCard> migrated =
                pathMigration.migrateLegacyCards(libraryDir, targetDeckDir, deckId);
        pathMigration.syncCanonicalDeckDir(libraryDir, targetDeckDir, deckId);

        deckPmvMapper.delete(new QueryWrapper<CofDeckPmv>().eq("deck_id", deckId));
        for (PmvEntry entry : manifestData.pmvs) {
            CofDeckPmv pmv = new CofDeckPmv();
            pmv.deckId = deckId;
            pmv.pmvId = entry.pmvId;
            pmv.name = entry.name;
            pmv.author = entry.author;
            pmv.description = entry.description;
            pmv.link = entry.link;
            deckPmvMapper.insert(pmv);
        }

        cardMapper.delete(new QueryWrapper<CofCard>().eq("deck_id", deckId));
        Map<String, ResourcePathMigration.MigratedCard> migratedByKey = new HashMap<>();
        for (ResourcePathMigration.MigratedCard card : migrated) {
            migratedByKey.put(card.pmvId() + ":" + card.cardId(), card);
        }
        List<CofCard> cards = discoverCards(targetDeckDir, deckId, migratedByKey);
        if (cards.isEmpty()) {
            cards = discoverCards(libraryDir, deckId, migratedByKey);
        }
        for (CofCard card : cards) {
            cardMapper.insert(card);
        }

        deck.cardCount = cards.size();
        deck.pmvCount = (int) cards.stream().map(c -> c.pmvId).distinct().count();
        deckMapper.updateById(deck);
        return 1;
    }

    private List<CofCard> discoverCards(
            Path deckDir,
            long deckId,
            Map<String, ResourcePathMigration.MigratedCard> migratedByKey) throws IOException {
        List<CofCard> cards = new ArrayList<>();
        if (!Files.isDirectory(deckDir)) {
            return cards;
        }
        try (Stream<Path> pmvDirs = Files.list(deckDir)) {
            for (Path pmvDir : pmvDirs.filter(Files::isDirectory).toList()) {
                String dirName = pmvDir.getFileName().toString();
                if ("cards".equals(dirName)) {
                    continue;
                }
                int pmvId;
                try {
                    pmvId = Integer.parseInt(dirName);
                } catch (NumberFormatException ex) {
                    continue;
                }
                try (Stream<Path> cardFiles = Files.list(pmvDir)) {
                    for (Path cardPath : cardFiles.filter(Files::isRegularFile).toList()) {
                        CofCard card = buildCardFromFile(cardPath, deckId, pmvId, migratedByKey);
                        if (card != null) {
                            cards.add(card);
                        }
                    }
                }
            }
        }
        Path legacyDir = deckDir.resolve("cards");
        if (cards.isEmpty() && Files.isDirectory(legacyDir)) {
            try (Stream<Path> legacyFiles = Files.list(legacyDir)) {
                for (Path cardPath : legacyFiles.filter(Files::isRegularFile).toList()) {
                    String fileName = cardPath.getFileName().toString();
                    Matcher matcher = CARD_FILE.matcher(fileName);
                    if (!matcher.matches()) {
                        continue;
                    }
                    int pmvId = Integer.parseInt(matcher.group(1));
                    CofCard card = buildCardFromFile(cardPath, deckId, pmvId, migratedByKey);
                    if (card != null) {
                        cards.add(card);
                    }
                }
            }
        }
        return cards;
    }

    private CofCard buildCardFromFile(
            Path cardPath,
            long deckId,
            int pmvId,
            Map<String, ResourcePathMigration.MigratedCard> migratedByKey) {
        String fileName = cardPath.getFileName().toString();
        Matcher matcher = CARD_FILE.matcher(fileName);
        String shot;
        String ext;
        if (matcher.matches()) {
            if (pmvId != Integer.parseInt(matcher.group(1))) {
                pmvId = Integer.parseInt(matcher.group(1));
            }
            shot = matcher.group(2).toLowerCase(Locale.ROOT);
            ext = matcher.group(3).toLowerCase(Locale.ROOT);
        } else {
            Matcher shotMatcher = SHOT_FILE.matcher(fileName);
            if (!shotMatcher.matches()) {
                return null;
            }
            shot = shotMatcher.group(1).toLowerCase(Locale.ROOT);
            ext = shotMatcher.group(2).toLowerCase(Locale.ROOT);
        }
        if ("jpeg".equalsIgnoreCase(ext)) {
            ext = "jpg";
        }
        String key = pmvId + ":" + shot;
        ResourcePathMigration.MigratedCard migrated = migratedByKey.get(key);
        CofCard card = new CofCard();
        card.deckId = deckId;
        card.pmvId = pmvId;
        card.cardId = shot;
        card.shot = shot;
        card.fileName = migrated != null ? migrated.fileName() : fileName;
        card.imageUrl = migrated != null
                ? migrated.imageUrl()
                : ResourcePathMigration.cardUrl(deckId, pmvId, shot, ext);
        card.cardUid = deckId + "/" + pmvId + "/" + shot;
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
                    int pmvId = entry.path("pmv_id").asInt(entry.path("pmvId").asInt(-1));
                    if (pmvId < 0) {
                        continue;
                    }
                    PmvEntry pmv = new PmvEntry();
                    pmv.pmvId = pmvId;
                    pmv.name = entry.path("name").asText("PMV " + pmvId);
                    pmv.author = entry.path("author").asText(null);
                    pmv.description = entry.path("description").asText(null);
                    pmv.link = entry.path("link").asText(null);
                    data.pmvs.add(pmv);
                    data.pmvNames.put(pmvId, pmv.name);
                }
            }
        } else if (text.trim().startsWith("[")) {
            JsonNode array = objectMapper.readTree(text);
            for (JsonNode entry : array) {
                int pmvId = entry.path("pmv_id").asInt(entry.path("pmvId").asInt(-1));
                if (pmvId < 0) {
                    continue;
                }
                PmvEntry pmv = new PmvEntry();
                pmv.pmvId = pmvId;
                pmv.name = entry.path("name").asText("PMV " + pmvId);
                data.pmvs.add(pmv);
                data.pmvNames.put(pmvId, pmv.name);
            }
        } else {
            for (String line : text.split("\\R")) {
                int comma = line.indexOf(',');
                if (comma > 0) {
                    int pmvId = Integer.parseInt(line.substring(0, comma).trim());
                    String name = line.substring(comma + 1).trim();
                    PmvEntry pmv = new PmvEntry();
                    pmv.pmvId = pmvId;
                    pmv.name = name;
                    data.pmvs.add(pmv);
                    data.pmvNames.put(pmvId, name);
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
        int pmvId;
        String name;
        String author;
        String description;
        String link;
    }
}
