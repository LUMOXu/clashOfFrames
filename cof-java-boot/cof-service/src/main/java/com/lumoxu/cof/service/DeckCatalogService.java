package com.lumoxu.cof.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lumoxu.cof.common.catalog.ReviewStatus;
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
        if (viewerClientId == null || viewerClientId.isBlank()) {
            return deckMapper.listEnabledDecks();
        }
        List<CofDeck> decks = new ArrayList<>();
        for (CofDeck deck : deckMapper.listEnabledDecks()) {
            decks.add(deck);
        }
        for (CofDeck deck : deckMapper.listBySubmitter(viewerClientId)) {
            if (!decks.stream().anyMatch(d -> d.id.equals(deck.id))) {
                decks.add(deck);
            }
        }
        return decks;
    }

    public boolean isDeckPlayable(CofDeck deck) {
        return deck != null
                && Boolean.TRUE.equals(deck.enabled)
                && ReviewStatus.isApproved(deck.reviewStatus);
    }

    public boolean isDeckVisibleInViewer(CofDeck deck, String viewerClientId) {
        if (deck == null) {
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
                : deckMapper.listEnabledDecks().stream().map(d -> String.valueOf(d.id)).toList();
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
            CofDeck deck = pmv.deckId != null ? deckMapper.selectById(pmv.deckId) : null;
            if (deck == null || !ReviewStatus.isApproved(deck.reviewStatus)) {
                continue;
            }
            if (!ReviewStatus.isApproved(pmv.reviewStatus)) {
                continue;
            }
            Map<String, Object> entry = new HashMap<>();
            entry.put("deckId", pmv.deckId);
            entry.put("pmvId", pmv.pmvId);
            entry.put("name", pmv.name);
            entry.put("author", pmv.author);
            entry.put("description", pmv.description);
            entry.put("link", pmv.link);
            entry.put("libraryName", deck.name);
            entry.put("libraryId", deck.folderName != null ? deck.folderName : String.valueOf(deck.id));
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

    /**
     * Matches {@code settings.libraryIds} against a catalog entry. IDs may be numeric deck ids
     * (e.g. {@code "1"}) or public folder names (e.g. {@code 基础包@ThePMVPanel'25}).
     */
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
        if (library.folderName != null) {
            direct = copies.get(library.folderName);
            if (direct != null) {
                return Math.max(1, direct);
            }
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
        if (library.folderName != null && selectedId.equals(library.folderName)) {
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
        return assembleBundle(deck, null);
    }

    private CardLibraryDto assembleBundle(CofDeck deck, String viewerClientId) {
        Map<Integer, String> pmvNames = deckPmvMapper.listByDeckId(deck.id).stream()
                .filter(p -> ReviewStatus.isVisibleToUser(p.reviewStatus, p.submitterClientId, viewerClientId))
                .collect(Collectors.toMap(p -> p.pmvId, p -> p.name, (a, b) -> a));
        CardLibraryDto dto = toSummary(deck);
        dto.reviewStatus = deck.reviewStatus;
        dto.submitterClientId = deck.submitterClientId;
        for (CofCard row : cardMapper.listByDeckId(deck.id)) {
            if (!ReviewStatus.isVisibleToUser(row.reviewStatus, row.submitterClientId, viewerClientId)) {
                continue;
            }
            if (!pmvNames.containsKey(row.pmvId)) {
                continue;
            }
            CardLibraryDto.CardDto card = new CardLibraryDto.CardDto();
            card.id = row.cardUid;
            card.libraryId = publicLibraryId(deck);
            card.fileName = row.fileName;
            card.pmvId = row.pmvId;
            card.pmvName = pmvNames.getOrDefault(row.pmvId, "PMV " + row.pmvId);
            card.shot = row.shot;
            card.imageUrl = row.imageUrl;
            card.backUrl = deck.backUrl;
            card.approvedForPlay = isDeckPlayable(deck)
                    && ReviewStatus.isApproved(row.reviewStatus)
                    && ReviewStatus.isApproved(pmvReviewStatus(deck.id, row.pmvId));
            dto.cards.add(card);
        }
        return dto;
    }

    private String pmvReviewStatus(long deckId, int pmvId) {
        CofDeckPmv pmv = deckPmvMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CofDeckPmv>()
                        .eq("deck_id", deckId)
                        .eq("pmv_id", pmvId)
                        .last("LIMIT 1"));
        return pmv != null ? pmv.reviewStatus : ReviewStatus.PENDING;
    }

    private static String publicLibraryId(CofDeck deck) {
        if (deck.folderName != null && !deck.folderName.isBlank()) {
            return deck.folderName;
        }
        return String.valueOf(deck.id);
    }

    private CardLibraryDto toSummary(CofDeck deck) {
        CardLibraryDto dto = new CardLibraryDto();
        dto.id = publicLibraryId(deck);
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
