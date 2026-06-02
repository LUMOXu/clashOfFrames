package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.common.catalog.ReviewStatus;
import com.lumoxu.cof.domain.entity.CofCard;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.entity.CofDeckPmv;
import com.lumoxu.cof.domain.mapper.CofCardMapper;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.domain.mapper.CofDeckPmvMapper;
import com.lumoxu.cof.service.model.CardLibraryDto;
import com.lumoxu.cof.service.support.TestRedisSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeckCatalogReviewServiceTest {

    @Mock
    private CofDeckMapper deckMapper;
    @Mock
    private CofDeckPmvMapper deckPmvMapper;
    @Mock
    private CofCardMapper cardMapper;

    private DeckCatalogService deckCatalogService;
    private DeckCatalogReviewService reviewService;
    private com.lumoxu.cof.service.redis.JsonRedisOps redis;

    @BeforeEach
    void setUp() {
        redis = TestRedisSupport.memoryJsonRedis(new ObjectMapper());
        deckCatalogService = new DeckCatalogService(deckMapper, deckPmvMapper, cardMapper, redis);
        reviewService = new DeckCatalogReviewService(deckMapper, deckPmvMapper, cardMapper, deckCatalogService);
    }

    @Test
    void approveDeckEnablesAndAppearsInPublicSummaries() {
        CofDeck deck = pendingDeck(5L);
        when(deckMapper.selectById(5L)).thenReturn(deck);
        CofDeckPmv pmv = approvedPmv(5L, 1);
        CofCard card = approvedCard(5L, 1);
        when(deckPmvMapper.listByDeckId(5L)).thenReturn(List.of(pmv));
        when(cardMapper.listByDeckId(5L)).thenReturn(List.of(card));
        when(deckMapper.listEnabledDecks()).thenReturn(List.of(deck));

        reviewService.approveDeck(5L);

        ArgumentCaptor<CofDeck> updated = ArgumentCaptor.forClass(CofDeck.class);
        verify(deckMapper).updateById(updated.capture());
        assertTrue(updated.getValue().enabled);
        assertEquals(ReviewStatus.APPROVED, updated.getValue().reviewStatus);
        assertTrue(deckCatalogService.isDeckPlayable(updated.getValue()));

        when(deckMapper.listEnabledDecks()).thenReturn(List.of(updated.getValue()));
        updated.getValue().cardCount = 1;
        updated.getValue().pmvCount = 1;
        List<CardLibraryDto> summaries = deckCatalogService.listPublicSummaries();
        assertEquals(1, summaries.size());
        assertEquals("u_test_my-deck", summaries.get(0).id);
    }

    @Test
    void approveCardAutoPublishesDeckWhenAllChildrenApproved() {
        CofDeck deck = pendingDeck(7L);
        deck.enabled = false;
        when(cardMapper.selectById(99L)).thenAnswer(inv -> {
            CofCard c = approvedCard(7L, 2);
            c.cardId = 99L;
            return c;
        });
        when(deckMapper.selectById(7L)).thenReturn(deck);
        CofDeckPmv pmv = approvedPmv(7L, 2);
        CofCard card = approvedCard(7L, 2);
        when(deckPmvMapper.listByDeckId(7L)).thenReturn(List.of(pmv));
        when(cardMapper.listByDeckId(7L)).thenReturn(List.of(card));

        reviewService.approveCard(99L);

        ArgumentCaptor<CofDeck> updated = ArgumentCaptor.forClass(CofDeck.class);
        verify(deckMapper).updateById(updated.capture());
        assertTrue(updated.getValue().enabled);
        assertEquals(ReviewStatus.APPROVED, updated.getValue().reviewStatus);
    }

    @Test
    void reconcileFixesApprovedButDisabledDeck() {
        CofDeck deck = pendingDeck(8L);
        deck.reviewStatus = ReviewStatus.APPROVED;
        deck.enabled = false;
        when(deckMapper.selectList(any())).thenReturn(List.of(deck));

        reviewService.reconcileAndRefreshCaches();

        ArgumentCaptor<CofDeck> updated = ArgumentCaptor.forClass(CofDeck.class);
        verify(deckMapper).updateById(updated.capture());
        assertTrue(updated.getValue().enabled);
    }

    private static CofDeck pendingDeck(long id) {
        CofDeck deck = new CofDeck();
        deck.id = id;
        deck.folderName = "u_test_my-deck";
        deck.name = "Mine";
        deck.enabled = false;
        deck.reviewStatus = ReviewStatus.PENDING;
        deck.cardCount = 1;
        deck.pmvCount = 1;
        return deck;
    }

    private static CofDeckPmv approvedPmv(long deckId, int matchId) {
        CofDeckPmv pmv = new CofDeckPmv();
        pmv.pmvId = 10L;
        pmv.deckId = deckId;
        pmv.matchId = matchId;
        pmv.name = "PMV";
        pmv.reviewStatus = ReviewStatus.APPROVED;
        return pmv;
    }

    private static CofCard approvedCard(long deckId, int matchId) {
        CofDeckPmv pmv = approvedPmv(deckId, matchId);
        CofCard card = new CofCard();
        card.cardId = 1L;
        card.deckId = deckId;
        card.pmvId = pmv.pmvId;
        card.shot = "a";
        card.reviewStatus = ReviewStatus.APPROVED;
        card.cardUid = deckId + "/" + matchId + "/a";
        return card;
    }
}
