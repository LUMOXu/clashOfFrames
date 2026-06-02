package com.lumoxu.cof.service;

import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.common.catalog.ReviewStatus;
import com.lumoxu.cof.domain.entity.CofCard;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.entity.CofDeckPmv;
import com.lumoxu.cof.domain.mapper.CofCardMapper;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.domain.mapper.CofDeckPmvMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
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

    private final ResourcePathMigration paths;
    private final CofDeckMapper deckMapper;
    private final CofDeckPmvMapper deckPmvMapper;
    private final CofCardMapper cardMapper;
    private final SubmissionImageProcessor imageProcessor;
    private final DeckCatalogService deckCatalogService;

    public DeckSubmissionService(
            ResourcePathMigration paths,
            CofDeckMapper deckMapper,
            CofDeckPmvMapper deckPmvMapper,
            CofCardMapper cardMapper,
            SubmissionImageProcessor imageProcessor,
            DeckCatalogService deckCatalogService) {
        this.paths = paths;
        this.deckMapper = deckMapper;
        this.deckPmvMapper = deckPmvMapper;
        this.cardMapper = cardMapper;
        this.imageProcessor = imageProcessor;
        this.deckCatalogService = deckCatalogService;
    }

    @Transactional
    public void deleteDeck(String clientId, long deckId) {
        CofDeck deck = requireOwnedDeck(clientId, deckId);
        cardMapper.delete(new QueryWrapper<CofCard>().eq("deck_id", deckId));
        deckPmvMapper.delete(new QueryWrapper<CofDeckPmv>().eq("deck_id", deckId));
        deckMapper.deleteById(deck.id);
        deckCatalogService.bustCaches();
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
        paths.writeDeckBack(jpeg, deckId);
        deck.backUrl = ResourcePathMigration.backUrl(deckId);
        deck.updatedAt = System.currentTimeMillis();
        deckMapper.updateById(deck);
        deckCatalogService.bustCaches();
        return deckPayload(deck, clientId);
    }

    @Transactional
    public Map<String, Object> addPmv(String clientId, long deckId, Map<String, Object> body) {
        CofDeck deck = requireOwnedDeck(clientId, deckId);
        int matchId = requiredInt(body, "pmvId", "PMV ID");
        if (deckPmvMapper.findByDeckIdAndMatchId(deckId, matchId) != null) {
            throw new CofException(ErrorCode.CONFLICT, "该 PMV ID 已存在于本牌组。");
        }
        Map<String, Object> canonical = deckCatalogService.findApprovedPmvByMatchId(matchId);
        String pmvName = requiredString(body, "name", "PMV 名称");
        String pmvAuthor = optionalString(body, "author");
        String pmvDescription = optionalString(body, "description");
        String pmvLink = optionalString(body, "link");
        if (canonical != null && !isPmvOwnedByClient(canonical, clientId, deckId)) {
            String canonicalName = String.valueOf(canonical.get("name"));
            if (!canonicalName.equals(pmvName.trim())) {
                throw new CofException(
                        ErrorCode.BAD_REQUEST,
                        "该 PMV 编号已在其他牌组登记，名称须与已有 PMV 一致（"
                                + canonicalName
                                + "）。");
            }
            pmvName = canonicalName;
            pmvAuthor = canonical.get("author") != null ? String.valueOf(canonical.get("author")) : null;
            pmvDescription =
                    canonical.get("description") != null ? String.valueOf(canonical.get("description")) : null;
            pmvLink = canonical.get("link") != null ? String.valueOf(canonical.get("link")) : null;
        }
        CofDeckPmv pmv = new CofDeckPmv();
        pmv.deckId = deckId;
        pmv.matchId = matchId;
        pmv.name = pmvName;
        pmv.author = pmvAuthor;
        pmv.description = pmvDescription;
        pmv.link = pmvLink;
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
            int matchId,
            String shot,
            InputStream file,
            long fileSize,
            Integer cropX,
            Integer cropY,
            Integer cropW,
            Integer cropH) throws IOException {
        CofDeck deck = requireOwnedDeck(clientId, deckId);
        String normalizedShot = normalizeShot(shot);
        CofDeckPmv pmv = requirePmv(deckId, matchId, clientId);
        if (fileSize > 15 * 1024 * 1024) {
            throw new CofException(ErrorCode.BAD_REQUEST, "图片过大（最大 15MB）。");
        }
        byte[] jpeg = imageProcessor.processCardFrame(file, cropX, cropY, cropW, cropH);
        CofCard existing = cardMapper.findByPmvIdAndShot(pmv.pmvId, normalizedShot);
        if (existing != null) {
            if (!clientId.equals(existing.submitterClientId)) {
                throw new CofException(ErrorCode.FORBIDDEN, "只能覆盖自己提交的同镜头卡牌。");
            }
            paths.deleteCardImageIfPresent(existing.imageUrl);
            String imageUrl = paths.storeCardImage(jpeg, "jpg");
            existing.imageUrl = imageUrl;
            existing.fileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
            existing.reviewStatus = ReviewStatus.PENDING;
            cardMapper.updateById(existing);
            recomputeDeckCounts(deck);
            deckCatalogService.bustCaches();
            return cardPayload(existing, pmv.matchId);
        }
        String imageUrl = paths.storeCardImage(jpeg, "jpg");
        CofCard card = new CofCard();
        card.deckId = deckId;
        card.pmvId = pmv.pmvId;
        card.shot = normalizedShot;
        card.fileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        card.imageUrl = imageUrl;
        card.cardUid = deckId + "/" + matchId + "/" + normalizedShot;
        card.submitterClientId = clientId;
        card.reviewStatus = ReviewStatus.PENDING;
        cardMapper.insert(card);
        recomputeDeckCounts(deck);
        deckCatalogService.bustCaches();
        return cardPayload(card, pmv.matchId);
    }

    @Transactional
    public void deletePmv(String clientId, long deckId, int matchId) {
        CofDeck deck = requireOwnedDeck(clientId, deckId);
        CofDeckPmv pmv = requireOwnedPmvRow(clientId, deckId, matchId);
        List<CofCard> cards = cardMapper.selectList(new QueryWrapper<CofCard>().eq("pmv_id", pmv.pmvId));
        for (CofCard card : cards) {
            paths.deleteCardImageIfPresent(card.imageUrl);
        }
        cardMapper.delete(new QueryWrapper<CofCard>().eq("pmv_id", pmv.pmvId));
        deckPmvMapper.deleteById(pmv.pmvId);
        recomputeDeckCounts(deck);
        deckCatalogService.bustCaches();
    }

    @Transactional
    public void deleteCard(String clientId, long deckId, int matchId, String shot) {
        CofDeck deck = requireOwnedDeck(clientId, deckId);
        CofDeckPmv pmv = requireOwnedPmvRow(clientId, deckId, matchId);
        String normalizedShot = normalizeShot(shot);
        CofCard card = cardMapper.findByPmvIdAndShot(pmv.pmvId, normalizedShot);
        if (card == null) {
            throw new CofException(ErrorCode.NOT_FOUND, "镜头不存在。");
        }
        if (!clientId.equals(card.submitterClientId)) {
            throw new CofException(ErrorCode.FORBIDDEN, "只能删除自己提交的镜头。");
        }
        paths.deleteCardImageIfPresent(card.imageUrl);
        cardMapper.deleteById(card.cardId);
        recomputeDeckCounts(deck);
        deckCatalogService.bustCaches();
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

    private CofDeckPmv requirePmv(long deckId, int matchId, String clientId) {
        CofDeckPmv pmv = deckPmvMapper.findByDeckIdAndMatchId(deckId, matchId);
        if (pmv == null) {
            throw new CofException(ErrorCode.BAD_REQUEST, "请先添加该 PMV。");
        }
        if (!clientId.equals(pmv.submitterClientId) && !ReviewStatus.isApproved(pmv.reviewStatus)) {
            throw new CofException(ErrorCode.FORBIDDEN, "无权向该 PMV 添加卡牌。");
        }
        return pmv;
    }

    private CofDeckPmv requireOwnedPmvRow(String clientId, long deckId, int matchId) {
        CofDeckPmv pmv = deckPmvMapper.findByDeckIdAndMatchId(deckId, matchId);
        if (pmv == null) {
            throw new CofException(ErrorCode.NOT_FOUND, "PMV 不存在。");
        }
        if (!clientId.equals(pmv.submitterClientId)) {
            throw new CofException(ErrorCode.FORBIDDEN, "只能删除自己提交的 PMV。");
        }
        return pmv;
    }

    private void recomputeDeckCounts(CofDeck deck) {
        List<CofCard> cards = cardMapper.listByDeckId(deck.id);
        List<CofDeckPmv> pmvs = deckPmvMapper.listByDeckId(deck.id);
        deck.cardCount = cards.size();
        deck.pmvCount = pmvs.size();
        deck.updatedAt = System.currentTimeMillis();
        deckMapper.updateById(deck);
    }

    private Map<String, Object> deckPayload(CofDeck deck, String viewerId) {
        Map<Long, Integer> matchByPmvId = new HashMap<>();
        for (CofDeckPmv pmv : deckPmvMapper.listByDeckId(deck.id)) {
            matchByPmvId.put(pmv.pmvId, pmv.matchId);
        }
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
                Integer matchId = matchByPmvId.get(card.pmvId);
                cardRows.add(cardPayload(card, matchId));
            }
        }
        row.put("cards", cardRows);
        return row;
    }

    private static Map<String, Object> pmvPayload(CofDeckPmv pmv) {
        Map<String, Object> row = new HashMap<>();
        row.put("pmvRowId", pmv.pmvId);
        row.put("deckId", pmv.deckId);
        row.put("pmvId", pmv.matchId);
        row.put("name", pmv.name);
        row.put("author", pmv.author);
        row.put("description", pmv.description);
        row.put("link", pmv.link);
        row.put("reviewStatus", pmv.reviewStatus);
        return row;
    }

    private static Map<String, Object> cardPayload(CofCard card, Integer matchId) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", card.cardId);
        row.put("deckId", card.deckId);
        row.put("pmvId", matchId);
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

    private static boolean isPmvOwnedByClient(Map<String, Object> canonical, String clientId, long deckId) {
        Object deckRef = canonical.get("deckId");
        if (deckRef instanceof Number number && number.longValue() == deckId) {
            return true;
        }
        Object submitter = canonical.get("submitterClientId");
        return submitter != null && clientId.equals(String.valueOf(submitter));
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
