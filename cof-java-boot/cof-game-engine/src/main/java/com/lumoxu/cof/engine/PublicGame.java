package com.lumoxu.cof.engine;

import java.util.ArrayList;
import java.util.List;

public class PublicGame {

    public String id;
    public String roomId;
    public String status;
    public GameSettings settings;
    public List<PublicPlayer> players = new ArrayList<>();
    public List<String> spectators = new ArrayList<>();
    public int turnIndex;
    public long turnStartedAt;
    public long turnAvailableAt;
    public long turnDeadlineAt;
    public long lastPlayAt;
    public long lockedUntil;
    public String lockMessage;
    public PublicAnimation lastAnimation;
    public int playCount;
    public int bellCount;
    public int successBellCount;
    public int failBellCount;
    public int discardedCards;
    public List<PublicTopEntry> preLastTopCards = new ArrayList<>();
    public PublicMatch lastMatch;
    public List<GameLog> logs = new ArrayList<>();
    public List<String> eliminatedOrder = new ArrayList<>();
    public String winnerId;
    public List<String> continueVotes = new ArrayList<>();
    public Long continueCountdownStartedAt;
    public Long continueReturnAt;
    public PublicResultInfo resultInfo;
    public long createdAt;
    public Long startedAt;
    public Long finishedAt;

    public static class PublicTopEntry {
        public String playerId;
        public String username;
        public PublicCard card;
        public int playedSeq;
    }

    public static class PublicMatch {
        public String type;
        public String by;
        public String username;
        public Long pmvId;
        public String pmvName;
        public List<PublicTopEntry> cards;
        public Integer wonCards;
        public Integer given;
        public long at;
    }

    public static class PublicResultInfo {
        public List<ResultInfo.ResultPlayer> players = new ArrayList<>();
        public List<List<Integer>> counts = new ArrayList<>();
    }

    public static class PublicAnimation {
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
        public Long pmvId;
        public String pmvName;
        public List<String> matchCardIds;
        public List<PublicAnimationPile> piles = new ArrayList<>();
        public List<PublicAnimationTransfer> transfers = new ArrayList<>();
    }

    public static class PublicAnimationPile {
        public String playerId;
        public String username;
        public int cardCount;
        public List<PublicCard> cards = new ArrayList<>();
    }

    public static class PublicAnimationTransfer {
        public String fromPlayerId;
        public String toPlayerId;
        public PublicCard card;
        public int delayMs;
    }
}
