package com.lumoxu.cof.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lumoxu.cof.domain.entity.CofCard;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.entity.CofDeckPmv;
import com.lumoxu.cof.domain.mapper.CofCardMapper;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.domain.mapper.CofDeckPmvMapper;
import com.lumoxu.cof.engine.Card;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.service.model.CardLibraryDto;
import com.lumoxu.cof.service.model.CatalogBundleDto;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import com.lumoxu.cof.service.redis.RedisKeys;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DeckCatalogService {

    private final CofDeckMapper deckMapper;
    private final CofDeckPmvMapper deckPmvMapper;
    private final CofCardMapper cardMapper;
    private final JsonRedisOps redis;

    public DeckCatalogService(
            CofDeckMapper deckMapper,
            CofDeckPmvMapper deckPmvMapper,
            CofCardMapper cardMapper,
            JsonRedisOps redis) {
        this.deckMapper = deckMapper;
        this.deckPmvMapper = deckPmvMapper;
        this.cardMapper = cardMapper;
        this.redis = redis;
    }

    public List<CardLibraryDto> listPublicSummaries() {
        Optional<List<CardLibraryDto>> cached = redis.get(
                RedisKeys.CACHE_CARD_LIBRARIES,
                new TypeReference<List<CardLibraryDto>>() {
                });
        if (cached.isPresent()) {
            return cached.get();
        }
        List<CardLibraryDto> summaries = deckMapper.listEnabledDecks().stream()
                .map(this::toSummary)
                .sorted(Comparator.comparing(l -> l.name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        redis.set(RedisKeys.CACHE_CARD_LIBRARIES, summaries, Duration.ofHours(24));
        return summaries;
    }

    public List<CardLibraryDto> listFullLibraries() {
        return deckMapper.listEnabledDecks().stream()
                .map(d -> loadDeckBundle(d.id))
                .filter(lib -> lib != null)
                .sorted(Comparator.comparing(l -> l.name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public CardLibraryDto loadDeckBundle(String libraryId) {
        Long deckId = resolveDeckId(libraryId);
        if (deckId == null) {
            return null;
        }
        return loadDeckBundle(deckId);
    }

    public CardLibraryDto loadDeckBundle(Long deckId) {
        String cacheKey = String.valueOf(deckId);
        Optional<CardLibraryDto> cached = redis.get(RedisKeys.deckBundle(cacheKey), CardLibraryDto.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        CofDeck deck = deckMapper.selectById(deckId);
        if (deck == null || Boolean.FALSE.equals(deck.enabled)) {
            return null;
        }
        CardLibraryDto dto = assembleBundle(deck);
        redis.set(RedisKeys.deckBundle(cacheKey), dto, Duration.ofHours(24));
        return dto;
    }

    public void warmRoomCatalog(String roomId, GameSettings settings) {
        List<String> libraryIds = settings != null && settings.libraryIds != null && !settings.libraryIds.isEmpty()
                ? settings.libraryIds
                : deckMapper.listEnabledDecks().stream().map(d -> String.valueOf(d.id)).toList();
        CatalogBundleDto bundle = new CatalogBundleDto();
        for (String libraryId : libraryIds) {
            CardLibraryDto lib = loadDeckBundle(libraryId);
            if (lib != null) {
                bundle.libraries.add(lib);
            }
        }
        redis.set(RedisKeys.roomCatalog(roomId), bundle, null);
    }

    public void attachCatalogToGame(String gameId, String roomId) {
        Optional<CatalogBundleDto> roomCatalog = redis.get(RedisKeys.roomCatalog(roomId), CatalogBundleDto.class);
        CatalogBundleDto gameCatalog = roomCatalog.orElseGet(CatalogBundleDto::new);
        redis.set(RedisKeys.gameCatalog(gameId), gameCatalog, null);
    }

    public void clearGameCatalog(String gameId) {
        redis.delete(RedisKeys.gameCatalog(gameId));
    }

    public void bustCaches() {
        redis.delete(RedisKeys.CACHE_CARD_LIBRARIES);
        deckMapper.listEnabledDecks().forEach(d -> redis.delete(RedisKeys.deckBundle(String.valueOf(d.id))));
    }

    public List<Card> expandedCards(String gameId, GameSettings settings) {
        CatalogBundleDto catalog = redis.get(RedisKeys.gameCatalog(gameId), CatalogBundleDto.class)
                .orElse(null);
        if (catalog == null || catalog.libraries.isEmpty()) {
            catalog = new CatalogBundleDto();
            catalog.libraries.addAll(listFullLibraries());
        }
        return expandFromLibraries(catalog.libraries, settings);
    }

    public List<Card> expandedCardsFromRoom(String roomId, GameSettings settings) {
        CatalogBundleDto catalog = redis.get(RedisKeys.roomCatalog(roomId), CatalogBundleDto.class)
                .orElseGet(() -> {
                    warmRoomCatalog(roomId, settings);
                    return redis.get(RedisKeys.roomCatalog(roomId), CatalogBundleDto.class).orElse(new CatalogBundleDto());
                });
        return expandFromLibraries(catalog.libraries, settings);
    }

    public List<Map<String, Object>> buildPmvIndex() {
        List<Map<String, Object>> index = new ArrayList<>();
        for (CofDeckPmv pmv : deckPmvMapper.selectList(null)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("deckId", pmv.deckId);
            entry.put("pmvId", pmv.pmvId);
            entry.put("name", pmv.name);
            entry.put("author", pmv.author);
            entry.put("description", pmv.description);
            entry.put("link", pmv.link);
            index.add(entry);
        }
        index.sort(Comparator
                .<Map<String, Object>>comparingLong(e -> ((Number) e.get("deckId")).longValue())
                .thenComparingInt(e -> (Integer) e.get("pmvId")));
        return index;
    }

    public Long resolveDeckId(String libraryId) {
        if (libraryId == null || libraryId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(libraryId);
        } catch (NumberFormatException ex) {
            CofDeck deck = deckMapper.findByFolderName(libraryId);
            return deck != null ? deck.id : null;
        }
    }

    private List<Card> expandFromLibraries(List<CardLibraryDto> libraries, GameSettings settings) {
        List<String> selected = settings != null && settings.libraryIds != null
                ? settings.libraryIds
                : List.of();
        List<Card> cards = new ArrayList<>();
        for (CardLibraryDto library : libraries) {
            if (!selected.isEmpty() && !selected.contains(library.id)) {
                continue;
            }
            int copies = settings != null && settings.libraryCopies != null
                    ? settings.libraryCopies.getOrDefault(library.id, 1)
                    : 1;
            for (int copy = 0; copy < copies; copy++) {
                for (CardLibraryDto.CardDto cardDto : library.cards) {
                    Card card = new Card();
                    card.id = copies == 1 ? cardDto.id : cardDto.id + "#copy" + (copy + 1);
                    card.libraryId = cardDto.libraryId;
                    card.fileName = cardDto.fileName;
                    card.pmvId = cardDto.pmvId;
                    card.pmvName = cardDto.pmvName;
                    card.imageUrl = cardDto.imageUrl;
                    card.backUrl = cardDto.backUrl;
                    card.shot = cardDto.shot;
                    cards.add(card);
                }
            }
        }
        return cards;
    }

    private CardLibraryDto assembleBundle(CofDeck deck) {
        Map<Integer, String> pmvNames = deckPmvMapper.listByDeckId(deck.id).stream()
                .collect(Collectors.toMap(p -> p.pmvId, p -> p.name, (a, b) -> a));
        CardLibraryDto dto = toSummary(deck);
        for (CofCard row : cardMapper.listByDeckId(deck.id)) {
            CardLibraryDto.CardDto card = new CardLibraryDto.CardDto();
            card.id = row.cardUid;
            card.libraryId = String.valueOf(deck.id);
            card.fileName = row.fileName;
            card.pmvId = row.pmvId;
            card.pmvName = pmvNames.getOrDefault(row.pmvId, "PMV " + row.pmvId);
            card.shot = row.shot;
            card.imageUrl = row.imageUrl;
            card.backUrl = deck.backUrl;
            dto.cards.add(card);
        }
        return dto;
    }

    private CardLibraryDto toSummary(CofDeck deck) {
        CardLibraryDto dto = new CardLibraryDto();
        dto.id = String.valueOf(deck.id);
        dto.folderName = deck.folderName;
        dto.name = deck.name;
        dto.title = deck.name;
        dto.curator = deck.curator;
        dto.description = deck.description;
        dto.version = deck.version;
        dto.link = deck.link;
        dto.backUrl = deck.backUrl;
        dto.cardCount = deck.cardCount != null ? deck.cardCount : 0;
        dto.pmvCount = deck.pmvCount != null ? deck.pmvCount : 0;
        return dto;
    }
}
