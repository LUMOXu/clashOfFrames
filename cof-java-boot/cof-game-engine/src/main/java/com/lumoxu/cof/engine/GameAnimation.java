package com.lumoxu.cof.engine;

import java.util.ArrayList;
import java.util.List;

public class GameAnimation {

    public String id;
    public String type;
    public String by;
    public String username;
    public String targetPlayerId;
    public long startedAt;
    public Integer highlightMs;
    public Integer moveMs;
    public Integer staggerMs;
    public int durationMs;
    public Integer pmvId;
    public String pmvName;
    public List<String> matchCardIds;
    public List<AnimationPile> piles = new ArrayList<>();
    public List<AnimationTransfer> transfers = new ArrayList<>();
}
