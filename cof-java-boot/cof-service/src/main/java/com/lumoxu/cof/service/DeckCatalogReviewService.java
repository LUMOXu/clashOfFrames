package com.lumoxu.cof.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

@Service
public class DeckCatalogReviewService {

    private final CofDeckMapper deckMapper;
    private final CofPmvMapper pmvMapper;
    private final CofCardMapper cardMapper;
    private final DeckCatalogService deckCatalogService;
    private final CatalogNameGuard nameGuard;

    public DeckCatalogReviewService(
            CofDeckMapper deckMapper,
            CofPmvMapper pmvMapper,
            CofCardMapper cardMapper,
            DeckCatalogService deckCatalogService,
            CatalogNameGuard nameGuard) {
        this.deckMapper = deckMapper;
        this.pmvMapper = pmvMapper;
        this.cardMapper = cardMapper;
        this.deckCatalogService = deckCatalogService;
        this.nameGuard = nameGuard;
    }

    @Transactional
    public void approveDeck(long deckId) {
        CofDeck deck = requireDeck(deckId);
        if (CatalogRevisionHelper.hasPendingEdit(deck)) {
            if (deck.pendingName != null) {
                nameGuard.ensureDeckNameAvailable(deck.pendingName, deck.id);
            }
            CatalogRevisionHelper.approveDeckRevision(deck);
        } else if (ReviewStatus.PENDING.equalsIgnoreCase(deck.reviewStatus)) {
            CatalogRevisionHelper.approveDeckInitial(deck);
        }
        deckMapper.updateById(deck);
        deckCatalogService.bustCachesForDeck(deckId);
    }

    @Transactional
    public void rejectDeck(long deckId) {
        CofDeck deck = requireDeck(deckId);
        if (CatalogRevisionHelper.hasPendingEdit(deck)) {
            CatalogRevisionHelper.rejectDeckRevision(deck);
        } else {
            deck.reviewStatus = ReviewStatus.REJECTED;
            CatalogRevisionHelper.touch(deck);
        }
        deckMapper.updateById(deck);
        deckCatalogService.bustCachesForDeck(deckId);
    }

    @Transactional
    public void approvePmv(long pmvId) {
        CofPmv pmv = requirePmv(pmvId);
        if (CatalogRevisionHelper.hasPendingEdit(pmv)) {
            if (pmv.pendingName != null) {
                nameGuard.ensurePmvNameAvailable(pmv.pendingName, pmv.id);
            }
            CatalogRevisionHelper.approvePmvRevision(pmv);
        } else if (ReviewStatus.PENDING.equalsIgnoreCase(pmv.reviewStatus)) {
            CatalogRevisionHelper.approvePmvInitial(pmv);
        }
        pmvMapper.updateById(pmv);
        deckCatalogService.bustCaches();
    }

    @Transactional
    public void rejectPmv(long pmvId) {
        CofPmv pmv = requirePmv(pmvId);
        if (CatalogRevisionHelper.hasPendingEdit(pmv)) {
            CatalogRevisionHelper.rejectPmvRevision(pmv);
        } else {
            pmv.reviewStatus = ReviewStatus.REJECTED;
            CatalogRevisionHelper.touch(pmv);
        }
        pmvMapper.updateById(pmv);
        deckCatalogService.bustCaches();
    }

    @Transactional
    public void approveCard(long cardId) {
        CofCard card = requireCard(cardId);
        CofDeck deck = requireDeck(card.deckId);
        CofPmv pmv = requirePmv(card.pmvId);
        if (!CatalogRevisionHelper.isApprovedLive(deck) || !CatalogRevisionHelper.isApprovedLive(pmv)) {
            throw new CofException(ErrorCode.CONFLICT, "须先通过牌组与 PMV 审核，才能通过卡牌。");
        }
        if (CatalogRevisionHelper.hasPendingEdit(card)) {
            if (card.pendingImageUrl != null) {
                ensureImageUrlAvailable(card.pendingImageUrl, card.id);
            }
            CatalogRevisionHelper.approveCardRevision(card);
        } else if (ReviewStatus.PENDING.equalsIgnoreCase(card.reviewStatus)) {
            CatalogRevisionHelper.approveCardInitial(card);
        }
        cardMapper.updateById(card);
        deckCatalogService.bustCachesForDeck(card.deckId);
    }

    @Transactional
    public void rejectCard(long cardId) {
        CofCard card = requireCard(cardId);
        if (CatalogRevisionHelper.hasPendingEdit(card)) {
            CatalogRevisionHelper.rejectCardRevision(card);
        } else {
            card.reviewStatus = ReviewStatus.REJECTED;
            CatalogRevisionHelper.touch(card);
        }
        cardMapper.updateById(card);
        deckCatalogService.bustCachesForDeck(card.deckId);
    }

    @Transactional
    public void reconcileAndRefreshCaches() {
        for (CofDeck deck : deckMapper.selectList(new QueryWrapper<CofDeck>().isNull("deleted_at"))) {
            if (deck == null || deck.id == null) {
                continue;
            }
            if (ReviewStatus.isApproved(deck.reviewStatus) && !Boolean.TRUE.equals(deck.enabled)) {
                deck.enabled = true;
                CatalogRevisionHelper.touch(deck);
                deckMapper.updateById(deck);
            }
        }
        deckCatalogService.bustCaches();
    }

    public void refreshCaches() {
        deckCatalogService.bustCaches();
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

    private CofDeck requireDeck(long deckId) {
        CofDeck deck = deckMapper.selectById(deckId);
        if (deck == null || !CatalogRevisionHelper.isAlive(deck)) {
            throw new CofException(ErrorCode.NOT_FOUND, "牌组不存在。");
        }
        return deck;
    }

    private CofPmv requirePmv(long pmvId) {
        CofPmv pmv = pmvMapper.selectById(pmvId);
        if (pmv == null || !CatalogRevisionHelper.isAlive(pmv)) {
            throw new CofException(ErrorCode.NOT_FOUND, "PMV 不存在。");
        }
        return pmv;
    }

    private CofCard requireCard(long cardId) {
        CofCard card = cardMapper.selectById(cardId);
        if (card == null || !CatalogRevisionHelper.isAlive(card)) {
            throw new CofException(ErrorCode.NOT_FOUND, "卡牌不存在。");
        }
        return card;
    }
}
