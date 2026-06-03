package com.lumoxu.cof.service;

import com.lumoxu.cof.common.catalog.ReviewStatus;
import com.lumoxu.cof.domain.entity.CofCard;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.entity.CofPmv;
import com.lumoxu.cof.domain.mapper.CofCardMapper;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.domain.mapper.CofPmvMapper;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeckCatalogReviewServiceTest {

    @Mock
    private CofDeckMapper deckMapper;
    @Mock
    private CofPmvMapper pmvMapper;
    @Mock
    private CofCardMapper cardMapper;
    @Mock
    private JsonRedisOps redis;
    @Mock
    private CatalogNameGuard nameGuard;

    private DeckCatalogService deckCatalogService;
    private DeckCatalogReviewService reviewService;

    @BeforeEach
    void setUp() {
        deckCatalogService = new DeckCatalogService(deckMapper, pmvMapper, cardMapper, redis);
        reviewService = new DeckCatalogReviewService(deckMapper, pmvMapper, cardMapper, deckCatalogService, nameGuard);
    }

    @Test
    void approveCardRequiresApprovedDeckAndPmv() {
        CofDeck deck = liveDeck(1L, ReviewStatus.APPROVED);
        CofPmv pmv = livePmv(2L, ReviewStatus.APPROVED);
        CofCard card = liveCard(3L, 1L, 2L, ReviewStatus.PENDING);
        when(cardMapper.selectById(3L)).thenReturn(card);
        when(deckMapper.selectById(1L)).thenReturn(deck);
        when(pmvMapper.selectById(2L)).thenReturn(pmv);

        reviewService.approveCard(3L);

        ArgumentCaptor<CofCard> captor = ArgumentCaptor.forClass(CofCard.class);
        verify(cardMapper).updateById(captor.capture());
        assertEquals(ReviewStatus.APPROVED, captor.getValue().reviewStatus);
    }

    @Test
    void approveDeckRevisionMergesPending() {
        CofDeck deck = liveDeck(5L, ReviewStatus.APPROVED);
        deck.pendingReviewStatus = CatalogRevisionHelper.PENDING_EDIT;
        deck.pendingName = "Renamed Deck";
        when(deckMapper.selectById(5L)).thenReturn(deck);

        reviewService.approveDeck(5L);

        ArgumentCaptor<CofDeck> captor = ArgumentCaptor.forClass(CofDeck.class);
        verify(deckMapper).updateById(captor.capture());
        assertEquals("Renamed Deck", captor.getValue().name);
    }

    private static CofDeck liveDeck(long id, String status) {
        CofDeck deck = new CofDeck();
        deck.id = id;
        deck.name = "Deck";
        deck.backUrl = "/cards/backs/1.jpg";
        deck.enabled = true;
        deck.reviewStatus = status;
        deck.createdAt = Instant.now();
        deck.updatedAt = Instant.now();
        return deck;
    }

    private static CofPmv livePmv(long id, String status) {
        CofPmv pmv = new CofPmv();
        pmv.id = id;
        pmv.name = "PMV";
        pmv.reviewStatus = status;
        pmv.createdAt = Instant.now();
        pmv.updatedAt = Instant.now();
        return pmv;
    }

    private static CofCard liveCard(long id, long deckId, long pmvId, String status) {
        CofCard card = new CofCard();
        card.id = id;
        card.deckId = deckId;
        card.pmvId = pmvId;
        card.imageUrl = "/cards/x.jpg";
        card.reviewStatus = status;
        card.createdAt = Instant.now();
        card.updatedAt = Instant.now();
        return card;
    }
}
