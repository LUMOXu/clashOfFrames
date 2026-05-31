package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.domain.entity.CofCard;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.mapper.CofCardMapper;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.domain.mapper.CofDeckPmvMapper;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.service.model.CardLibraryDto;
import com.lumoxu.cof.service.model.CatalogBundleDto;
import com.lumoxu.cof.service.redis.RedisKeys;
import com.lumoxu.cof.service.support.TestRedisSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeckCatalogServiceTest {

    @Mock
    private CofDeckMapper deckMapper;
    @Mock
    private CofDeckPmvMapper deckPmvMapper;
    @Mock
    private CofCardMapper cardMapper;

    private DeckCatalogService deckCatalogService;
    private com.lumoxu.cof.service.redis.JsonRedisOps redis;

    @BeforeEach
    void setUp() {
        redis = TestRedisSupport.memoryJsonRedis(new ObjectMapper());
        deckCatalogService = new DeckCatalogService(deckMapper, deckPmvMapper, cardMapper, redis);
    }

    @Test
    void warmRoomCatalogWritesRedisKey() {
        CofDeck deck = new CofDeck();
        deck.id = 1L;
        deck.name = "Test";
        deck.folderName = "test-deck";
        deck.backUrl = "/cards/1/back.png";
        deck.cardCount = 1;
        deck.pmvCount = 1;
        deck.enabled = true;
        when(deckMapper.selectById(1L)).thenReturn(deck);
        when(deckPmvMapper.listByDeckId(1L)).thenReturn(List.of());
        CofCard card = new CofCard();
        card.deckId = 1L;
        card.pmvId = 1;
        card.cardId = "a";
        card.shot = "a";
        card.imageUrl = "/cards/1/1/a.jpg";
        card.cardUid = "1/1/a";
        when(cardMapper.listByDeckId(1L)).thenReturn(List.of(card));

        GameSettings settings = GameSettings.defaultSettings();
        settings.libraryIds = List.of("1");
        deckCatalogService.warmRoomCatalog("room-1", settings);

        CatalogBundleDto bundle = redis.get(RedisKeys.roomCatalog("room-1"), CatalogBundleDto.class).orElseThrow();
        assertEquals(1, bundle.libraries.size());
        assertEquals("1", bundle.libraries.get(0).id);
    }

    @Test
    void loadDeckBundleCachesInRedis() {
        CofDeck deck = new CofDeck();
        deck.id = 2L;
        deck.name = "Cached";
        deck.folderName = "cached-deck";
        deck.backUrl = "/cards/2/back.png";
        deck.enabled = true;
        when(deckMapper.selectById(2L)).thenReturn(deck);
        when(deckPmvMapper.listByDeckId(2L)).thenReturn(List.of());
        when(cardMapper.listByDeckId(2L)).thenReturn(List.of());

        CardLibraryDto first = deckCatalogService.loadDeckBundle(2L);
        CardLibraryDto second = deckCatalogService.loadDeckBundle(2L);
        assertEquals(first.id, second.id);
        assertTrue(redis.get(RedisKeys.deckBundle("2"), CardLibraryDto.class).isPresent());
    }

    @Test
    void expandedCardsFromRoomUsesCachedLibraries() {
        CatalogBundleDto catalog = new CatalogBundleDto();
        CardLibraryDto lib = new CardLibraryDto();
        lib.id = "1";
        CardLibraryDto.CardDto card = new CardLibraryDto.CardDto();
        card.id = "1/1/a";
        card.libraryId = "1";
        card.pmvId = 1;
        card.imageUrl = "/cards/1/1/a.jpg";
        lib.cards.add(card);
        catalog.libraries.add(lib);
        redis.set(RedisKeys.roomCatalog("room-x"), catalog, null);

        GameSettings settings = GameSettings.defaultSettings();
        settings.libraryIds = List.of("1");
        assertFalse(deckCatalogService.expandedCardsFromRoom("room-x", settings).isEmpty());
    }

    @Test
    void resolveDeckIdAcceptsFolderName() {
        CofDeck deck = new CofDeck();
        deck.id = 3L;
        deck.folderName = "legacy-folder";
        when(deckMapper.findByFolderName("legacy-folder")).thenReturn(deck);
        assertEquals(3L, deckCatalogService.resolveDeckId("legacy-folder"));
    }
}
