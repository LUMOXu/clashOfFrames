package com.lumoxu.cof.engine;

import java.util.ArrayList;
import java.util.List;

public class Player {

    public String clientId;
    public String username;
    public boolean isComputer;
    public String computerId;
    public String statsId;
    public boolean connected = true;
    public boolean eliminated;
    public boolean exited;
    public boolean ready;
    public int loadingLoaded;
    public int loadingTotal;
    public int loadingProgress;
    public boolean loadingCached;
    public Long loadingStartedAt;
    public Long loadingFinishedAt;
    public List<Card> drawPile = new ArrayList<>();
    public List<Card> displayPile = new ArrayList<>();
    public Long eliminatedAt;
    public Integer rank;
    public PlayerStats stats = new PlayerStats();
    public ComputerState computerState;
}
