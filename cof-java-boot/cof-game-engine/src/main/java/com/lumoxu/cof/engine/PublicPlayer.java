package com.lumoxu.cof.engine;

import java.util.ArrayList;
import java.util.List;

public class PublicPlayer {

    public String clientId;
    public String username;
    public boolean isComputer;
    public String computerId;
    public String statsId;
    public boolean connected;
    public boolean eliminated;
    public boolean exited;
    public boolean ready;
    public int loadingLoaded;
    public int loadingTotal;
    public int loadingProgress;
    public boolean loadingCached;
    public Long loadingStartedAt;
    public Long loadingFinishedAt;
    public Long eliminatedAt;
    public Integer rank;
    public PlayerStats stats;
    public int drawCount;
    public int displayCount;
    public List<PublicCard> drawPile = new ArrayList<>();
    public List<PublicCard> displayPile = new ArrayList<>();
}
