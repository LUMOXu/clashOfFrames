package com.lumoxu.cof.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lumoxu.cof.common.catalog.ReviewStatus;
import com.lumoxu.cof.domain.entity.CofCard;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.entity.CofPmv;
import com.lumoxu.cof.domain.mapper.CofCardMapper;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.domain.mapper.CofPmvMapper;
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
    private final CofPmvMapper pmvMapper;
    private final CofCardMapper cardMapper;
    private final JsonRedisOps redis;

    public DeckCatalogService(
            CofDeckMapper deckMapper,
            CofPmvMapper pmvMapper,
            CofCardMapper cardMapper,
            JsonRedisOps redis) {
        this.deckMapper = deckMapper;
        this.pmvMapper = pmvMapper;
        this.cardMapper = cardMapper;
        this.redis = redis;
    }

    public List<CardLibraryDto> listPublicSummaries() {
        Optional<List<CardLibraryDto>> cached = redis.get(
                RedisKeys.CACHE_CARD_LIBRARIES,
                new TypeReference<List<CardLibraryDto>>() {});
        if (cached.isPresent()) {
            return cached.get();
        }
        List<CardLibraryDto> summaries = deckMapper.listPlayableDecks().stream()
                .map(this::toSummary)
                .sorted(Comparator.comparing(l -> l.name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        redis.set(RedisKeys.CACHE_CARD_LIBRARIES, summaries, Duration.ofHours(24));
        return summaries;
    }

    public List<CardLibraryDto> listFullLibraries() {
        return listFullLibrariesForViewer(null);
    }

    public List<CardLibraryDto> listFullLibrariesForViewer(String viewerClientId) {
        return listPlayableDecks(viewerClientId).stream()
                .map(d -> loadDeckBundle(d.id, viewerClientId))
                .filter(lib -> lib != null)
                .sorted(Comparator.comparing(l -> l.name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public List<CofDeck> listPlayableDecks(String viewerClientId) {
        List<CofDeck> decks = new ArrayList<>(deckMapper.listPlayableDecks());
        if (viewerClientId == null || viewerClientId.isBlank()) {
            return decks;
        }
        for (CofDeck deck : deckMapper.listBySubmitter(viewerClientId)) {
            if (decks.stream().noneMatch(d -> d.id.equals(deck.id))) {
                decks.add(deck);
            }
        }
        return decks;
    }

    public boolean isDeckPlayable(CofDeck deck) {
        return CatalogRevisionHelper.isPlayableDeck(deck);
    }

    public boolean isDeckVisibleInViewer(CofDeck deck, String viewerClientId) {
        if (deck == null || !CatalogRevisionHelper.isAlive(deck)) {
            return false;
        }
        if (isDeckPlayable(deck)) {
            return true;
        }
        return ReviewStatus.isVisibleToUser(deck.reviewStatus, deck.submitterClientId, viewerClientId);
    }

    public CardLibraryDto loadDeckBundle(String libraryId) {
        Long deckId = resolveDeckId(libraryId);
        if (deckId == null) {
            return null;
        }
        return loadDeckBundle(deckId);
    }

    public CardLibraryDto loadDeckBundle(Long deckId) {
        return loadDeckBundle(deckId, null);
    }

    public CardLibraryDto loadDeckBundle(Long deckId, String viewerClientId) {
        String cacheKey = viewerClientId == null || viewerClientId.isBlank()
                ? String.valueOf(deckId)
                : deckId + ":viewer:" + viewerClientId;
        Optional<CardLibraryDto> cached = redis.get(RedisKeys.deckBundle(cacheKey), CardLibraryDto.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        CofDeck deck = deckMapper.selectById(deckId);
        if (deck == null || !isDeckVisibleInViewer(deck, viewerClientId)) {
            return null;
        }
        CardLibraryDto dto = assembleBundle(deck, viewerClientId);
        if (viewerClientId == null || viewerClientId.isBlank()) {
            redis.set(RedisKeys.deckBundle(cacheKey), dto, Duration.ofHours(24));
        }
        return dto;
    }

    public void warmRoomCatalog(String roomId, GameSettings settings) {
        List<String> libraryIds = settings != null && settings.libraryIds != null && !settings.libraryIds.isEmpty()
                ? settings.libraryIds
                : deckMapper.listPlayableDecks().stream().map(d -> String.valueOf(d.id)).toList();
        CatalogBundleDto bundle = new CatalogBundleDto();
        for (String libraryId : libraryIds) {
            CardLibraryDto lib = loadDeckBundle(libraryId);
            if (lib != null && lib.cards != null && !lib.cards.isEmpty()) {
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
        redis.deleteByPrefix(RedisKeys.DECK_BUNDLE_PREFIX);
    }

    public void bustCachesForDeck(Long deckId) {
        if (deckId == null) {
            return;
        }
        redis.delete(RedisKeys.CACHE_CARD_LIBRARIES);
        redis.delete(RedisKeys.deckBundle(String.valueOf(deckId)));
        redis.deleteByPrefix(RedisKeys.deckBundle(deckId + ":viewer:"));
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
                    return redis.get(RedisKeys.roomCatalog(roomId), CatalogBundleDto.class)
                            .orElse(new CatalogBundleDto());
                });
        return expandFromLibraries(catalog.libraries, settings);
    }

    public Map<String, Object> findApprovedPmvById(long pmvId) {
        CofPmv pmv = pmvMapper.selectById(pmvId);
        if (pmv == null || !CatalogRevisionHelper.isAlive(pmv) || !CatalogRevisionHelper.isApprovedLive(pmv)) {
            return null;
        }
        Map<String, Object> entry = new HashMap<>();
        entry.put("pmvId", pmv.id);
        entry.put("name", pmv.name);
        entry.put("author", pmv.author);
        entry.put("description", pmv.description);
        entry.put("link", pmv.link);
        entry.put("submitterClientId", pmv.submitterClientId);
        return entry;
    }

    public List<Map<String, Object>> buildPmvIndex() {
        List<Map<String, Object>> index = new ArrayList<>();
        for (CofPmv pmv : pmvMapper.listApproved()) {
            if (!CatalogRevisionHelper.isAlive(pmv)) {
                continue;
            }
            Map<String, Object> entry = new HashMap<>();
            entry.put("pmvId", pmv.id);
            entry.put("name", pmv.name);
            entry.put("author", pmv.author);
            entry.put("description", pmv.description);
            entry.put("link", pmv.link);
            entry.put("submitterClientId", pmv.submitterClientId);
            index.add(entry);
        }
        index.sort(Comparator.comparingLong(e -> ((Number) e.get("pmvId")).longValue()));
        return index;
    }

    public Long resolveDeckId(String libraryId) {
        if (libraryId == null || libraryId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(libraryId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public boolean isLibrarySelected(CardLibraryDto library, List<String> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            return true;
        }
        for (String selectedId : selectedIds) {
            if (libraryMatches(library, selectedId)) {
                return true;
            }
        }
        return false;
    }

    public int libraryCopyCount(CardLibraryDto library, GameSettings settings) {
        if (settings == null || settings.libraryCopies == null || settings.libraryCopies.isEmpty()) {
            return 1;
        }
        Map<String, Integer> copies = settings.libraryCopies;
        Integer direct = copies.get(library.id);
        if (direct != null) {
            return Math.max(1, direct);
        }
        Long deckId = resolveDeckId(library.id);
        if (deckId != null) {
            direct = copies.get(String.valueOf(deckId));
            if (direct != null) {
                return Math.max(1, direct);
            }
        }
        for (Map.Entry<String, Integer> entry : copies.entrySet()) {
            if (libraryMatches(library, entry.getKey())) {
                return Math.max(1, entry.getValue());
            }
        }
        return 1;
    }

    private boolean libraryMatches(CardLibraryDto library, String selectedId) {
        if (selectedId == null || selectedId.isBlank() || library == null) {
            return false;
        }
        if (selectedId.equals(library.id)) {
            return true;
        }
        Long selectedDeckId = resolveDeckId(selectedId);
        Long libraryDeckId = resolveDeckId(library.id);
        return selectedDeckId != null && selectedDeckId.equals(libraryDeckId);
    }

    private List<Card> expandFromLibraries(List<CardLibraryDto> libraries, GameSettings settings) {
        List<String> selected = settings != null && settings.libraryIds != null
                ? settings.libraryIds
                : List.of();
        List<Card> cards = new ArrayList<>();
        for (CardLibraryDto library : libraries) {
            if (!isLibrarySelected(library, selected)) {
                continue;
            }
            int copies = libraryCopyCount(library, settings);
            for (int copy = 0; copy < copies; copy++) {
                for (CardLibraryDto.CardDto cardDto : library.cards) {
                    if (cardDto.approvedForPlay == Boolean.FALSE) {
                        continue;
                    }
                    Card card = new Card();
                    card.id = copies == 1 ? cardDto.id : cardDto.id + "#copy" + (copy + 1);
                    card.libraryId = cardDto.libraryId;
                    card.pmvId = (int) cardDto.pmvId;
                    card.pmvName = cardDto.pmvName;
                    card.imageUrl = cardDto.imageUrl;
                    card.backUrl = cardDto.backUrl;
                    cards.add(card);
                }
            }
        }
        return cards;
    }

    private CardLibraryDto assembleBundle(CofDeck deck, String viewerClientId) {
        Map<Long, CofPmv> pmvById = new HashMap<>();
        for (CofPmv pmv : pmvMapper.listAlive()) {
            if (ReviewStatus.isVisibleToUser(pmv.reviewStatus, pmv.submitterClientId, viewerClientId)) {
                pmvById.put(pmv.id, pmv);
            }
        }
        CardLibraryDto dto = toSummary(deck);
        dto.reviewStatus = deck.reviewStatus;
        dto.pendingReviewStatus = deck.pendingReviewStatus;
        dto.submitterClientId = deck.submitterClientId;
        for (CofCard row : cardMapper.listAliveByDeckId(deck.id)) {
            if (!ReviewStatus.isVisibleToUser(row.reviewStatus, row.submitterClientId, viewerClientId)) {
                continue;
            }
            CofPmv pmv = pmvById.get(row.pmvId);
            if (pmv == null) {
                continue;
            }
            CardLibraryDto.CardDto card = new CardLibraryDto.CardDto();
            card.id = String.valueOf(row.id);
            card.libraryId = publicLibraryId(deck);
            card.pmvId = pmv.id;
            card.pmvName = pmv.name;
            card.cardName = row.name;
            card.cardDescription = row.description;
            card.imageUrl = row.imageUrl;
            card.backUrl = deck.backUrl;
            card.pendingReviewStatus = row.pendingReviewStatus;
            card.approvedForPlay = CatalogRevisionHelper.isPlayableCard(deck, pmv, row);
            dto.cards.add(card);
        }
        return dto;
    }

    private static String publicLibraryId(CofDeck deck) {
        return String.valueOf(deck.id);
    }

    private CardLibraryDto toSummary(CofDeck deck) {
        CardLibraryDto dto = new CardLibraryDto();
        dto.id = publicLibraryId(deck);
        dto.name = deck.name;
        dto.title = deck.name;
        dto.description = deck.description;
        dto.backUrl = deck.backUrl;
        dto.cardCount = (int) cardMapper.countAliveByDeckId(deck.id);
        dto.pmvCount = (int) cardMapper.countDistinctPmvByDeckId(deck.id);
        return dto;
    }
}
