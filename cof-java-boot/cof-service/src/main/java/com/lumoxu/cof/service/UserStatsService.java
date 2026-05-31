package com.lumoxu.cof.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.domain.entity.CofUserStats;
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
    private final JsonRedisOps redis;
    private final ObjectMapper objectMapper;

    public UserStatsService(CofUserStatsMapper statsMapper, JsonRedisOps redis, ObjectMapper objectMapper) {
        this.statsMapper = statsMapper;
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
    public List<Map<String, Object>> leaderboard() {
        return redis.get(RedisKeys.CACHE_LEADERBOARD, List.class).map(list -> (List<Map<String, Object>>) list)
                .orElseGet(() -> {
                    List<CofUserStats> rows = statsMapper.selectList(
                            new QueryWrapper<CofUserStats>()
                                    .eq("is_computer", false)
                                    .orderByDesc("wins")
                                    .last("LIMIT 50"));
                    List<Map<String, Object>> payload = new ArrayList<>();
                    for (CofUserStats row : rows) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("statsId", row.statsId);
                        item.put("username", row.username);
                        item.put("wins", row.wins);
                        item.put("gamesPlayed", row.gamesPlayed);
                        payload.add(item);
                    }
                    payload.sort(Comparator.comparingInt((Map<String, Object> m) -> (Integer) m.getOrDefault("wins", 0)).reversed());
                    redis.set(RedisKeys.CACHE_LEADERBOARD, payload, Duration.ofMinutes(5));
                    return payload;
                });
    }
}
