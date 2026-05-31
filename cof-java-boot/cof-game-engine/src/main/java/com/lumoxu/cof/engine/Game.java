package com.lumoxu.cof.engine;

import java.util.ArrayList;
import java.util.List;

public class Game {

    public String id;
    public String roomId;
    public String status;
    public GameSettings settings;
    public List<Player> players = new ArrayList<>();
    public List<String> spectators = new ArrayList<>();
    public int turnIndex;
    public long turnStartedAt;
    public long turnAvailableAt;
    public long turnDeadlineAt;
    public long lastPlayAt;
    public long lockedUntil;
    public String lockMessage = "";
    public GameAnimation lastAnimation;
    public int playCount;
    public int bellCount;
    public int successBellCount;
    public int failBellCount;
    public int discardedCards;
    public List<TopCardEntry> preLastTopCards = new ArrayList<>();
    public MatchInfo lastMatch;
    public List<GameLog> logs = new ArrayList<>();
    public List<String> eliminatedOrder = new ArrayList<>();
    public String winnerId;
    public List<String> continueVotes = new ArrayList<>();
    public Long continueCountdownStartedAt;
    public Long continueReturnAt;
    public ResultInfo resultInfo;
    public long createdAt;
    public Long startedAt;
    public Long finishedAt;
}
