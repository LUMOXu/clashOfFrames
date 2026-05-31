package com.lumoxu.cof.engine;

import java.util.ArrayList;
import java.util.List;

public class GameSummary {

    public String gameId;
    public String roomId;
    public long at;
    public int playerCount;
    public int playCount;
    public int bellCount;
    public int successBellCount;
    public int failBellCount;
    public String winnerId;
    public double averageRoundLength;
    public List<SummaryPlayer> players = new ArrayList<>();

    public static class SummaryPlayer {
        public String clientId;
        public String username;
        public boolean isComputer;
        public String computerId;
        public String statsId;
        public Integer rank;
        public int finalDrawCount;
        public PlayerStats stats;
    }
}
