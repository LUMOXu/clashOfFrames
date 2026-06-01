package com.lumoxu.cof.service;

import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.service.model.RoomState;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 投票开局：仅真人票数计入阈值；人机不计入人数判据也不参与投票。
 */
@Service
public class RoomStartVoteService {

    public int humanPlayerCount(RoomState room) {
        if (room.players == null) {
            return 0;
        }
        int count = 0;
        for (String clientId : room.players) {
            if (!PlayerPresenceService.isComputerClient(clientId)) {
                count += 1;
            }
        }
        return count;
    }

    public int humanVoteCount(RoomState room) {
        if (room.startVotes == null || room.players == null) {
            return 0;
        }
        int count = 0;
        for (String clientId : room.startVotes) {
            if (room.players.contains(clientId) && !PlayerPresenceService.isComputerClient(clientId)) {
                count += 1;
            }
        }
        return count;
    }

    public int voteRequirement(RoomState room) {
        int humans = humanPlayerCount(room);
        if (humans <= 0) {
            return 1;
        }
        GameSettings settings = room.settings;
        if (settings != null
                && "manual".equals(settings.startVoteThresholdMode)
                && settings.startVoteThreshold != null
                && settings.startVoteThreshold > 0) {
            return Math.max(1, Math.min(humans, settings.startVoteThreshold));
        }
        return Math.max(1, humans - 2);
    }

    /**
     * @return true if countdown or vote list changed
     */
    public boolean evaluateStartVotes(RoomState room, long now) {
        if (!"waiting".equals(room.status)) {
            return false;
        }
        String before = (room.startCountdownStartedAt == null ? "" : room.startCountdownStartedAt)
                + ":"
                + (room.startAt == null ? "" : room.startAt);

        if (room.startVotes == null) {
            room.startVotes = new java.util.ArrayList<>();
        }
        Set<String> voteSet = new LinkedHashSet<>();
        for (String clientId : room.startVotes) {
            if (room.players.contains(clientId) && !PlayerPresenceService.isComputerClient(clientId)) {
                voteSet.add(clientId);
            }
        }
        room.startVotes.clear();
        room.startVotes.addAll(voteSet);

        int required = voteRequirement(room);
        int validVotes = humanVoteCount(room);
        int humans = humanPlayerCount(room);
        int minPlayers = room.settings != null ? room.settings.minPlayers : 2;
        boolean ready = humans >= minPlayers && validVotes >= required;

        if (!ready) {
            room.startCountdownStartedAt = null;
            room.startAt = null;
        } else if (room.startAt == null) {
            room.startCountdownStartedAt = now;
            room.startAt = now + 10_000L;
        }

        String after = (room.startCountdownStartedAt == null ? "" : room.startCountdownStartedAt)
                + ":"
                + (room.startAt == null ? "" : room.startAt);
        return !before.equals(after);
    }

    public boolean isReadyToStart(RoomState room, long now) {
        if (!"waiting".equals(room.status) || room.startAt == null) {
            return false;
        }
        if (now < room.startAt) {
            return false;
        }
        int required = voteRequirement(room);
        int validVotes = humanVoteCount(room);
        int humans = humanPlayerCount(room);
        int minPlayers = room.settings != null ? room.settings.minPlayers : 2;
        return humans >= minPlayers && validVotes >= required;
    }
}
