package com.lumoxu.cof.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CloneUtil {

    private CloneUtil() {
    }

    public static <T> T clone(T value) {
        if (value == null) {
            return null;
        }
        if (value instanceof GameSettings) {
            @SuppressWarnings("unchecked")
            T out = (T) cloneSettings((GameSettings) value);
            return out;
        }
        if (value instanceof Card) {
            @SuppressWarnings("unchecked")
            T out = (T) cloneCard((Card) value);
            return out;
        }
        if (value instanceof PlayerStats) {
            @SuppressWarnings("unchecked")
            T out = (T) cloneStats((PlayerStats) value);
            return out;
        }
        if (value instanceof Player) {
            @SuppressWarnings("unchecked")
            T out = (T) clonePlayer((Player) value);
            return out;
        }
        if (value instanceof Game) {
            @SuppressWarnings("unchecked")
            T out = (T) cloneGame((Game) value);
            return out;
        }
        if (value instanceof TopCardEntry) {
            @SuppressWarnings("unchecked")
            T out = (T) cloneTopEntry((TopCardEntry) value);
            return out;
        }
        if (value instanceof MatchInfo) {
            @SuppressWarnings("unchecked")
            T out = (T) cloneMatch((MatchInfo) value);
            return out;
        }
        if (value instanceof GameLog) {
            @SuppressWarnings("unchecked")
            T out = (T) cloneLog((GameLog) value);
            return out;
        }
        if (value instanceof GameAnimation) {
            @SuppressWarnings("unchecked")
            T out = (T) cloneAnimation((GameAnimation) value);
            return out;
        }
        throw new IllegalArgumentException("Unsupported clone type: " + value.getClass());
    }

    public static GameSettings cloneSettings(GameSettings source) {
        if (source == null) {
            return null;
        }
        GameSettings copy = new GameSettings();
        copy.minPlayers = source.minPlayers;
        copy.maxPlayers = source.maxPlayers;
        copy.isPublic = source.isPublic;
        copy.libraryIds = new ArrayList<>(source.libraryIds);
        copy.libraryCopies = new HashMap<>(source.libraryCopies);
        copy.startVoteThresholdMode = source.startVoteThresholdMode;
        copy.startVoteThreshold = source.startVoteThreshold;
        copy.allowEmptyBell = source.allowEmptyBell;
        copy.randomBacks = source.randomBacks;
        copy.conflictResolution = source.conflictResolution;
        copy.disconnectProtection = source.disconnectProtection;
        return copy;
    }

    public static Card cloneCard(Card source) {
        if (source == null) {
            return null;
        }
        Card copy = new Card();
        copy.id = source.id;
        copy.libraryId = source.libraryId;
        copy.fileName = source.fileName;
        copy.pmvId = source.pmvId;
        copy.pmvName = source.pmvName;
        copy.shot = source.shot;
        copy.imageUrl = source.imageUrl;
        copy.backUrl = source.backUrl;
        copy.playedSeq = source.playedSeq;
        copy.playedBy = source.playedBy;
        return copy;
    }

    public static List<Card> cloneCards(List<Card> cards) {
        List<Card> out = new ArrayList<>();
        for (Card card : cards) {
            out.add(cloneCard(card));
        }
        return out;
    }

    public static PlayerStats cloneStats(PlayerStats source) {
        if (source == null) {
            return null;
        }
        PlayerStats copy = new PlayerStats();
        copy.plays = source.plays;
        copy.rings = source.rings;
        copy.correctRings = source.correctRings;
        copy.wrongRings = source.wrongRings;
        copy.wonCards = source.wonCards;
        return copy;
    }

    public static Player clonePlayer(Player source) {
        if (source == null) {
            return null;
        }
        Player copy = new Player();
        copy.clientId = source.clientId;
        copy.username = source.username;
        copy.isComputer = source.isComputer;
        copy.computerId = source.computerId;
        copy.statsId = source.statsId;
        copy.connected = source.connected;
        copy.eliminated = source.eliminated;
        copy.exited = source.exited;
        copy.ready = source.ready;
        copy.loadingLoaded = source.loadingLoaded;
        copy.loadingTotal = source.loadingTotal;
        copy.loadingProgress = source.loadingProgress;
        copy.loadingCached = source.loadingCached;
        copy.loadingStartedAt = source.loadingStartedAt;
        copy.loadingFinishedAt = source.loadingFinishedAt;
        copy.drawPile = cloneCards(source.drawPile);
        copy.displayPile = cloneCards(source.displayPile);
        copy.eliminatedAt = source.eliminatedAt;
        copy.rank = source.rank;
        copy.stats = cloneStats(source.stats);
        return copy;
    }

    public static TopCardEntry cloneTopEntry(TopCardEntry source) {
        if (source == null) {
            return null;
        }
        TopCardEntry copy = new TopCardEntry();
        copy.playerId = source.playerId;
        copy.username = source.username;
        copy.card = cloneCard(source.card);
        copy.playedSeq = source.playedSeq;
        return copy;
    }

    public static List<TopCardEntry> cloneTopEntries(List<TopCardEntry> entries) {
        List<TopCardEntry> out = new ArrayList<>();
        for (TopCardEntry entry : entries) {
            out.add(cloneTopEntry(entry));
        }
        return out;
    }

    public static MatchInfo cloneMatch(MatchInfo source) {
        if (source == null) {
            return null;
        }
        MatchInfo copy = new MatchInfo();
        copy.type = source.type;
        copy.by = source.by;
        copy.username = source.username;
        copy.pmvId = source.pmvId;
        copy.pmvName = source.pmvName;
        copy.cards = source.cards == null ? null : cloneTopEntries(source.cards);
        copy.wonCards = source.wonCards;
        copy.given = source.given;
        copy.at = source.at;
        return copy;
    }

    public static GameLog cloneLog(GameLog source) {
        if (source == null) {
            return null;
        }
        GameLog copy = new GameLog();
        copy.id = source.id;
        copy.playCount = source.playCount;
        copy.text = source.text;
        copy.at = source.at;
        return copy;
    }

    public static AnimationPile cloneAnimationPile(AnimationPile source) {
        AnimationPile copy = new AnimationPile();
        copy.playerId = source.playerId;
        copy.username = source.username;
        copy.cards = cloneCards(source.cards);
        return copy;
    }

    public static AnimationTransfer cloneAnimationTransfer(AnimationTransfer source) {
        AnimationTransfer copy = new AnimationTransfer();
        copy.fromPlayerId = source.fromPlayerId;
        copy.toPlayerId = source.toPlayerId;
        copy.card = cloneCard(source.card);
        copy.delayMs = source.delayMs;
        return copy;
    }

    public static GameAnimation cloneAnimation(GameAnimation source) {
        if (source == null) {
            return null;
        }
        GameAnimation copy = new GameAnimation();
        copy.id = source.id;
        copy.type = source.type;
        copy.by = source.by;
        copy.username = source.username;
        copy.targetPlayerId = source.targetPlayerId;
        copy.startedAt = source.startedAt;
        copy.highlightMs = source.highlightMs;
        copy.moveMs = source.moveMs;
        copy.staggerMs = source.staggerMs;
        copy.durationMs = source.durationMs;
        copy.pmvId = source.pmvId;
        copy.pmvName = source.pmvName;
        copy.matchCardIds = source.matchCardIds == null ? null : new ArrayList<>(source.matchCardIds);
        for (AnimationPile pile : source.piles) {
            copy.piles.add(cloneAnimationPile(pile));
        }
        for (AnimationTransfer transfer : source.transfers) {
            copy.transfers.add(cloneAnimationTransfer(transfer));
        }
        return copy;
    }

    public static Game cloneGame(Game source) {
        if (source == null) {
            return null;
        }
        Game copy = new Game();
        copy.id = source.id;
        copy.roomId = source.roomId;
        copy.status = source.status;
        copy.settings = cloneSettings(source.settings);
        for (Player player : source.players) {
            copy.players.add(clonePlayer(player));
        }
        copy.spectators = new ArrayList<>(source.spectators);
        copy.turnIndex = source.turnIndex;
        copy.turnStartedAt = source.turnStartedAt;
        copy.turnAvailableAt = source.turnAvailableAt;
        copy.turnDeadlineAt = source.turnDeadlineAt;
        copy.lastPlayAt = source.lastPlayAt;
        copy.lockedUntil = source.lockedUntil;
        copy.lockMessage = source.lockMessage;
        copy.lastAnimation = cloneAnimation(source.lastAnimation);
        copy.playCount = source.playCount;
        copy.bellCount = source.bellCount;
        copy.successBellCount = source.successBellCount;
        copy.failBellCount = source.failBellCount;
        copy.discardedCards = source.discardedCards;
        copy.preLastTopCards = cloneTopEntries(source.preLastTopCards);
        copy.lastMatch = cloneMatch(source.lastMatch);
        for (GameLog log : source.logs) {
            copy.logs.add(cloneLog(log));
        }
        copy.eliminatedOrder = new ArrayList<>(source.eliminatedOrder);
        copy.winnerId = source.winnerId;
        copy.continueVotes = new ArrayList<>(source.continueVotes);
        copy.continueCountdownStartedAt = source.continueCountdownStartedAt;
        copy.continueReturnAt = source.continueReturnAt;
        if (source.resultInfo != null) {
            copy.resultInfo = cloneResultInfo(source.resultInfo);
        }
        copy.createdAt = source.createdAt;
        copy.startedAt = source.startedAt;
        copy.finishedAt = source.finishedAt;
        copy.statsSaved = source.statsSaved;
        return copy;
    }

    public static ResultInfo cloneResultInfo(ResultInfo source) {
        ResultInfo copy = new ResultInfo();
        for (ResultInfo.ResultPlayer player : source.players) {
            ResultInfo.ResultPlayer rp = new ResultInfo.ResultPlayer();
            rp.clientId = player.clientId;
            rp.username = player.username;
            copy.players.add(rp);
        }
        for (List<Integer> row : source.counts) {
            copy.counts.add(new ArrayList<>(row));
        }
        return copy;
    }
}
