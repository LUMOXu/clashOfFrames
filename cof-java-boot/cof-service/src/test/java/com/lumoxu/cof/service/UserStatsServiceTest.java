package com.lumoxu.cof.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.domain.entity.CofMatchHistory;
import com.lumoxu.cof.domain.entity.CofUserStats;
import com.lumoxu.cof.domain.mapper.CofMatchHistoryMapper;
import com.lumoxu.cof.domain.mapper.CofUserStatsMapper;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameCore;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.engine.Room;
import com.lumoxu.cof.service.support.TestRedisSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserStatsServiceTest {

    @Mock
    private CofUserStatsMapper statsMapper;

    @Mock
    private CofMatchHistoryMapper matchHistoryMapper;

    private UserStatsService userStatsService;
    private ObjectMapper objectMapper;
    private Map<String, CofUserStats> persistedStats;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        persistedStats = new HashMap<>();
        when(statsMapper.selectById(any(String.class)))
                .thenAnswer(invocation -> persistedStats.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            CofUserStats stats = invocation.getArgument(0);
            persistedStats.put(stats.statsId, stats);
            return 1;
        }).when(statsMapper).insert(any(CofUserStats.class));
        userStatsService = new UserStatsService(
                statsMapper,
                matchHistoryMapper,
                TestRedisSupport.memoryJsonRedis(objectMapper),
                objectMapper);
    }

    @Test
    void recordFinishedGamePersistsStatsAndHistory() {
        Room room = new Room();
        room.id = "room-stats";
        room.settings = GameSettings.defaultSettings();
        Player human = new Player();
        human.clientId = "human-stats";
        human.username = "Human";
        human.statsId = "human-stats";
        human.rank = 1;
        Game game = GameCore.createGame(room, List.of(human), List.of(new com.lumoxu.cof.engine.Card(), new com.lumoxu.cof.engine.Card()));
        GameCore.startPlaying(game, 1000L);
        game.status = "finished";
        game.winnerId = "human-stats";
        game.finishedAt = 2000L;

        when(matchHistoryMapper.insert(any(CofMatchHistory.class))).thenReturn(1);

        assertTrue(userStatsService.recordFinishedGame(game));
        verify(statsMapper).insert(any(CofUserStats.class));
        verify(matchHistoryMapper).insert(argThat((CofMatchHistory row) -> {
            assertNotNull(row.logText);
            return row.gameId != null && !row.gameId.isBlank();
        }));
        assertTrue(Boolean.TRUE.equals(game.statsSaved));
    }

    @Test
    void ensureStatsInsertsWhenMissing() {
        CofUserStats stats = userStatsService.ensureStats("u1", "Alice", false, null);
        assertEquals("u1", stats.statsId);
        verify(statsMapper).insert(any(CofUserStats.class));
    }

    @Test
    void qualifyingGodWinPersistsFirstAwardValues() throws Exception {
        Game game = godGame(true);
        CofUserStats winnerStats = existingStats("winner", "Winner");
        persistedStats.put(winnerStats.statsId, winnerStats);
        when(matchHistoryMapper.insert(any(CofMatchHistory.class))).thenReturn(1);
        allowAtomicGodClaim();

        assertTrue(userStatsService.recordFinishedGame(game));

        assertEquals(game.finishedAt, winnerStats.godDefeatedAt);
        assertEquals(game.id, winnerStats.godRewardGameId);
        assertEquals("winner", game.godSlayerAwardWinnerId);
        assertTrue(player(game, "winner").godSlayer);
        assertEquals(1, objectMapper.readTree(winnerStats.defeatedComputers).path("computer_god").asInt());
        verify(statsMapper).update(
                argThat(update -> update != null
                        && "winner".equals(update.statsId)
                        && game.finishedAt.equals(update.godDefeatedAt)
                        && game.id.equals(update.godRewardGameId)
                        && update.updatedAt != null),
                argThat((Wrapper<CofUserStats> wrapper) -> {
                    String condition = wrapper.getSqlSegment();
                    return condition.contains("stats_id")
                            && condition.contains("god_defeated_at")
                            && condition.contains("IS NULL");
                }));
        verify(statsMapper, atLeastOnce()).update(
                isNull(),
                argThat((Wrapper<CofUserStats> wrapper) -> {
                    if (!(wrapper instanceof UpdateWrapper<?> updateWrapper)) {
                        return false;
                    }
                    String sqlSet = updateWrapper.getSqlSet();
                    return sqlSet != null
                            && sqlSet.contains("defeated_computers")
                            && !sqlSet.contains("god_defeated_at")
                            && !sqlSet.contains("god_reward_game_id");
                }));
    }

    @Test
    void onlyWinnerReceivesGodAward() {
        Game game = godGame(true);
        CofUserStats winnerStats = existingStats("winner", "Winner");
        CofUserStats otherStats = existingStats("other", "Other");
        persistedStats.put(winnerStats.statsId, winnerStats);
        persistedStats.put(otherStats.statsId, otherStats);
        when(matchHistoryMapper.insert(any(CofMatchHistory.class))).thenReturn(1);
        allowAtomicGodClaim();

        assertTrue(userStatsService.recordFinishedGame(game));

        assertEquals(game.finishedAt, winnerStats.godDefeatedAt);
        assertNull(otherStats.godDefeatedAt);
        assertTrue(player(game, "winner").godSlayer);
        assertFalse(player(game, "other").godSlayer);
    }

    @Test
    void repeatedSaveDoesNotGrantAwardTwice() {
        Game game = godGame(false);
        CofUserStats winnerStats = existingStats("winner", "Winner");
        persistedStats.put(winnerStats.statsId, winnerStats);
        when(matchHistoryMapper.insert(any(CofMatchHistory.class))).thenReturn(1);
        allowAtomicGodClaim();

        assertTrue(userStatsService.recordFinishedGame(game));
        Long firstAwardAt = winnerStats.godDefeatedAt;
        String firstRewardGameId = winnerStats.godRewardGameId;

        game.finishedAt = game.finishedAt + 1000;
        assertFalse(userStatsService.recordFinishedGame(game));

        assertEquals(firstAwardAt, winnerStats.godDefeatedAt);
        assertEquals(firstRewardGameId, winnerStats.godRewardGameId);
        verify(matchHistoryMapper, times(1)).insert(any(CofMatchHistory.class));
    }

    @Test
    void existingGodAwardIsNotReplacedOrReannounced() {
        Game game = godGame(false);
        CofUserStats winnerStats = existingStats("winner", "Winner");
        winnerStats.godDefeatedAt = 111L;
        winnerStats.godRewardGameId = "old-game";
        persistedStats.put(winnerStats.statsId, winnerStats);
        when(matchHistoryMapper.insert(any(CofMatchHistory.class))).thenReturn(1);

        assertTrue(userStatsService.recordFinishedGame(game));

        assertEquals(111L, winnerStats.godDefeatedAt);
        assertEquals("old-game", winnerStats.godRewardGameId);
        assertNull(game.godSlayerAwardWinnerId);
        assertFalse(player(game, "winner").godSlayer);
    }

    @Test
    void lostAtomicGodClaimDoesNotOverwriteFirstRewardOrAnnounceAward() throws Exception {
        Game game = godGame(false);
        CofUserStats winnerStats = existingStats("winner", "Winner");
        persistedStats.put(winnerStats.statsId, winnerStats);
        when(matchHistoryMapper.insert(any(CofMatchHistory.class))).thenReturn(1);
        doAnswer(invocation -> {
            CofUserStats update = invocation.getArgument(0);
            if (update == null) {
                return 1;
            }
            winnerStats.godDefeatedAt = 1_500L;
            winnerStats.godRewardGameId = "first-game";
            return 0;
        }).when(statsMapper).update(nullable(CofUserStats.class), any());

        assertTrue(userStatsService.recordFinishedGame(game));

        assertEquals(1_500L, winnerStats.godDefeatedAt);
        assertEquals("first-game", winnerStats.godRewardGameId);
        assertNull(game.godSlayerAwardWinnerId);
        assertFalse(player(game, "winner").godSlayer);
        assertEquals(0, objectMapper.readTree(winnerStats.defeatedComputers).path("computer_god").asInt());
    }

    @Test
    void nonGodComputerDefeatCountingIsPreserved() throws Exception {
        Game game = godGame(false);
        Player computer = player(game, "god");
        computer.computerId = "computer_easy";
        CofUserStats winnerStats = existingStats("winner", "Winner");
        persistedStats.put(winnerStats.statsId, winnerStats);
        when(matchHistoryMapper.insert(any(CofMatchHistory.class))).thenReturn(1);

        assertTrue(userStatsService.recordFinishedGame(game));

        assertEquals(1, objectMapper.readTree(winnerStats.defeatedComputers).path("computer_easy").asInt());
        assertNull(winnerStats.godDefeatedAt);
    }

    private static Game godGame(boolean includeOtherHuman) {
        Room room = new Room();
        room.id = "god-room";
        room.settings = new GameSettings();
        room.settings.libraryIds = new ArrayList<>(List.of("1", "2"));
        room.settings.libraryCopies = new HashMap<>(Map.of("1", 7, "2", 11));

        Player winner = gamePlayer("winner", "Winner", false, null);
        Player god = gamePlayer("god", "GOD", true, "computer_god");
        List<Player> players = new ArrayList<>();
        players.add(winner);
        if (includeOtherHuman) {
            players.add(gamePlayer("other", "Other", false, null));
        }
        players.add(god);

        List<com.lumoxu.cof.engine.Card> cards = new ArrayList<>();
        for (int i = 0; i < players.size() * 3; i++) {
            cards.add(new com.lumoxu.cof.engine.Card());
        }
        Game game = GameCore.createGame(room, players, cards, 1_000L, () -> 0.25);
        GameCore.startPlaying(game, 1_000L);
        game.status = "finished";
        game.winnerId = "winner";
        game.finishedAt = 2_000L;
        player(game, "winner").rank = 1;
        if (includeOtherHuman) {
            player(game, "other").rank = 2;
        }
        player(game, "god").rank = includeOtherHuman ? 3 : 2;
        player(game, "god").eliminated = true;
        return game;
    }

    private static Player gamePlayer(
            String clientId,
            String username,
            boolean isComputer,
            String computerId) {
        Player player = new Player();
        player.clientId = clientId;
        player.username = username;
        player.statsId = clientId;
        player.isComputer = isComputer;
        player.computerId = computerId;
        return player;
    }

    private static Player player(Game game, String clientId) {
        return game.players.stream()
                .filter(player -> clientId.equals(player.clientId))
                .findFirst()
                .orElseThrow();
    }

    private static CofUserStats existingStats(String statsId, String username) {
        CofUserStats stats = new CofUserStats();
        stats.statsId = statsId;
        stats.username = username;
        stats.gamesPlayed = 0;
        stats.wins = 0;
        stats.rings = 0;
        stats.correctRings = 0;
        stats.wrongRings = 0;
        stats.wonCards = 0;
        stats.totalRank = 0;
        stats.isComputer = false;
        stats.defeatedComputers = "{}";
        stats.history = "[]";
        return stats;
    }

    private void allowAtomicGodClaim() {
        doAnswer(invocation -> {
            CofUserStats update = invocation.getArgument(0);
            if (update == null) {
                return 1;
            }
            CofUserStats persisted = persistedStats.get(update.statsId);
            if (persisted == null || persisted.godDefeatedAt != null) {
                return 0;
            }
            persisted.godDefeatedAt = update.godDefeatedAt;
            persisted.godRewardGameId = update.godRewardGameId;
            persisted.updatedAt = update.updatedAt;
            return 1;
        }).when(statsMapper).update(nullable(CofUserStats.class), any());
    }
}
