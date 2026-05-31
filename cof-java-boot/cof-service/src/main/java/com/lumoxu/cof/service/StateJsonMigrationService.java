package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.common.util.PasswordUtil;
import com.lumoxu.cof.domain.entity.CofUser;
import com.lumoxu.cof.domain.entity.CofUserStats;
import com.lumoxu.cof.domain.mapper.CofUserMapper;
import com.lumoxu.cof.domain.mapper.CofUserStatsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;

@Service
public class StateJsonMigrationService {

    private final ObjectMapper objectMapper;
    private final CofUserMapper userMapper;
    private final CofUserStatsMapper statsMapper;

    public StateJsonMigrationService(
            ObjectMapper objectMapper,
            CofUserMapper userMapper,
            CofUserStatsMapper statsMapper) {
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
        this.statsMapper = statsMapper;
    }

    @Transactional
    public int importFromFile(Path stateJson) throws IOException {
        if (!Files.exists(stateJson)) {
            return 0;
        }
        JsonNode root = objectMapper.readTree(stateJson.toFile());
        JsonNode users = root.path("users");
        int imported = 0;
        Iterator<String> fieldNames = users.fieldNames();
        while (fieldNames.hasNext()) {
            String clientIdText = fieldNames.next();
            JsonNode userNode = users.get(clientIdText);
            UUID clientId = UUID.fromString(clientIdText);
            if (userMapper.selectById(clientId) != null) {
                continue;
            }
            CofUser user = new CofUser();
            user.clientId = clientId;
            user.username = userNode.path("username").asText();
            user.passwordHash = userNode.path("passwordHash").asText();
            user.passwordSalt = textOrNull(userNode, "passwordSalt");
            user.passwordIterations = userNode.path("passwordIterations").asInt(PasswordUtil.DEFAULT_ITERATIONS);
            user.passwordDigest = userNode.path("passwordDigest").asText(PasswordUtil.DEFAULT_DIGEST);
            user.createdAt = userNode.path("createdAt").asLong(System.currentTimeMillis());
            user.lastLoginAt = userNode.path("lastLoginAt").asLong(user.createdAt);
            userMapper.insert(user);
            imported++;
        }
        JsonNode stats = root.path("stats");
        fieldNames = stats.fieldNames();
        while (fieldNames.hasNext()) {
            String statsId = fieldNames.next();
            if (statsMapper.selectById(statsId) != null) {
                continue;
            }
            JsonNode statsNode = stats.get(statsId);
            CofUserStats row = new CofUserStats();
            row.statsId = statsId;
            row.username = statsNode.path("username").asText(statsId);
            row.gamesPlayed = statsNode.path("gamesPlayed").asInt(0);
            row.wins = statsNode.path("wins").asInt(0);
            row.rings = statsNode.path("rings").asInt(0);
            row.correctRings = statsNode.path("correctRings").asInt(0);
            row.wrongRings = statsNode.path("wrongRings").asInt(0);
            row.wonCards = statsNode.path("wonCards").asInt(0);
            row.totalRank = statsNode.path("totalRank").asInt(0);
            row.isComputer = statsNode.path("isComputer").asBoolean(false);
            row.computerId = textOrNull(statsNode, "computerId");
            row.defeatedComputers = statsNode.path("defeatedComputers").isMissingNode()
                    ? "{}"
                    : statsNode.path("defeatedComputers").toString();
            row.history = statsNode.path("history").isMissingNode()
                    ? "[]"
                    : statsNode.path("history").toString();
            row.godRewardGameId = textOrNull(statsNode, "godRewardGameId");
            row.godDefeatedAt = statsNode.path("godDefeatedAt").isNumber()
                    ? statsNode.path("godDefeatedAt").asLong()
                    : null;
            row.updatedAt = statsNode.path("updatedAt").asLong(System.currentTimeMillis());
            statsMapper.insert(row);
        }
        return imported;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
