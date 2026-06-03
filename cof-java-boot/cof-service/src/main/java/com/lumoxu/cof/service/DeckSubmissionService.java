package com.lumoxu.cof.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.common.catalog.ReviewStatus;
import com.lumoxu.cof.domain.entity.CofCard;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.entity.CofPmv;
import com.lumoxu.cof.domain.mapper.CofCardMapper;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.domain.mapper.CofPmvMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeckSubmissionService {

    private final ResourcePathMigration paths;
    private final CofDeckMapper deckMapper;
    private final CofPmvMapper pmvMapper;
    private final CofCardMapper cardMapper;
    private final SubmissionImageProcessor imageProcessor;
    private final DeckCatalogService deckCatalogService;
    private final CatalogNameGuard nameGuard;

    public DeckSubmissionService(
            ResourcePathMigration paths,
            CofDeckMapper deckMapper,
            CofPmvMapper pmvMapper,
            CofCardMapper cardMapper,
            SubmissionImageProcessor imageProcessor,
            DeckCatalogService deckCatalogService,
            CatalogNameGuard nameGuard) {
        this.paths = paths;
        this.deckMapper = deckMapper;
        this.pmvMapper = pmvMapper;
        this.cardMapper = cardMapper;
        this.imageProcessor = imageProcessor;
        this.deckCatalogService = deckCatalogService;
        this.nameGuard = nameGuard;
    }

    public List<Map<String, Object>> listPmvsForPicker(String clientId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CofPmv pmv : pmvMapper.listAlive()) {
            if (!ReviewStatus.isApproved(pmv.reviewStatus)
                    && !clientId.equals(pmv.submitterClientId)) {
                continue;
            }
            rows.add(pmvPayload(pmv));
        }
        rows.sort((a, b) -> String.valueOf(a.get("name"))
                .compareToIgnoreCase(String.valueOf(b.get("name"))));
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
        nameGuard.ensureDeckNameAvailable(name, null);
        Instant now = CatalogRevisionHelper.now();
        CofDeck deck = new CofDeck();
        deck.name = name;
        deck.description = optionalString(body, "description");
        deck.enabled = true;
        deck.reviewStatus = ReviewStatus.PENDING;
        deck.submitterClientId = clientId;
        deck.createdAt = now;
        deck.updatedAt = now;
        deck.backUrl = ResourcePathMigration.backUrl(0);
        deckMapper.insert(deck);
        deck.backUrl = ResourcePathMigration.backUrl(deck.id);
        deckMapper.updateById(deck);
        deckCatalogService.bustCaches();
        return deckPayload(deck, clientId);
    }

    @Transactional
    public Map<String, Object> updateDeck(String clientId, long deckId, Map<String, Object> body) {
        CofDeck deck = requireOwnedDeck(clientId, deckId);
        if (CatalogRevisionHelper.isApprovedLive(deck)) {
            CatalogRevisionHelper.ensureNoPendingEdit(deck);
            applyDeckPending(deck, body);
            deck.pendingReviewStatus = CatalogRevisionHelper.PENDING_EDIT;
        } else {
            applyDeckLive(deck, body);
            deck.reviewStatus = ReviewStatus.PENDING;
        }
        CatalogRevisionHelper.touch(deck);
        deckMapper.updateById(deck);
        deckCatalogService.bustCachesForDeck(deckId);
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
        String backUrl = ResourcePathMigration.backUrl(deckId);
        if (CatalogRevisionHelper.isApprovedLive(deck)) {
            CatalogRevisionHelper.ensureNoPendingEdit(deck);
            deck.pendingBackUrl = backUrl;
            deck.pendingReviewStatus = CatalogRevisionHelper.PENDING_EDIT;
        } else {
            deck.backUrl = backUrl;
            deck.reviewStatus = ReviewStatus.PENDING;
        }
        CatalogRevisionHelper.touch(deck);
        deckMapper.updateById(deck);
        deckCatalogService.bustCachesForDeck(deckId);
        return deckPayload(deck, clientId);
    }

    @Transactional
    public Map<String, Object> createPmv(String clientId, Map<String, Object> body) {
        String pmvName = requiredString(body, "name", "PMV 名称");
        nameGuard.ensurePmvNameAvailable(pmvName, null);
        Instant now = CatalogRevisionHelper.now();
        CofPmv pmv = new CofPmv();
        pmv.name = pmvName;
        pmv.author = optionalString(body, "author");
        pmv.description = optionalString(body, "description");
        pmv.link = optionalString(body, "link");
        pmv.reviewStatus = ReviewStatus.PENDING;
        pmv.submitterClientId = clientId;
        pmv.createdAt = now;
        pmv.updatedAt = now;
        pmvMapper.insert(pmv);
        deckCatalogService.bustCaches();
        return pmvPayload(pmv);
    }

    @Transactional
    public Map<String, Object> updatePmv(String clientId, long pmvId, Map<String, Object> body) {
        CofPmv pmv = requireOwnedPmv(clientId, pmvId);
        if (CatalogRevisionHelper.isApprovedLive(pmv)) {
            CatalogRevisionHelper.ensureNoPendingEdit(pmv);
            applyPmvPending(pmv, body);
            pmv.pendingReviewStatus = CatalogRevisionHelper.PENDING_EDIT;
        } else {
            applyPmvLive(pmv, body);
            pmv.reviewStatus = ReviewStatus.PENDING;
        }
        CatalogRevisionHelper.touch(pmv);
        pmvMapper.updateById(pmv);
        deckCatalogService.bustCaches();
        return pmvPayload(pmv);
    }

    @Transactional
    public Map<String, Object> addCard(
            String clientId,
            long deckId,
            long pmvId,
            String cardName,
            String cardDescription,
            InputStream file,
            long fileSize,
            Integer cropX,
            Integer cropY,
            Integer cropW,
            Integer cropH) throws IOException {
        CofDeck deck = requireOwnedDeck(clientId, deckId);
        CofPmv pmv = requirePmvForCard(clientId, pmvId);
        if (fileSize > 15 * 1024 * 1024) {
            throw new CofException(ErrorCode.BAD_REQUEST, "图片过大（最大 15MB）。");
        }
        byte[] jpeg = imageProcessor.processCardFrame(file, cropX, cropY, cropW, cropH);
        String imageUrl = paths.storeCardImage(jpeg, "jpg");
        ensureImageUrlAvailable(imageUrl, null);
        Instant now = CatalogRevisionHelper.now();
        CofCard card = new CofCard();
        card.deckId = deckId;
        card.pmvId = pmvId;
        card.name = blankToNull(cardName);
        card.description = blankToNull(cardDescription);
        card.imageUrl = imageUrl;
        card.reviewStatus = ReviewStatus.PENDING;
        card.submitterClientId = clientId;
        card.createdAt = now;
        card.updatedAt = now;
        cardMapper.insert(card);
        deckCatalogService.bustCachesForDeck(deckId);
        return cardPayload(card, pmv);
    }

    @Transactional
    public Map<String, Object> updateCard(
            String clientId,
            long cardId,
            String cardName,
            String cardDescription,
            InputStream file,
            long fileSize,
            Integer cropX,
            Integer cropY,
            Integer cropW,
            Integer cropH) throws IOException {
        CofCard card = requireOwnedCard(clientId, cardId);
        CofPmv pmv = pmvMapper.selectById(card.pmvId);
        if (CatalogRevisionHelper.isApprovedLive(card)) {
            CatalogRevisionHelper.ensureNoPendingEdit(card);
            if (file != null && fileSize > 0) {
                if (fileSize > 15 * 1024 * 1024) {
                    throw new CofException(ErrorCode.BAD_REQUEST, "图片过大（最大 15MB）。");
                }
                byte[] jpeg = imageProcessor.processCardFrame(file, cropX, cropY, cropW, cropH);
                String imageUrl = paths.storeCardImage(jpeg, "jpg");
                ensureImageUrlAvailable(imageUrl, card.id);
                card.pendingImageUrl = imageUrl;
            }
            if (cardName != null) {
                card.pendingName = blankToNull(cardName);
            }
            if (cardDescription != null) {
                card.pendingDescription = blankToNull(cardDescription);
            }
            card.pendingReviewStatus = CatalogRevisionHelper.PENDING_EDIT;
        } else {
            if (file != null && fileSize > 0) {
                if (fileSize > 15 * 1024 * 1024) {
                    throw new CofException(ErrorCode.BAD_REQUEST, "图片过大（最大 15MB）。");
                }
                byte[] jpeg = imageProcessor.processCardFrame(file, cropX, cropY, cropW, cropH);
                String imageUrl = paths.storeCardImage(jpeg, "jpg");
                ensureImageUrlAvailable(imageUrl, card.id);
                card.imageUrl = imageUrl;
            }
            if (cardName != null) {
                card.name = blankToNull(cardName);
            }
            if (cardDescription != null) {
                card.description = blankToNull(cardDescription);
            }
            card.reviewStatus = ReviewStatus.PENDING;
        }
        CatalogRevisionHelper.touch(card);
        cardMapper.updateById(card);
        deckCatalogService.bustCachesForDeck(card.deckId);
        return cardPayload(card, pmv);
    }

    @Transactional
    public void deleteDeck(String clientId, long deckId) {
        requireOwnedDeck(clientId, deckId);
        Instant now = CatalogRevisionHelper.now();
        cardMapper.update(
                null,
                new UpdateWrapper<CofCard>()
                        .eq("deck_id", deckId)
                        .isNull("deleted_at")
                        .set("deleted_at", now)
                        .set("updated_at", now));
        CofDeck deck = deckMapper.selectById(deckId);
        deck.deletedAt = now;
        CatalogRevisionHelper.touch(deck);
        deckMapper.updateById(deck);
        deckCatalogService.bustCaches();
    }

    @Transactional
    public void deletePmv(String clientId, long pmvId) {
        requireOwnedPmv(clientId, pmvId);
        Instant now = CatalogRevisionHelper.now();
        CofPmv pmv = pmvMapper.selectById(pmvId);
        pmv.deletedAt = now;
        CatalogRevisionHelper.touch(pmv);
        pmvMapper.updateById(pmv);
        deckCatalogService.bustCaches();
    }

    @Transactional
    public void deleteCard(String clientId, long cardId) {
        CofCard card = requireOwnedCard(clientId, cardId);
        card.deletedAt = CatalogRevisionHelper.now();
        CatalogRevisionHelper.touch(card);
        cardMapper.updateById(card);
        deckCatalogService.bustCachesForDeck(card.deckId);
    }

    private void applyDeckLive(CofDeck deck, Map<String, Object> body) {
        if (body.containsKey("name")) {
            String name = requiredString(body, "name", "牌组名称");
            nameGuard.ensureDeckNameAvailable(name, deck.id);
            deck.name = name;
        }
        if (body.containsKey("description")) {
            deck.description = optionalString(body, "description");
        }
    }

    private void applyDeckPending(CofDeck deck, Map<String, Object> body) {
        if (body.containsKey("name")) {
            String name = requiredString(body, "name", "牌组名称");
            nameGuard.ensureDeckNameAvailable(name, deck.id);
            deck.pendingName = name;
        }
        if (body.containsKey("description")) {
            deck.pendingDescription = optionalString(body, "description");
        }
    }

    private void applyPmvLive(CofPmv pmv, Map<String, Object> body) {
        if (body.containsKey("name")) {
            String name = requiredString(body, "name", "PMV 名称");
            nameGuard.ensurePmvNameAvailable(name, pmv.id);
            pmv.name = name;
        }
        if (body.containsKey("author")) {
            pmv.author = optionalString(body, "author");
        }
        if (body.containsKey("description")) {
            pmv.description = optionalString(body, "description");
        }
        if (body.containsKey("link")) {
            pmv.link = optionalString(body, "link");
        }
    }

    private void applyPmvPending(CofPmv pmv, Map<String, Object> body) {
        if (body.containsKey("name")) {
            String name = requiredString(body, "name", "PMV 名称");
            nameGuard.ensurePmvNameAvailable(name, pmv.id);
            pmv.pendingName = name;
        }
        if (body.containsKey("author")) {
            pmv.pendingAuthor = optionalString(body, "author");
        }
        if (body.containsKey("description")) {
            pmv.pendingDescription = optionalString(body, "description");
        }
        if (body.containsKey("link")) {
            pmv.pendingLink = optionalString(body, "link");
        }
    }

    private CofDeck requireOwnedDeck(String clientId, long deckId) {
        CofDeck deck = deckMapper.selectById(deckId);
        if (deck == null || !CatalogRevisionHelper.isAlive(deck)) {
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

    private CofPmv requireOwnedPmv(String clientId, long pmvId) {
        CofPmv pmv = pmvMapper.selectById(pmvId);
        if (pmv == null || !CatalogRevisionHelper.isAlive(pmv)) {
            throw new CofException(ErrorCode.NOT_FOUND, "PMV 不存在。");
        }
        if (!clientId.equals(pmv.submitterClientId)) {
            throw new CofException(ErrorCode.FORBIDDEN, "只能编辑自己提交的 PMV。");
        }
        return pmv;
    }

    private CofPmv requirePmvForCard(String clientId, long pmvId) {
        CofPmv pmv = pmvMapper.selectById(pmvId);
        if (pmv == null || !CatalogRevisionHelper.isAlive(pmv)) {
            throw new CofException(ErrorCode.NOT_FOUND, "PMV 不存在。");
        }
        if (!clientId.equals(pmv.submitterClientId) && !ReviewStatus.isApproved(pmv.reviewStatus)) {
            throw new CofException(ErrorCode.FORBIDDEN, "只能使用已通过或自己提交的 PMV。");
        }
        return pmv;
    }

    private CofCard requireOwnedCard(String clientId, long cardId) {
        CofCard card = cardMapper.selectById(cardId);
        if (card == null || !CatalogRevisionHelper.isAlive(card)) {
            throw new CofException(ErrorCode.NOT_FOUND, "卡牌不存在。");
        }
        if (!clientId.equals(card.submitterClientId)) {
            throw new CofException(ErrorCode.FORBIDDEN, "只能编辑自己提交的卡牌。");
        }
        return card;
    }

    private void ensureImageUrlAvailable(String imageUrl, Long excludeCardId) {
        CofCard byUrl = cardMapper.findAliveByImageUrl(imageUrl);
        if (byUrl != null && (excludeCardId == null || !excludeCardId.equals(byUrl.id))) {
            throw new CofException(ErrorCode.CONFLICT, "图片地址已被占用。");
        }
        CofCard byPending = cardMapper.findAliveByPendingImageUrl(imageUrl);
        if (byPending != null && (excludeCardId == null || !excludeCardId.equals(byPending.id))) {
            throw new CofException(ErrorCode.CONFLICT, "图片地址已被其他待审修改占用。");
        }
    }

    private Map<String, Object> deckPayload(CofDeck deck, String viewerId) {
        Map<Long, CofPmv> pmvById = new HashMap<>();
        for (CofPmv pmv : pmvMapper.listAlive()) {
            if (ReviewStatus.isVisibleToUser(pmv.reviewStatus, pmv.submitterClientId, viewerId)) {
                pmvById.put(pmv.id, pmv);
            }
        }
        Map<String, Object> row = new HashMap<>();
        row.put("id", deck.id);
        row.put("name", deck.name);
        row.put("description", deck.description);
        row.put("backUrl", deck.backUrl);
        row.put("reviewStatus", deck.reviewStatus);
        row.put("pendingReviewStatus", deck.pendingReviewStatus);
        row.put("pendingName", deck.pendingName);
        row.put("pendingDescription", deck.pendingDescription);
        row.put("pendingBackUrl", deck.pendingBackUrl);
        row.put("enabled", deck.enabled);
        row.put("cardCount", cardMapper.countAliveByDeckId(deck.id));
        row.put("pmvCount", cardMapper.countDistinctPmvByDeckId(deck.id));
        row.put("visibleInGame", deckCatalogService.isDeckPlayable(deck));
        List<Map<String, Object>> cardRows = new ArrayList<>();
        for (CofCard card : cardMapper.listAliveByDeckId(deck.id)) {
            if (!ReviewStatus.isVisibleToUser(card.reviewStatus, card.submitterClientId, viewerId)) {
                continue;
            }
            CofPmv pmv = pmvById.get(card.pmvId);
            if (pmv != null) {
                cardRows.add(cardPayload(card, pmv));
            }
        }
        row.put("cards", cardRows);
        return row;
    }

    private static Map<String, Object> pmvPayload(CofPmv pmv) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", pmv.id);
        row.put("pmvId", pmv.id);
        row.put("name", pmv.name);
        row.put("author", pmv.author);
        row.put("description", pmv.description);
        row.put("link", pmv.link);
        row.put("reviewStatus", pmv.reviewStatus);
        row.put("pendingReviewStatus", pmv.pendingReviewStatus);
        return row;
    }

    private static Map<String, Object> cardPayload(CofCard card, CofPmv pmv) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", card.id);
        row.put("deckId", card.deckId);
        row.put("pmvId", pmv != null ? pmv.id : card.pmvId);
        row.put("pmvName", pmv != null ? pmv.name : null);
        row.put("pmvAuthor", pmv != null ? pmv.author : null);
        row.put("name", card.name);
        row.put("description", card.description);
        row.put("imageUrl", card.imageUrl);
        row.put("pendingImageUrl", card.pendingImageUrl);
        row.put("reviewStatus", card.reviewStatus);
        row.put("pendingReviewStatus", card.pendingReviewStatus);
        return row;
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

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
