package com.lumoxu.cof.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DeckSubmissionService {

    private static final Pattern SHOT_PATTERN = Pattern.compile("^[a-z]$");
    private static final Pattern FOLDER_PATTERN = Pattern.compile("^[a-zA-Z0-9_@'.\\-]{2,120}$");

    private final Path resourceRoot;
    private final CofDeckMapper deckMapper;
    private final CofDeckPmvMapper deckPmvMapper;
    private final CofCardMapper cardMapper;
    private final SubmissionImageProcessor imageProcessor;
    private final DeckCatalogService deckCatalogService;

    public DeckSubmissionService(
            @Value("${cof.resource-root:../cof-resource}") String resourceRoot,
            CofDeckMapper deckMapper,
            CofDeckPmvMapper deckPmvMapper,
            CofCardMapper cardMapper,
            SubmissionImageProcessor imageProcessor,
            DeckCatalogService deckCatalogService) {
        this.resourceRoot = Path.of(resourceRoot).toAbsolutePath().normalize();
        this.deckMapper = deckMapper;
        this.deckPmvMapper = deckPmvMapper;
        this.cardMapper = cardMapper;
        this.imageProcessor = imageProcessor;
        this.deckCatalogService = deckCatalogService;
    }

    public List<Map<String, Object>> listEditableDecks(String clientId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CofDeck deck : deckMapper.listBySubmitter(clientId)) {
            if (ReviewStatus.REJECTED.equalsIgnoreCase(deck.reviewStatus)) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("id", deck.id);
            row.put("name", deck.name);
            row.put("curator", deck.curator);
            row.put("reviewStatus", deck.reviewStatus);
            row.put("owned", true);
            rows.add(row);
        }
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Map<String, Object> row : rows) {
            seen.add(((Number) row.get("id")).longValue());
        }
        for (CofDeck deck : deckMapper.listEnabledDecks()) {
            if (seen.contains(deck.id)) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("id", deck.id);
            row.put("name", deck.name);
            row.put("curator", deck.curator);
            row.put("reviewStatus", deck.reviewStatus);
            row.put("owned", clientId.equals(deck.submitterClientId));
            rows.add(row);
        }
        return rows;
    }

    public List<Map<String, Object>> listMine(String clientId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CofDeck deck : deckMapper.listBySubmitter(clientId)) {
            rows.add(deckPayload(deck, clientId));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> createDeck(String clientId, Map<String, Object> body) {
        String name = requiredString(body, "name", "牌组名称");
        String folderSlug = optionalString(body, "folderSlug");
        String folderName = buildFolderName(clientId, folderSlug, name);
        if (deckMapper.findByFolderName(folderName) != null) {
            throw new CofException(ErrorCode.CONFLICT, "牌组标识已存在，请更换名称。");
        }
        long now = System.currentTimeMillis();
        CofDeck deck = new CofDeck();
        deck.folderName = folderName;
        deck.name = name;
        deck.curator = optionalString(body, "curator");
        deck.description = optionalString(body, "description");
        deck.version = optionalString(body, "version");
        deck.link = optionalString(body, "link");
        deck.enabled = false;
        deck.reviewStatus = ReviewStatus.PENDING;
        deck.submitterClientId = clientId;
        deck.cardCount = 0;
        deck.pmvCount = 0;
        deck.updatedAt = now;
        deck.backUrl = ResourcePathMigration.backUrl(0);
        deckMapper.insert(deck);
        deck.backUrl = ResourcePathMigration.backUrl(deck.id);
        deckMapper.updateById(deck);
        try {
            Files.createDirectories(deckDir(deck.id));
        } catch (IOException ex) {
            throw new CofException(ErrorCode.INTERNAL, "创建牌组目录失败。");
        }
        deckCatalogService.bustCaches();
        return deckPayload(deck, clientId);
    }

    @Transactional
    public Map<String, Object> uploadDeckBack(
            String clientId,
            long deckId,
            InputStream file,
            long fileSize,
            Integer cropX,
            Integer cropY,
            Integer cropW,
            Integer cropH) throws IOException {
        CofDeck deck = requireOwnedDeck(clientId, deckId);
        if (fileSize > 15 * 1024 * 1024) {
            throw new CofException(ErrorCode.BAD_REQUEST, "图片过大（最大 15MB）。");
        }
        byte[] jpeg = imageProcessor.processDeckBack(file, cropX, cropY, cropW, cropH);
        Path backPath = deckDir(deckId).resolve("back.jpg");
        imageProcessor.writeJpegToPath(jpeg, backPath);
        deck.backUrl = ResourcePathMigration.backUrl(deckId);
        deck.updatedAt = System.currentTimeMillis();
        deckMapper.updateById(deck);
        deckCatalogService.bustCaches();
        return deckPayload(deck, clientId);
    }

    @Transactional
    public Map<String, Object> addPmv(String clientId, long deckId, Map<String, Object> body) {
        CofDeck deck = requireOwnedDeck(clientId, deckId);
        int pmvId = requiredInt(body, "pmvId", "PMV ID");
        String pmvName = requiredString(body, "name", "PMV 名称");
        CofDeckPmv existing = deckPmvMapper.selectOne(
                new QueryWrapper<CofDeckPmv>().eq("deck_id", deckId).eq("pmv_id", pmvId).last("LIMIT 1"));
        if (existing != null) {
            throw new CofException(ErrorCode.CONFLICT, "该 PMV ID 已存在于本牌组。");
        }
        CofDeckPmv pmv = new CofDeckPmv();
        pmv.deckId = deckId;
        pmv.pmvId = pmvId;
        pmv.name = pmvName;
        pmv.author = optionalString(body, "author");
        pmv.description = optionalString(body, "description");
        pmv.link = optionalString(body, "link");
        pmv.submitterClientId = clientId;
        pmv.reviewStatus = ReviewStatus.PENDING;
        deckPmvMapper.insert(pmv);
        recomputeDeckCounts(deck);
        deckCatalogService.bustCaches();
        return pmvPayload(pmv);
    }

    @Transactional
    public Map<String, Object> addCard(
            String clientId,
            long deckId,
            int pmvId,
            String shot,
            InputStream file,
            long fileSize,
            Integer cropX,
            Integer cropY,
            Integer cropW,
            Integer cropH) throws IOException {
        CofDeck deck = requireOwnedDeck(clientId, deckId);
        String normalizedShot = normalizeShot(shot);
        requirePmv(deckId, pmvId, clientId);
        CofCard existing = cardMapper.selectOne(
                new QueryWrapper<CofCard>()
                        .eq("deck_id", deckId)
                        .eq("pmv_id", pmvId)
                        .eq("card_id", normalizedShot)
                        .last("LIMIT 1"));
        if (existing != null) {
            throw new CofException(ErrorCode.CONFLICT, "该镜头已存在。");
        }
        if (fileSize > 15 * 1024 * 1024) {
            throw new CofException(ErrorCode.BAD_REQUEST, "图片过大（最大 15MB）。");
        }
        byte[] jpeg = imageProcessor.processCardFrame(file, cropX, cropY, cropW, cropH);
        Path cardPath = deckDir(deckId).resolve(String.valueOf(pmvId)).resolve(normalizedShot + ".jpg");
        imageProcessor.writeJpegToPath(jpeg, cardPath);
        String imageUrl = ResourcePathMigration.cardUrl(deckId, pmvId, normalizedShot, "jpg");
        CofCard card = new CofCard();
        card.deckId = deckId;
        card.pmvId = pmvId;
        card.cardId = normalizedShot;
        card.shot = normalizedShot;
        card.fileName = normalizedShot + ".jpg";
        card.imageUrl = imageUrl;
        card.cardUid = deckId + "/" + pmvId + "/" + normalizedShot;
        card.submitterClientId = clientId;
        card.reviewStatus = ReviewStatus.PENDING;
        cardMapper.insert(card);
        recomputeDeckCounts(deck);
        deckCatalogService.bustCaches();
        return cardPayload(card);
    }

    private CofDeck requireOwnedDeck(String clientId, long deckId) {
        CofDeck deck = deckMapper.selectById(deckId);
        if (deck == null) {
            throw new CofException(ErrorCode.NOT_FOUND, "牌组不存在。");
        }
        if (!clientId.equals(deck.submitterClientId)) {
            throw new CofException(ErrorCode.FORBIDDEN, "只能编辑自己提交的牌组。");
        }
        if (ReviewStatus.REJECTED.equalsIgnoreCase(deck.reviewStatus)) {
            throw new CofException(ErrorCode.CONFLICT, "该牌组已被拒绝，无法继续编辑。");
        }
        return deck;
    }

    private void requirePmv(long deckId, int pmvId, String clientId) {
        CofDeckPmv pmv = deckPmvMapper.selectOne(
                new QueryWrapper<CofDeckPmv>().eq("deck_id", deckId).eq("pmv_id", pmvId).last("LIMIT 1"));
        if (pmv == null) {
            throw new CofException(ErrorCode.BAD_REQUEST, "请先添加该 PMV。");
        }
        if (!clientId.equals(pmv.submitterClientId) && !ReviewStatus.isApproved(pmv.reviewStatus)) {
            throw new CofException(ErrorCode.FORBIDDEN, "无权向该 PMV 添加卡牌。");
        }
    }

    private void recomputeDeckCounts(CofDeck deck) {
        List<CofCard> cards = cardMapper.listByDeckId(deck.id);
        List<CofDeckPmv> pmvs = deckPmvMapper.listByDeckId(deck.id);
        deck.cardCount = cards.size();
        deck.pmvCount = pmvs.size();
        deck.updatedAt = System.currentTimeMillis();
        deckMapper.updateById(deck);
    }

    private Path deckDir(long deckId) {
        return resourceRoot.resolve("cards").resolve(String.valueOf(deckId));
    }

    private Map<String, Object> deckPayload(CofDeck deck, String viewerId) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", deck.id);
        row.put("folderName", deck.folderName);
        row.put("name", deck.name);
        row.put("curator", deck.curator);
        row.put("description", deck.description);
        row.put("version", deck.version);
        row.put("link", deck.link);
        row.put("backUrl", deck.backUrl);
        row.put("cardCount", deck.cardCount);
        row.put("pmvCount", deck.pmvCount);
        row.put("reviewStatus", deck.reviewStatus);
        row.put("enabled", deck.enabled);
        row.put("updatedAt", deck.updatedAt);
        row.put("visibleInGame", deckCatalogService.isDeckPlayable(deck));
        List<Map<String, Object>> pmvRows = new ArrayList<>();
        for (CofDeckPmv pmv : deckPmvMapper.listByDeckId(deck.id)) {
            if (ReviewStatus.isVisibleToUser(pmv.reviewStatus, pmv.submitterClientId, viewerId)) {
                pmvRows.add(pmvPayload(pmv));
            }
        }
        row.put("pmvs", pmvRows);
        List<Map<String, Object>> cardRows = new ArrayList<>();
        for (CofCard card : cardMapper.listByDeckId(deck.id)) {
            if (ReviewStatus.isVisibleToUser(card.reviewStatus, card.submitterClientId, viewerId)) {
                cardRows.add(cardPayload(card));
            }
        }
        row.put("cards", cardRows);
        return row;
    }

    private static Map<String, Object> pmvPayload(CofDeckPmv pmv) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", pmv.id);
        row.put("deckId", pmv.deckId);
        row.put("pmvId", pmv.pmvId);
        row.put("name", pmv.name);
        row.put("author", pmv.author);
        row.put("description", pmv.description);
        row.put("link", pmv.link);
        row.put("reviewStatus", pmv.reviewStatus);
        return row;
    }

    private static Map<String, Object> cardPayload(CofCard card) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", card.id);
        row.put("deckId", card.deckId);
        row.put("pmvId", card.pmvId);
        row.put("shot", card.shot);
        row.put("imageUrl", card.imageUrl);
        row.put("reviewStatus", card.reviewStatus);
        return row;
    }

    private static String buildFolderName(String clientId, String slug, String name) {
        String base = slug != null && !slug.isBlank() ? slug : name;
        base = base.trim().replaceAll("\\s+", "-");
        if (!FOLDER_PATTERN.matcher(base).matches()) {
            base = "deck";
        }
        String prefix = "u" + clientId.replace("-", "").substring(0, Math.min(8, clientId.length()));
        return prefix + "_" + base;
    }

    private static String normalizeShot(String shot) {
        if (shot == null || shot.isBlank()) {
            throw new CofException(ErrorCode.BAD_REQUEST, "镜头标识不能为空（a-z）。");
        }
        String s = shot.trim().toLowerCase(Locale.ROOT);
        if (!SHOT_PATTERN.matcher(s).matches()) {
            throw new CofException(ErrorCode.BAD_REQUEST, "镜头标识须为单个小写字母 a-z。");
        }
        return s;
    }

    private static String requiredString(Map<String, Object> body, String key, String label) {
        String value = optionalString(body, key);
        if (value == null || value.isBlank()) {
            throw new CofException(ErrorCode.BAD_REQUEST, label + "不能为空。");
        }
        return value.trim();
    }

    private static String optionalString(Map<String, Object> body, String key) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return null;
        }
        return String.valueOf(body.get(key));
    }

    private static int requiredInt(Map<String, Object> body, String key, String label) {
        Object raw = body.get(key);
        if (raw == null) {
            throw new CofException(ErrorCode.BAD_REQUEST, label + "不能为空。");
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            throw new CofException(ErrorCode.BAD_REQUEST, label + "格式无效。");
        }
    }
}
