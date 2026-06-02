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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Publishes user-submitted decks into the public catalog after review.
 * Keeps {@code cof_deck.enabled} and child review flags consistent so approved content
 * appears in {@link DeckCatalogService#listPublicSummaries()} and match card pools.
 */
@Service
public class DeckCatalogReviewService {

    private final CofDeckMapper deckMapper;
    private final CofDeckPmvMapper deckPmvMapper;
    private final CofCardMapper cardMapper;
    private final DeckCatalogService deckCatalogService;

    public DeckCatalogReviewService(
            CofDeckMapper deckMapper,
            CofDeckPmvMapper deckPmvMapper,
            CofCardMapper cardMapper,
            DeckCatalogService deckCatalogService) {
        this.deckMapper = deckMapper;
        this.deckPmvMapper = deckPmvMapper;
        this.cardMapper = cardMapper;
        this.deckCatalogService = deckCatalogService;
    }

    @Transactional
    public void approveDeck(long deckId) {
        CofDeck deck = requireDeck(deckId);
        long now = System.currentTimeMillis();
        deck.reviewStatus = ReviewStatus.APPROVED;
        deck.enabled = true;
        deck.updatedAt = now;
        deckMapper.updateById(deck);
        approvePendingChildren(deckId);
        deckCatalogService.bustCachesForDeck(deckId);
    }

    @Transactional
    public void rejectDeck(long deckId) {
        CofDeck deck = requireDeck(deckId);
        deck.reviewStatus = ReviewStatus.REJECTED;
        deck.enabled = false;
        deck.updatedAt = System.currentTimeMillis();
        deckMapper.updateById(deck);
        deckCatalogService.bustCachesForDeck(deckId);
    }

    @Transactional
    public void approvePmv(long pmvRowId) {
        CofDeckPmv pmv = requirePmvRow(pmvRowId);
        pmv.reviewStatus = ReviewStatus.APPROVED;
        deckPmvMapper.updateById(pmv);
        tryPublishDeck(pmv.deckId);
        deckCatalogService.bustCachesForDeck(pmv.deckId);
    }

    @Transactional
    public void rejectPmv(long pmvRowId) {
        CofDeckPmv pmv = requirePmvRow(pmvRowId);
        pmv.reviewStatus = ReviewStatus.REJECTED;
        deckPmvMapper.updateById(pmv);
        deckCatalogService.bustCachesForDeck(pmv.deckId);
    }

    @Transactional
    public void approveCard(long cardRowId) {
        CofCard card = requireCardRow(cardRowId);
        card.reviewStatus = ReviewStatus.APPROVED;
        cardMapper.updateById(card);
        tryPublishDeck(card.deckId);
        deckCatalogService.bustCachesForDeck(card.deckId);
    }

    @Transactional
    public void rejectCard(long cardRowId) {
        CofCard card = requireCardRow(cardRowId);
        card.reviewStatus = ReviewStatus.REJECTED;
        cardMapper.updateById(card);
        deckCatalogService.bustCachesForDeck(card.deckId);
    }

    /**
     * Fixes decks that are marked approved but not enabled, promotes decks whose PMVs/cards
     * are all approved, and clears catalog caches.
     */
    @Transactional
    public void reconcileAndRefreshCaches() {
        for (CofDeck deck : deckMapper.selectList(new QueryWrapper<>())) {
            if (deck == null || deck.id == null) {
                continue;
            }
            if (ReviewStatus.isApproved(deck.reviewStatus) && !Boolean.TRUE.equals(deck.enabled)) {
                deck.enabled = true;
                deck.updatedAt = System.currentTimeMillis();
                deckMapper.updateById(deck);
            }
            tryPublishDeck(deck.id);
        }
        deckCatalogService.bustCaches();
    }

    public void refreshCaches() {
        deckCatalogService.bustCaches();
    }

    private void approvePendingChildren(long deckId) {
        for (CofDeckPmv pmv : deckPmvMapper.listByDeckId(deckId)) {
            if (ReviewStatus.PENDING.equalsIgnoreCase(pmv.reviewStatus)) {
                pmv.reviewStatus = ReviewStatus.APPROVED;
                deckPmvMapper.updateById(pmv);
            }
        }
        for (CofCard card : cardMapper.listByDeckId(deckId)) {
            if (ReviewStatus.PENDING.equalsIgnoreCase(card.reviewStatus)) {
                card.reviewStatus = ReviewStatus.APPROVED;
                cardMapper.updateById(card);
            }
        }
    }

    /**
     * When every PMV and card in the deck is approved (and there is playable content),
     * mark the deck approved and enabled so it appears in public catalog APIs.
     */
    void tryPublishDeck(Long deckId) {
        if (deckId == null) {
            return;
        }
        CofDeck deck = deckMapper.selectById(deckId);
        if (deck == null || ReviewStatus.REJECTED.equalsIgnoreCase(deck.reviewStatus)) {
            return;
        }
        if (ReviewStatus.isApproved(deck.reviewStatus) && Boolean.TRUE.equals(deck.enabled)) {
            return;
        }
        List<CofDeckPmv> pmvs = deckPmvMapper.listByDeckId(deckId);
        List<CofCard> cards = cardMapper.listByDeckId(deckId);
        if (cards.isEmpty()) {
            return;
        }
        if (pmvs.isEmpty()) {
            return;
        }
        if (!allApproved(pmvs) || !allApprovedCards(cards)) {
            return;
        }
        deck.reviewStatus = ReviewStatus.APPROVED;
        deck.enabled = true;
        deck.updatedAt = System.currentTimeMillis();
        deckMapper.updateById(deck);
    }

    private static boolean allApproved(List<CofDeckPmv> pmvs) {
        for (CofDeckPmv pmv : pmvs) {
            if (!ReviewStatus.isApproved(pmv.reviewStatus)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allApprovedCards(List<CofCard> cards) {
        for (CofCard card : cards) {
            if (!ReviewStatus.isApproved(card.reviewStatus)) {
                return false;
            }
        }
        return true;
    }

    private CofDeck requireDeck(long deckId) {
        CofDeck deck = deckMapper.selectById(deckId);
        if (deck == null) {
            throw new CofException(ErrorCode.NOT_FOUND, "牌组不存在。");
        }
        return deck;
    }

    private CofDeckPmv requirePmvRow(long pmvRowId) {
        CofDeckPmv pmv = deckPmvMapper.selectById(pmvRowId);
        if (pmv == null) {
            throw new CofException(ErrorCode.NOT_FOUND, "PMV 不存在。");
        }
        return pmv;
    }

    private CofCard requireCardRow(long cardRowId) {
        CofCard card = cardMapper.selectById(cardRowId);
        if (card == null) {
            throw new CofException(ErrorCode.NOT_FOUND, "卡牌不存在。");
        }
        return card;
    }
}
