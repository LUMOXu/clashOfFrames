package com.lumoxu.cof.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.domain.entity.CofMatchHistory;
import com.lumoxu.cof.domain.entity.CofUserStats;
import com.lumoxu.cof.domain.mapper.CofMatchHistoryMapper;
import com.lumoxu.cof.domain.mapper.CofUserStatsMapper;
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
}
