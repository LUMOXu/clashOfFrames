package com.lumoxu.cof.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.domain.entity.CofMatchHistory;
import com.lumoxu.cof.domain.entity.CofUserStats;
import com.lumoxu.cof.domain.mapper.CofMatchHistoryMapper;
import com.lumoxu.cof.domain.mapper.CofUserStatsMapper;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameCore;
import com.lumoxu.cof.engine.GameLogTextFormatter;
import com.lumoxu.cof.engine.GameSummary;
import com.lumoxu.cof.engine.PlayerStats;
import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import com.lumoxu.cof.service.redis.RedisKeys;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserStatsService {

    private static final String GOD_COMPUTER_ID = "computer_god";

    private final CofUserStatsMapper statsMapper;
    private final CofMatchHistoryMapper matchHistoryMapper;
    private final JsonRedisOps redis;
    private final ObjectMapper objectMapper;

    public UserStatsService(
            CofUserStatsMapper statsMapper,
            CofMatchHistoryMapper matchHistoryMapper,
            JsonRedisOps redis,
            ObjectMapper objectMapper) {
        this.statsMapper = statsMapper;
        this.matchHistoryMapper = matchHistoryMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public CofUserStats ensureStats(String statsId, String username, boolean isComputer, String computerId) {
        CofUserStats stats = statsMapper.selectById(statsId);
        long now = System.currentTimeMillis();
        if (stats == null) {
            stats = new CofUserStats();
            stats.statsId = statsId;
            stats.username = username;
            stats.gamesPlayed = 0;
            stats.wins = 0;
            stats.rings = 0;
            stats.correctRings = 0;
            stats.wrongRings = 0;
            stats.wonCards = 0;
            stats.totalRank = 0;
            stats.isComputer = isComputer;
            stats.computerId = computerId;
            stats.defeatedComputers = "{}";
            stats.history = "[]";
            stats.updatedAt = now;
            statsMapper.insert(stats);
        } else if (username != null && !username.equals(stats.username)) {
            stats.username = username;
            stats.updatedAt = now;
            statsMapper.updateById(stats);
        }
        redis.delete(RedisKeys.CACHE_LEADERBOARD);
        return stats;
    }

    /**
     * Persists match history and player stats when a game ends (mirrors old server.js saveGameStats).
     *
     * @return true if stats were written (caller should save the game to Redis)
     */
    public boolean recordFinishedGame(Game game) {
        if (game == null || !"finished".equals(game.status) || Boolean.TRUE.equals(game.statsSaved)) {
            return false;
        }
        game.statsSaved = true;
        GameSummary summary = GameCore.summarizeGameForStats(game);
        try {
            CofMatchHistory match = new CofMatchHistory();
            match.gameId = summary.gameId;
            match.roomId = summary.roomId;
            match.playedAt = summary.at;
            Map<String, Object> matchPayload = new HashMap<>();
            matchPayload.put("gameId", summary.gameId);
            matchPayload.put("roomId", summary.roomId);
            matchPayload.put("at", summary.at);
            matchPayload.put("playerCount", summary.playerCount);
            matchPayload.put("playCount", summary.playCount);
            matchPayload.put("bellCount", summary.bellCount);
            matchPayload.put("successBellCount", summary.successBellCount);
            matchPayload.put("failBellCount", summary.failBellCount);
            matchPayload.put("winnerId", summary.winnerId);
            matchPayload.put("averageRoundLength", summary.averageRoundLength);
            match.summary = objectMapper.writeValueAsString(matchPayload);
            match.logText = GameLogTextFormatter.format(game);
            matchHistoryMapper.insert(match);

            for (GameSummary.SummaryPlayer entry : summary.players) {
                applySummaryToPlayer(entry, summary);
            }
            updateComputerDefeatStats(summary);
            redis.delete(RedisKeys.CACHE_LEADERBOARD);
            return true;
        } catch (Exception ex) {
            game.statsSaved = false;
            throw new IllegalStateException("Failed to persist game stats", ex);
        }
    }

    public Map<String, Object> profileFor(String statsId) {
        CofUserStats stats = statsMapper.selectById(statsId);
        if (stats == null) {
            stats = ensureStats(statsId, statsId, false, null);
        }
        Map<String, Object> profile = new HashMap<>();
        profile.put("statsId", stats.statsId);
        profile.put("username", stats.username);
        profile.put("gamesPlayed", stats.gamesPlayed);
        profile.put("wins", stats.wins);
        profile.put("rings", stats.rings);
        profile.put("correctRings", stats.correctRings);
        profile.put("wrongRings", stats.wrongRings);
        profile.put("wonCards", stats.wonCards);
        profile.put("totalRank", stats.totalRank);
        int gamesPlayed = stats.gamesPlayed != null ? stats.gamesPlayed : 0;
        int wins = stats.wins != null ? stats.wins : 0;
        int rings = stats.rings != null ? stats.rings : 0;
        int correctRings = stats.correctRings != null ? stats.correctRings : 0;
        int totalRank = stats.totalRank != null ? stats.totalRank : 0;
        profile.put("winRate", gamesPlayed > 0 ? (double) wins / gamesPlayed : null);
        profile.put("correctRate", rings > 0 ? (double) correctRings / rings : null);
        profile.put("averageRank", gamesPlayed > 0 ? (double) totalRank / gamesPlayed : null);
        profile.put("isComputer", Boolean.TRUE.equals(stats.isComputer));
        profile.put("computerId", stats.computerId);
        try {
            profile.put("defeatedComputers", objectMapper.readValue(
                    stats.defeatedComputers != null ? stats.defeatedComputers : "{}",
                    new TypeReference<Map<String, Object>>() {
                    }));
            profile.put("history", objectMapper.readValue(
                    stats.history != null ? stats.history : "[]",
                    new TypeReference<List<Object>>() {
                    }));
        } catch (Exception ex) {
            profile.put("defeatedComputers", Map.of());
            profile.put("history", List.of());
        }
        profile.put("godRewardGameId", stats.godRewardGameId);
        profile.put("godDefeatedAt", stats.godDefeatedAt);
        return profile;
    }

    /**
     * Returns persisted match log text for replay. Caller must be a participant in the game.
     */
    public Map<String, Object> matchReplayFor(String statsId, String gameId) {
        if (gameId == null || gameId.isBlank()) {
            throw new CofException(ErrorCode.BAD_REQUEST, "缺少对局 ID。");
        }
        if (!userParticipatedInGame(statsId, gameId)) {
            throw new CofException(ErrorCode.FORBIDDEN, "只能查看自己参与过的对局回放。");
        }
        CofMatchHistory row = matchHistoryMapper.selectOne(
                new QueryWrapper<CofMatchHistory>().eq("game_id", gameId).last("LIMIT 1"));
        if (row == null || row.logText == null || row.logText.isBlank()) {
            throw new CofException(ErrorCode.NOT_FOUND, "该对局暂无回放日志。");
        }
        Map<String, Object> replay = new HashMap<>();
        replay.put("gameId", row.gameId);
        replay.put("roomId", row.roomId);
        replay.put("playedAt", row.playedAt);
        replay.put("logText", row.logText);
        if (row.summary != null && !row.summary.isBlank()) {
            try {
                Map<String, Object> summary = objectMapper.readValue(
                        row.summary,
                        new TypeReference<Map<String, Object>>() {
                        });
                replay.put("summary", summary);
            } catch (Exception ignored) {
                // optional metadata
            }
        }
        return replay;
    }

    private boolean userParticipatedInGame(String statsId, String gameId) {
        CofUserStats stats = statsMapper.selectById(statsId);
        if (stats == null || stats.history == null || stats.history.isBlank()) {
            return false;
        }
        try {
            List<Map<String, Object>> history = objectMapper.readValue(
                    stats.history,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            for (Map<String, Object> entry : history) {
                Object id = entry.get("gameId");
                if (gameId.equals(id)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            return false;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> leaderboard() {
        return redis.get(RedisKeys.CACHE_LEADERBOARD, Map.class)
                .map(map -> (Map<String, Object>) map)
                .orElseGet(() -> {
                    List<CofUserStats> rows = statsMapper.selectList(
                            new QueryWrapper<CofUserStats>()
                                    .orderByDesc("wins")
                                    .last("LIMIT 100"));
                    List<Map<String, Object>> players = new ArrayList<>();
                    for (CofUserStats row : rows) {
                        players.add(enrichLeaderboardPlayer(row));
                    }
                    players.sort(Comparator
                            .comparingInt((Map<String, Object> m) -> (Integer) m.getOrDefault("wins", 0))
                            .reversed()
                            .thenComparingInt(m -> (Integer) m.getOrDefault("gamesPlayed", 0))
                            .reversed());
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("players", players);
                    payload.put("matches", loadLeaderboardMatches());
                    redis.set(RedisKeys.CACHE_LEADERBOARD, payload, Duration.ofMinutes(5));
                    return payload;
                });
    }

    private Map<String, Object> enrichLeaderboardPlayer(CofUserStats row) {
        int gamesPlayed = row.gamesPlayed != null ? row.gamesPlayed : 0;
        int wins = row.wins != null ? row.wins : 0;
        int rings = row.rings != null ? row.rings : 0;
        int correctRings = row.correctRings != null ? row.correctRings : 0;
        int wonCards = row.wonCards != null ? row.wonCards : 0;
        int totalRank = row.totalRank != null ? row.totalRank : 0;
        Map<String, Object> item = new HashMap<>();
        item.put("statsId", row.statsId);
        item.put("username", row.username);
        item.put("gamesPlayed", gamesPlayed);
        item.put("wins", wins);
        item.put("rings", rings);
        item.put("correctRings", correctRings);
        item.put("wonCards", wonCards);
        item.put("isComputer", Boolean.TRUE.equals(row.isComputer));
        item.put("computerId", row.computerId);
        item.put("winRate", gamesPlayed > 0 ? (double) wins / gamesPlayed : null);
        item.put("correctRate", rings > 0 ? (double) correctRings / rings : null);
        item.put("averageRank", gamesPlayed > 0 ? (double) totalRank / gamesPlayed : null);
        item.put("defeatedComputers", parseDefeatedComputers(row.defeatedComputers));
        return item;
    }

    private Map<String, Object> parseDefeatedComputers(String json) {
        try {
            return objectMapper.readValue(
                    json != null ? json : "{}",
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> loadLeaderboardMatches() {
        List<CofMatchHistory> rows = matchHistoryMapper.selectList(
                new QueryWrapper<CofMatchHistory>().orderByDesc("played_at").last("LIMIT 100"));
        List<Map<String, Object>> matches = new ArrayList<>();
        for (CofMatchHistory row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("at", row.playedAt);
            item.put("roomId", row.roomId);
            item.put("gameId", row.gameId);
            if (row.summary != null && !row.summary.isBlank()) {
                try {
                    Map<String, Object> summary = objectMapper.readValue(
                            row.summary,
                            new TypeReference<Map<String, Object>>() {
                            });
                    item.putAll(summary);
                } catch (Exception ignored) {
                    // keep base fields only
                }
            }
            matches.add(item);
        }
        matches.sort(Comparator.comparingLong((Map<String, Object> m) -> {
            Object playCount = m.get("playCount");
            return playCount instanceof Number number ? number.longValue() : 0L;
        }).reversed());
        return matches;
    }

    private void applySummaryToPlayer(GameSummary.SummaryPlayer entry, GameSummary summary) {
        String statsId = entry.statsId != null && !entry.statsId.isBlank()
                ? entry.statsId
                : entry.clientId;
        CofUserStats stats = ensureStats(statsId, entry.username, entry.isComputer, entry.computerId);
        stats.username = entry.username;
        stats.isComputer = entry.isComputer;
        stats.computerId = entry.computerId;
        stats.gamesPlayed = (stats.gamesPlayed != null ? stats.gamesPlayed : 0) + 1;
        if (entry.clientId != null && entry.clientId.equals(summary.winnerId)) {
            stats.wins = (stats.wins != null ? stats.wins : 0) + 1;
        }
        PlayerStats ps = entry.stats != null ? entry.stats : new PlayerStats();
        stats.rings = (stats.rings != null ? stats.rings : 0) + ps.rings;
        stats.correctRings = (stats.correctRings != null ? stats.correctRings : 0) + ps.correctRings;
        stats.wrongRings = (stats.wrongRings != null ? stats.wrongRings : 0) + ps.wrongRings;
        stats.wonCards = (stats.wonCards != null ? stats.wonCards : 0) + ps.wonCards;
        int rank = entry.rank != null ? entry.rank : summary.playerCount;
        stats.totalRank = (stats.totalRank != null ? stats.totalRank : 0) + rank;
        appendHistoryEntry(stats, entry, summary);
        stats.updatedAt = System.currentTimeMillis();
        statsMapper.updateById(stats);
    }

    private void appendHistoryEntry(CofUserStats stats, GameSummary.SummaryPlayer entry, GameSummary summary) {
        try {
            List<Map<String, Object>> history = objectMapper.readValue(
                    stats.history != null ? stats.history : "[]",
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            Map<String, Object> row = new HashMap<>();
            row.put("gameId", summary.gameId);
            row.put("roomId", summary.roomId);
            row.put("at", summary.at);
            row.put("playerCount", summary.playerCount);
            row.put("rank", entry.rank);
            PlayerStats ps = entry.stats != null ? entry.stats : new PlayerStats();
            row.put("plays", ps.plays);
            row.put("rings", ps.rings);
            row.put("correctRings", ps.correctRings);
            row.put("wrongRings", ps.wrongRings);
            row.put("wonCards", ps.wonCards);
            row.put("hasReplay", true);
            history.add(0, row);
            if (history.size() > 100) {
                history = new ArrayList<>(history.subList(0, 100));
            }
            stats.history = objectMapper.writeValueAsString(history);
        } catch (Exception ex) {
            stats.history = "[]";
        }
    }

    private void updateComputerDefeatStats(GameSummary summary) {
        List<GameSummary.SummaryPlayer> computers = summary.players.stream()
                .filter(p -> p.isComputer && p.computerId != null && !p.computerId.isBlank() && p.rank != null)
                .toList();
        List<GameSummary.SummaryPlayer> humans = summary.players.stream()
                .filter(p -> !p.isComputer && p.rank != null)
                .toList();
        for (GameSummary.SummaryPlayer human : humans) {
            String statsId = human.statsId != null && !human.statsId.isBlank()
                    ? human.statsId
                    : human.clientId;
            CofUserStats stats = ensureStats(statsId, human.username, false, null);
            Map<String, Object> defeated = parseDefeatedComputers(stats.defeatedComputers);
            for (GameSummary.SummaryPlayer computer : computers) {
                if (human.rank < computer.rank) {
                    if (GOD_COMPUTER_ID.equals(computer.computerId) && human.finalDrawCount < 3) {
                        continue;
                    }
                    int previous = ((Number) defeated.getOrDefault(computer.computerId, 0)).intValue();
                    defeated.put(computer.computerId, previous + 1);
                    if (GOD_COMPUTER_ID.equals(computer.computerId) && previous == 0) {
                        stats.godDefeatedAt = summary.at;
                        stats.godRewardGameId = summary.gameId;
                    }
                }
            }
            try {
                stats.defeatedComputers = objectMapper.writeValueAsString(defeated);
            } catch (Exception ex) {
                stats.defeatedComputers = "{}";
            }
            stats.updatedAt = System.currentTimeMillis();
            statsMapper.updateById(stats);
        }
    }
}
