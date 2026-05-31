package com.lumoxu.cof.service.model;

import com.lumoxu.cof.engine.GameSettings;

import java.util.ArrayList;
import java.util.List;

public class RoomState {

    public String id;
    public String hostId;
    public List<String> players = new ArrayList<>();
    public List<String> spectators = new ArrayList<>();
    public String status = "waiting";
    public GameSettings settings;
    public String gameId;
    public String lastWinnerId;
    public List<String> startVotes = new ArrayList<>();
    public Long startCountdownStartedAt;
    public Long startAt;
    public long createdAt;
}
