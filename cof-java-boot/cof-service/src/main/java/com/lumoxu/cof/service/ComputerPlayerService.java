package com.lumoxu.cof.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.domain.entity.CofComputerPlayer;
import com.lumoxu.cof.domain.mapper.CofComputerPlayerMapper;
import com.lumoxu.cof.service.model.ComputerPlayerDto;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import com.lumoxu.cof.service.redis.RedisKeys;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class ComputerPlayerService {

    private final CofComputerPlayerMapper playerMapper;
    private final JsonRedisOps redis;

    public ComputerPlayerService(CofComputerPlayerMapper playerMapper, JsonRedisOps redis) {
        this.playerMapper = playerMapper;
        this.redis = redis;
    }

    public List<ComputerPlayerDto> listPlayers() {
        return redis.get(RedisKeys.CACHE_COMPUTER_PLAYERS, new TypeReference<List<ComputerPlayerDto>>() {
                })
                .orElseGet(() -> {
                    List<ComputerPlayerDto> players = loadFromDatabase();
                    redis.set(RedisKeys.CACHE_COMPUTER_PLAYERS, players, Duration.ofHours(6));
                    return players;
                });
    }

    public ComputerPlayerDto findRequired(String id) {
        return listPlayers().stream()
                .filter(p -> p.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new CofException(ErrorCode.NOT_FOUND, "人机不存在。"));
    }

    public List<ComputerPlayerDto> publicPlayers() {
        List<ComputerPlayerDto> publicList = new ArrayList<>();
        for (ComputerPlayerDto player : listPlayers()) {
            ComputerPlayerDto copy = new ComputerPlayerDto();
            copy.id = player.id;
            copy.name = player.name;
            copy.description = player.description;
            publicList.add(copy);
        }
        return publicList;
    }

    public void bustCache() {
        redis.delete(RedisKeys.CACHE_COMPUTER_PLAYERS);
    }

    private List<ComputerPlayerDto> loadFromDatabase() {
        List<ComputerPlayerDto> players = new ArrayList<>();
        for (CofComputerPlayer row : playerMapper.selectList(null)) {
            players.add(toDto(row));
        }
        return players;
    }

    private static ComputerPlayerDto toDto(CofComputerPlayer row) {
        ComputerPlayerDto dto = new ComputerPlayerDto();
        dto.id = row.computerId;
        dto.name = row.name;
        dto.description = row.description;
        dto.playDelayMeanSeconds = row.playDelayMeanSeconds;
        dto.playDelayStdSeconds = row.playDelayStdSeconds;
        dto.reactionMeanSeconds = row.reactionMeanSeconds;
        dto.reactionStdSeconds = row.reactionStdSeconds;
        dto.matchDetectionProbability = row.matchDetectionProbability;
        dto.falseRingProbability = row.falseRingProbability;
        return dto;
    }
}
