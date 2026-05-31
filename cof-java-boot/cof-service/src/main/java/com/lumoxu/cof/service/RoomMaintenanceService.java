package com.lumoxu.cof.service;

import com.lumoxu.cof.service.model.RoomState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class RoomMaintenanceService {

    private final RoomService roomService;
    private final PlayerPresenceService playerPresenceService;

    public RoomMaintenanceService(RoomService roomService, PlayerPresenceService playerPresenceService) {
        this.roomService = roomService;
        this.playerPresenceService = playerPresenceService;
    }

    /**
     * @return true if the room was disbanded
     */
    public boolean maintain(RoomState room, long now) {
        roomService.migrateHostIfNeeded(room);
        if (!shouldAutoDisband(room, now)) {
            return false;
        }
        roomService.autoDisband(room);
        return true;
    }

    public boolean shouldAutoDisband(RoomState room, long now) {
        List<String> humans = humanOccupants(room);
        if (humans.isEmpty()) {
            return true;
        }
        return humans.stream().allMatch(clientId -> playerPresenceService.isOfflineTooLong(clientId, now));
    }

    static List<String> humanOccupants(RoomState room) {
        Set<String> ids = new LinkedHashSet<>();
        if (room.players != null) {
            ids.addAll(room.players);
        }
        if (room.spectators != null) {
            ids.addAll(room.spectators);
        }
        List<String> humans = new ArrayList<>();
        for (String clientId : ids) {
            if (!PlayerPresenceService.isComputerClient(clientId)) {
                humans.add(clientId);
            }
        }
        return humans;
    }
}
