package com.lumoxu.cof.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.domain.entity.CofComputerPlayer;
import com.lumoxu.cof.domain.mapper.CofComputerPlayerMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ComputerPlayerImportService {

    private final ObjectMapper objectMapper;
    private final Path resourceRoot;
    private final CofComputerPlayerMapper playerMapper;
    private final ComputerPlayerService computerPlayerService;

    public ComputerPlayerImportService(
            ObjectMapper objectMapper,
            @Value("${cof.resource-root:../cof-resource}") String resourceRoot,
            CofComputerPlayerMapper playerMapper,
            ComputerPlayerService computerPlayerService) {
        this.objectMapper = objectMapper;
        this.resourceRoot = Path.of(resourceRoot).toAbsolutePath().normalize();
        this.playerMapper = playerMapper;
        this.computerPlayerService = computerPlayerService;
    }

    @Transactional
    public int importFromDefaultLocations() throws IOException {
        Path configFile = resourceRoot.resolve("config").resolve("computerPlayers.json");
        if (!Files.exists(configFile)) {
            configFile = resourceRoot.getParent().resolve("old").resolve("config").resolve("computerPlayers.json");
        }
        if (!Files.exists(configFile)) {
            configFile = resourceRoot.getParent().resolve("config").resolve("computerPlayers.json");
        }
        if (!Files.exists(configFile)) {
            return 0;
        }
        return importFromFile(configFile);
    }

    @Transactional
    public int importFromFile(Path jsonFile) throws IOException {
        JsonNode root = objectMapper.readTree(jsonFile.toFile());
        long now = System.currentTimeMillis();
        int count = 0;
        for (JsonNode node : root.path("players")) {
            CofComputerPlayer row = new CofComputerPlayer();
            row.computerId = node.path("id").asText();
            row.name = node.path("name").asText();
            row.description = node.path("description").asText(null);
            row.playDelayMeanSeconds = node.path("playDelayMeanSeconds").asDouble(2);
            row.playDelayStdSeconds = node.path("playDelayStdSeconds").asDouble(1);
            row.reactionMeanSeconds = node.path("reactionMeanSeconds").asDouble(2);
            row.reactionStdSeconds = node.path("reactionStdSeconds").asDouble(1);
            row.matchDetectionProbability = node.path("matchDetectionProbability").asDouble(0.5);
            row.falseRingProbability = node.path("falseRingProbability").asDouble(0.1);
            row.updatedAt = now;
            if (playerMapper.selectById(row.computerId) == null) {
                playerMapper.insert(row);
            } else {
                playerMapper.updateById(row);
            }
            count++;
        }
        computerPlayerService.bustCache();
        return count;
    }
}
