package com.lumoxu.cof.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameSettings {

    public int minPlayers = 2;
    public int maxPlayers = 8;
    public boolean isPublic = true;
    public List<String> libraryIds = new ArrayList<>();
    public Map<String, Integer> libraryCopies = new HashMap<>();
    public String startVoteThresholdMode = "auto";
    public Integer startVoteThreshold;
    public boolean allowEmptyBell = false;
    public boolean randomBacks = false;
    public boolean conflictResolution = true;
    public boolean disconnectProtection = true;

    public static GameSettings defaultSettings() {
        return CloneUtil.clone(new GameSettings());
    }
}
