package com.lumoxu.cof.service;

import com.lumoxu.cof.common.catalog.ReviewStatus;
import com.lumoxu.cof.domain.entity.CofCard;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.entity.CofPmv;
import com.lumoxu.cof.domain.mapper.CofCardMapper;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.domain.mapper.CofPmvMapper;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.service.model.CardLibraryDto;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import com.lumoxu.cof.service.redis.RedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeckCatalogServiceTest {

    @Mock
    private CofDeckMapper deckMapper;
    @Mock
    private CofPmvMapper pmvMapper;
    @Mock
    private CofCardMapper cardMapper;
    @Mock
    private JsonRedisOps redis;

    private DeckCatalogService deckCatalogService;

    @BeforeEach
    void setUp() {
        deckCatalogService = new DeckCatalogService(deckMapper, pmvMapper, cardMapper, redis);
    }

    @Test
    void loadDeckBundleBuildsPlayableCards() {
        CofDeck deck = approvedDeck(1L, "Test Deck");
        CofPmv pmv = approvedPmv(10L, "PMV");
        CofCard card = approvedCard(100L, 1L, 10L);
        CofCard pending = approvedCard(101L, 1L, 10L);
        pending.reviewStatus = ReviewStatus.PENDING;
        when(deckMapper.selectById(1L)).thenReturn(deck);
        when(pmvMapper.listAlive()).thenReturn(List.of(pmv));
        when(cardMapper.listAliveByDeckId(1L)).thenReturn(List.of(card, pending));
        when(cardMapper.countAliveByDeckId(1L)).thenReturn(2L);
        when(cardMapper.countDistinctPmvByDeckId(1L)).thenReturn(2L);
        when(redis.get(eq(RedisKeys.deckBundle("1")), eq(CardLibraryDto.class))).thenReturn(Optional.empty());

        CardLibraryDto bundle = deckCatalogService.loadDeckBundle(1L);
        assertEquals("1", bundle.id);
        assertEquals(1, bundle.cardCount);
        assertEquals(1, bundle.pmvCount);
        assertEquals(1, bundle.cards.size());
        assertEquals("100", bundle.cards.get(0).id);
        assertEquals(10L, bundle.cards.get(0).pmvId);
        assertTrue(bundle.cards.get(0).approvedForPlay);
    }

    @Test
    void bustCachesForDeckDropsWaitingRoomCatalogs() {
        when(redis.setMembers(RedisKeys.ROOM_INDEX)).thenReturn(Set.of("room-a", "room-b"));

        deckCatalogService.bustCachesForDeck(1L);

        verify(redis).delete(RedisKeys.CACHE_CARD_LIBRARIES);
        verify(redis).delete(RedisKeys.deckBundle("1"));
        verify(redis).delete(RedisKeys.roomCatalog("room-a"));
        verify(redis).delete(RedisKeys.roomCatalog("room-b"));
    }

    @Test
    void resolveDeckIdAcceptsNumericString() {
        assertEquals(42L, deckCatalogService.resolveDeckId("42"));
    }

    @Test
    void libraryMatchesUsesNumericId() {
        CardLibraryDto lib = new CardLibraryDto();
        lib.id = "7";
        assertTrue(deckCatalogService.isLibrarySelected(lib, List.of("7")));
    }

    private static CofDeck approvedDeck(long id, String name) {
        CofDeck deck = new CofDeck();
        deck.id = id;
        deck.name = name;
        deck.backUrl = "/cards/backs/" + id + ".jpg";
        deck.enabled = true;
        deck.reviewStatus = ReviewStatus.APPROVED;
        deck.createdAt = Instant.now();
        deck.updatedAt = Instant.now();
        return deck;
    }

    private static CofPmv approvedPmv(long id, String name) {
        CofPmv pmv = new CofPmv();
        pmv.id = id;
        pmv.name = name;
        pmv.reviewStatus = ReviewStatus.APPROVED;
        pmv.createdAt = Instant.now();
        pmv.updatedAt = Instant.now();
        return pmv;
    }

    private static CofCard approvedCard(long id, long deckId, long pmvId) {
        CofCard card = new CofCard();
        card.id = id;
        card.deckId = deckId;
        card.pmvId = pmvId;
        card.imageUrl = "/cards/" + id + ".jpg";
        card.reviewStatus = ReviewStatus.APPROVED;
        card.createdAt = Instant.now();
        card.updatedAt = Instant.now();
        return card;
    }
}
