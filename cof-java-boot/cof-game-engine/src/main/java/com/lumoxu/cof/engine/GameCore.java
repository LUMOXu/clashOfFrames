package com.lumoxu.cof.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

import static com.lumoxu.cof.engine.GameConstants.DISCONNECTED_TURN_TIMEOUT_MS;
import static com.lumoxu.cof.engine.GameConstants.FAIL_MOVE_MS;
import static com.lumoxu.cof.engine.GameConstants.FAIL_STAGGER_MS;
import static com.lumoxu.cof.engine.GameConstants.MANUAL_TURN_TIMEOUT_MS;
import static com.lumoxu.cof.engine.GameConstants.PUBLIC_LOG_LIMIT;
import static com.lumoxu.cof.engine.GameConstants.SUCCESS_HIGHLIGHT_MS;
import static com.lumoxu.cof.engine.GameConstants.SUCCESS_MOVE_MS;
import static com.lumoxu.cof.engine.GameConstants.TURN_TIMEOUT_MS;

public final class GameCore {

    public static final GameSettings DEFAULT_SETTINGS = GameSettings.defaultSettings();

    private GameCore() {
    }

    public static String randomId(String prefix) {
        String timePart = Long.toString(System.currentTimeMillis(), 36);
        String randomPart = Double.toString(Math.random()).substring(2, 8);
        return prefix + timePart + randomPart;
    }

    public static <T> List<T> shuffle(List<T> items) {
        return shuffle(items, Math::random);
    }

    public static <T> List<T> shuffle(List<T> items, DoubleSupplier rng) {
        List<T> out = new ArrayList<>(items);
        for (int i = out.size() - 1; i > 0; i--) {
            int j = (int) Math.floor(rng.getAsDouble() * (i + 1));
            Collections.swap(out, i, j);
        }
        return out;
    }

    public static GameSettings normalizeSettings(GameSettings input) {
        return normalizeSettings(input, List.of(), Map.of());
    }

    public static GameSettings normalizeSettings(
            GameSettings input,
            List<String> availableLibraryIds,
            Map<String, Integer> libraryCardCounts) {
        GameSettings merged = CloneUtil.cloneSettings(DEFAULT_SETTINGS);
        if (input != null) {
            merged.minPlayers = input.minPlayers;
            merged.maxPlayers = input.maxPlayers;
            merged.isPublic = input.isPublic;
            if (input.libraryIds != null) {
                merged.libraryIds = new ArrayList<>(input.libraryIds);
            }
            if (input.libraryCopies != null) {
                merged.libraryCopies = new HashMap<>(input.libraryCopies);
            }
            merged.startVoteThresholdMode = input.startVoteThresholdMode;
            merged.startVoteThreshold = input.startVoteThreshold;
            merged.allowEmptyBell = input.allowEmptyBell;
            merged.randomBacks = input.randomBacks;
            merged.conflictResolution = input.conflictResolution;
            merged.disconnectProtection = input.disconnectProtection;
        }

        int minPlayers = Math.max(2, Math.min(8, merged.minPlayers > 0 ? merged.minPlayers : 2));
        int maxPlayers = Math.max(minPlayers, Math.min(8, merged.maxPlayers > 0 ? merged.maxPlayers : 8));

        List<String> selectedLibraries = merged.libraryIds != null ? merged.libraryIds : List.of();
        List<String> validSelected = new ArrayList<>();
        for (String id : selectedLibraries) {
            if (availableLibraryIds.contains(id)) {
                validSelected.add(id);
            }
        }
        List<String> libraryIds;
        if (!validSelected.isEmpty()) {
            libraryIds = new ArrayList<>(new LinkedHashSet<>(validSelected));
        } else if (!availableLibraryIds.isEmpty()) {
            libraryIds = List.of(availableLibraryIds.get(0));
        } else {
            libraryIds = List.of();
        }

        Map<String, Integer> inputCopies = merged.libraryCopies != null ? merged.libraryCopies : Map.of();
        Map<String, Integer> libraryCopies = new HashMap<>();
        for (String id : libraryIds) {
            int cardCount = Math.max(1, libraryCardCounts.getOrDefault(id, 0));
            int limit = Math.max(1, (int) Math.floor(120.0 / cardCount));
            int raw = inputCopies.getOrDefault(id, 1);
            libraryCopies.put(id, Math.max(1, Math.min(limit, raw)));
        }

        String thresholdMode = "manual".equals(merged.startVoteThresholdMode) ? "manual" : "auto";
        Integer thresholdValue = merged.startVoteThreshold;
        Integer normalizedThreshold = null;
        if (thresholdValue != null) {
            normalizedThreshold = Math.max(1, Math.min(8, thresholdValue));
        }

        GameSettings result = new GameSettings();
        result.minPlayers = minPlayers;
        result.maxPlayers = maxPlayers;
        result.isPublic = merged.isPublic;
        result.libraryIds = libraryIds;
        result.libraryCopies = libraryCopies;
        result.startVoteThresholdMode = thresholdMode;
        result.startVoteThreshold = normalizedThreshold;
        result.allowEmptyBell = merged.allowEmptyBell;
        result.randomBacks = merged.randomBacks;
        result.conflictResolution = merged.conflictResolution;
        result.disconnectProtection = merged.disconnectProtection;
        return result;
    }

    public static DealResult dealCards(List<Card> cards, int playerCount) {
        int perPlayer = cards.size() / playerCount;
        List<Card> used = new ArrayList<>(cards.subList(0, perPlayer * playerCount));
        List<List<Card>> hands = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            hands.add(new ArrayList<>());
        }
        for (int index = 0; index < used.size(); index++) {
            hands.get(index % playerCount).add(CloneUtil.cloneCard(used.get(index)));
        }
        DealResult result = new DealResult();
        result.hands = hands;
        result.discarded = cards.size() - used.size();
        result.perPlayer = perPlayer;
        return result;
    }

    public static Game createGame(Room room, List<Player> players, List<Card> cards) {
        return createGame(room, players, cards, System.currentTimeMillis(), Math::random);
    }

    public static Game createGame(
            Room room,
            List<Player> players,
            List<Card> cards,
            long now,
            DoubleSupplier rng) {
        List<Card> deck = shuffle(cards, rng);
        DealResult dealt = dealCards(deck, players.size());
        if (dealt.perPlayer < 1) {
            throw new IllegalStateException("Not enough cards to deal at least one card to each player.");
        }

        int startIndex;
        if (room.lastWinnerId != null) {
            startIndex = Math.max(0, indexOfClient(players, room.lastWinnerId));
        } else {
            startIndex = (int) Math.floor(rng.getAsDouble() * players.size());
        }

        List<Player> gamePlayers = new ArrayList<>();
        for (int index = 0; index < players.size(); index++) {
            Player source = players.get(index);
            Player player = new Player();
            player.clientId = source.clientId;
            player.username = source.username;
            player.isComputer = source.isComputer;
            player.computerId = source.computerId;
            player.statsId = source.statsId != null ? source.statsId : source.clientId;
            player.connected = source.connected;
            player.eliminated = false;
            player.exited = false;
            player.ready = source.isComputer;
            if (source.isComputer) {
                player.loadingLoaded = 1;
                player.loadingTotal = 1;
                player.loadingProgress = 100;
                player.loadingCached = true;
                player.loadingStartedAt = now;
                player.loadingFinishedAt = now;
            } else {
                player.loadingLoaded = 0;
                player.loadingTotal = 0;
                player.loadingProgress = 0;
                player.loadingCached = false;
                player.loadingStartedAt = null;
                player.loadingFinishedAt = null;
            }
            player.drawPile = dealt.hands.get(index);
            player.displayPile = new ArrayList<>();
            player.eliminatedAt = null;
            player.rank = null;
            player.stats = new PlayerStats();
            gamePlayers.add(player);
        }

        Game game = new Game();
        game.id = randomId("game_");
        game.roomId = room.id;
        game.status = "loading";
        game.settings = CloneUtil.cloneSettings(room.settings);
        game.players = gamePlayers;
        game.turnIndex = startIndex < 0 ? 0 : startIndex;
        game.turnStartedAt = now;
        game.turnAvailableAt = now;
        game.turnDeadlineAt = now + TURN_TIMEOUT_MS;
        game.discardedCards = dealt.discarded;
        game.resultInfo = createResultInfo(gamePlayers);
        game.createdAt = now;
        addLog(game, "游戏加载中。");
        return game;
    }

    public static PublicGame publicGame(Game game) {
        PublicGame out = new PublicGame();
        out.id = game.id;
        out.roomId = game.roomId;
        out.status = game.status;
        out.settings = CloneUtil.cloneSettings(game.settings);
        out.resultInfo = publicResultInfo(game);
        out.spectators = new ArrayList<>(game.spectators);
        out.turnIndex = game.turnIndex;
        out.turnStartedAt = game.turnStartedAt;
        out.turnAvailableAt = game.turnAvailableAt;
        out.turnDeadlineAt = game.turnDeadlineAt;
        out.lastPlayAt = game.lastPlayAt;
        out.lockedUntil = game.lockedUntil;
        out.lockMessage = game.lockMessage;
        out.playCount = game.playCount;
        out.bellCount = game.bellCount;
        out.successBellCount = game.successBellCount;
        out.failBellCount = game.failBellCount;
        out.discardedCards = game.discardedCards;
        out.eliminatedOrder = new ArrayList<>(game.eliminatedOrder);
        out.winnerId = game.winnerId;
        out.continueVotes = new ArrayList<>(game.continueVotes);
        out.continueCountdownStartedAt = game.continueCountdownStartedAt;
        out.continueReturnAt = game.continueReturnAt;
        out.createdAt = game.createdAt;
        out.startedAt = game.startedAt;
        out.finishedAt = game.finishedAt;

        for (TopCardEntry entry : game.preLastTopCards) {
            out.preLastTopCards.add(publicTopEntry(entry));
        }
        out.lastMatch = publicMatch(game.lastMatch);
        int logLimit = Math.min(PUBLIC_LOG_LIMIT, game.logs.size());
        for (int i = 0; i < logLimit; i++) {
            out.logs.add(CloneUtil.cloneLog(game.logs.get(i)));
        }
        out.lastAnimation = publicAnimation(game.lastAnimation);

        for (Player player : game.players) {
            PublicPlayer publicPlayer = new PublicPlayer();
            publicPlayer.clientId = player.clientId;
            publicPlayer.username = player.username;
            publicPlayer.isComputer = player.isComputer;
            publicPlayer.computerId = player.computerId;
            publicPlayer.statsId = player.statsId;
            publicPlayer.connected = player.connected;
            publicPlayer.eliminated = player.eliminated;
            publicPlayer.exited = player.exited;
            publicPlayer.ready = player.ready;
            publicPlayer.loadingLoaded = player.loadingLoaded;
            publicPlayer.loadingTotal = player.loadingTotal;
            publicPlayer.loadingProgress = player.loadingProgress;
            publicPlayer.loadingCached = player.loadingCached;
            publicPlayer.loadingStartedAt = player.loadingStartedAt;
            publicPlayer.loadingFinishedAt = player.loadingFinishedAt;
            publicPlayer.eliminatedAt = player.eliminatedAt;
            publicPlayer.rank = player.rank;
            publicPlayer.stats = CloneUtil.cloneStats(player.stats);
            publicPlayer.drawCount = player.drawPile.size();
            publicPlayer.displayCount = player.displayPile.size();
            int drawLimit = Math.min(8, player.drawPile.size());
            for (int i = 0; i < drawLimit; i++) {
                publicPlayer.drawPile.add(publicBackCard(player.drawPile.get(i)));
            }
            for (int i = 0; i < player.displayPile.size(); i++) {
                boolean isTop = i == player.displayPile.size() - 1;
                publicPlayer.displayPile.add(publicStackFaceCard(player.displayPile.get(i), isTop));
            }
            out.players.add(publicPlayer);
        }
        return out;
    }

    public static void addLog(Game game, String text) {
        GameLog log = new GameLog();
        log.id = randomId("log_");
        log.playCount = game.playCount;
        log.text = "[出牌数" + game.playCount + "] " + text;
        log.at = System.currentTimeMillis();
        game.logs.add(0, log);
        if (game.logs.size() > 250) {
            while (game.logs.size() > 250) {
                game.logs.remove(game.logs.size() - 1);
            }
        }
    }

    public static Player getPlayer(Game game, String clientId) {
        for (Player player : game.players) {
            if (clientId.equals(player.clientId)) {
                return player;
            }
        }
        return null;
    }

    public static List<Player> activePlayers(Game game) {
        return activePlayers(game, true);
    }

    public static List<Player> activePlayers(Game game, boolean includeEmptyBell) {
        List<Player> out = new ArrayList<>();
        for (Player player : game.players) {
            if (player.eliminated || player.exited) {
                continue;
            }
            if (includeEmptyBell && game.settings.allowEmptyBell) {
                out.add(player);
                continue;
            }
            if (!player.drawPile.isEmpty()) {
                out.add(player);
            }
        }
        return out;
    }

    public static List<TopCardEntry> tableTopCards(Game game) {
        List<TopCardEntry> out = new ArrayList<>();
        for (Player player : game.players) {
            if (player.displayPile.isEmpty()) {
                continue;
            }
            Card card = player.displayPile.get(player.displayPile.size() - 1);
            TopCardEntry entry = new TopCardEntry();
            entry.playerId = player.clientId;
            entry.username = player.username;
            entry.card = CloneUtil.cloneCard(card);
            entry.playedSeq = card.playedSeq > 0 ? card.playedSeq : 0;
            out.add(entry);
        }
        return out;
    }

    public static MatchInfo findMatchFromTopCards(List<TopCardEntry> topCards) {
        Map<Integer, List<TopCardEntry>> groups = new HashMap<>();
        for (TopCardEntry entry : topCards) {
            if (entry.card == null || entry.card.pmvId == null) {
                continue;
            }
            groups.computeIfAbsent(entry.card.pmvId, key -> new ArrayList<>()).add(entry);
        }

        List<List<TopCardEntry>> candidates = new ArrayList<>();
        for (List<TopCardEntry> group : groups.values()) {
            if (group.size() >= 2) {
                candidates.add(group);
            }
        }
        candidates.sort((a, b) -> {
            int aLatest = a.stream().mapToInt(entry -> entry.playedSeq).max().orElse(0);
            int bLatest = b.stream().mapToInt(entry -> entry.playedSeq).max().orElse(0);
            return Integer.compare(bLatest, aLatest);
        });

        if (candidates.isEmpty()) {
            return null;
        }

        List<TopCardEntry> group = new ArrayList<>(candidates.get(0));
        group.sort(Comparator.comparingInt((TopCardEntry entry) -> entry.playedSeq).reversed());
        List<TopCardEntry> shown = group.subList(0, Math.min(2, group.size()));

        MatchInfo match = new MatchInfo();
        match.pmvId = shown.get(0).card.pmvId;
        match.pmvName = shown.get(0).card.pmvName;
        match.cards = CloneUtil.cloneTopEntries(shown);
        return match;
    }

    public static MatchInfo findCurrentMatch(Game game) {
        return findMatchFromTopCards(tableTopCards(game));
    }

    public static MatchInfo findBellMatch(Game game) {
        MatchInfo current = findCurrentMatch(game);
        if (!game.settings.conflictResolution) {
            return current;
        }
        MatchInfo previous = findMatchFromTopCards(game.preLastTopCards);
        return previous != null ? previous : current;
    }

    public static ActionResult canAct(Game game, String clientId, long now) {
        Player player = getPlayer(game, clientId);
        if (player == null) {
            return ActionResult.fail("你不在这局游戏中。");
        }
        if (!"playing".equals(game.status)) {
            return ActionResult.fail("游戏还没有开始。");
        }
        if (game.lockedUntil > now) {
            return ActionResult.fail("动作结算中，请稍等。");
        }
        if (player.eliminated || player.exited) {
            return ActionResult.fail("你已经不能操作了。");
        }
        return ActionResult.ok(player);
    }

    public static int nextTurnIndex(Game game) {
        return nextTurnIndex(game, game.turnIndex);
    }

    public static int nextTurnIndex(Game game, int fromIndex) {
        if (game.players.isEmpty()) {
            return 0;
        }
        for (int step = 1; step <= game.players.size(); step++) {
            int index = (fromIndex + step) % game.players.size();
            Player player = game.players.get(index);
            if (!player.eliminated && !player.exited && !player.drawPile.isEmpty()) {
                return index;
            }
        }
        return fromIndex;
    }

    public static int turnTimeoutMs(Game game) {
        return turnTimeoutMs(game, game.turnIndex);
    }

    public static int turnTimeoutMs(Game game, int index) {
        Player player = game.players.get(index);
        if (player != null && !player.connected && game.settings.disconnectProtection) {
            return DISCONNECTED_TURN_TIMEOUT_MS;
        }
        return TURN_TIMEOUT_MS;
    }

    public static void setTurnTiming(Game game) {
        setTurnTiming(game, System.currentTimeMillis(), 1000, false);
    }

    public static void setTurnTiming(Game game, long now, int availableDelay) {
        setTurnTiming(game, now, availableDelay, false);
    }

    public static void setTurnTiming(Game game, long now, int availableDelay, boolean manualOnly) {
        game.turnStartedAt = now;
        game.turnAvailableAt = now + availableDelay;
        Player current = game.players.get(game.turnIndex);
        long timeout = manualOnly && current != null && current.connected
                ? MANUAL_TURN_TIMEOUT_MS
                : turnTimeoutMs(game);
        game.turnDeadlineAt = now + timeout;
    }

    public static void ensureTurnPlayerCanPlay(Game game) {
        ensureTurnPlayerCanPlay(game, System.currentTimeMillis());
    }

    public static void ensureTurnPlayerCanPlay(Game game, long now) {
        Player current = game.players.get(game.turnIndex);
        if (current == null || current.eliminated || current.exited || current.drawPile.isEmpty()) {
            game.turnIndex = nextTurnIndex(game, game.turnIndex);
            setTurnTiming(game, now, 0);
        }
    }

    public static void startPlaying(Game game) {
        startPlaying(game, System.currentTimeMillis());
    }

    public static void startPlaying(Game game, long now) {
        game.status = "playing";
        if (game.startedAt == null) {
            game.startedAt = now;
        }
        setTurnTiming(game, now, 0);
        ensureTurnPlayerCanPlay(game, now);
        addLog(game, "所有可用玩家加载完成，对局开始。");
        GameReplayRecorder.record(game, now);
    }

    public static ActionResult performPlayCard(Game game, String clientId) {
        return performPlayCard(game, clientId, System.currentTimeMillis(), false);
    }

    public static ActionResult performPlayCard(Game game, String clientId, long now, boolean auto) {
        ActionResult acting = canAct(game, clientId, now);
        if (!acting.ok) {
            return acting;
        }
        Player player = acting.player;
        int playerIndex = indexOfClient(game.players, clientId);
        if (playerIndex != game.turnIndex) {
            return ActionResult.fail("还没轮到你出牌。");
        }
        if (!auto && now < game.turnAvailableAt) {
            return ActionResult.fail("出牌间隔还没到。");
        }
        if (player.drawPile.isEmpty()) {
            return ActionResult.fail("你已经没有未出牌了。");
        }

        game.preLastTopCards = tableTopCards(game);
        Card card = player.drawPile.remove(0);
        game.playCount += 1;
        card.playedSeq = game.playCount;
        card.playedBy = player.clientId;
        player.displayPile.add(card);
        player.stats.plays += 1;
        game.lastPlayAt = now;
        addLog(game, player.username + (auto ? "自动" : "") + "出牌。");

        if (!game.settings.allowEmptyBell && player.drawPile.isEmpty()) {
            eliminatePlayer(game, player.clientId, "牌堆耗尽");
        }
        recordResultInfoRow(game);

        game.turnIndex = nextTurnIndex(game, playerIndex);
        if (game.settings.allowEmptyBell) {
            eliminateEmptyDuelPlayers(game);
        }
        setTurnTiming(game, now, 1000);
        checkGameOver(game, now);
        GameReplayRecorder.record(game, now);
        return ActionResult.okGame(game);
    }

    public static ActionResult performRingBell(Game game, String clientId) {
        return performRingBell(game, clientId, System.currentTimeMillis(), Math::random);
    }

    public static ActionResult performRingBell(Game game, String clientId, long now, DoubleSupplier rng) {
        ActionResult acting = canAct(game, clientId, now);
        if (!acting.ok) {
            return acting;
        }
        Player player = acting.player;
        MatchInfo match = findBellMatch(game);
        game.bellCount += 1;
        player.stats.rings += 1;

        if (match != null) {
            List<Card> wonCards = new ArrayList<>();
            List<AnimationPile> piles = new ArrayList<>();
            for (Player item : game.players) {
                if (!item.displayPile.isEmpty()) {
                    AnimationPile pile = new AnimationPile();
                    pile.playerId = item.clientId;
                    pile.username = item.username;
                    pile.cards = CloneUtil.cloneCards(item.displayPile);
                    piles.add(pile);
                }
                wonCards.addAll(item.displayPile);
                item.displayPile.clear();
            }
            List<Card> shuffledWon = shuffle(wonCards, rng);
            int durationMs = SUCCESS_HIGHLIGHT_MS + SUCCESS_MOVE_MS;
            player.drawPile.addAll(shuffledWon);
            player.stats.correctRings += 1;
            player.stats.wonCards += wonCards.size();
            game.successBellCount += 1;

            MatchInfo lastMatch = new MatchInfo();
            lastMatch.type = "success";
            lastMatch.by = clientId;
            lastMatch.username = player.username;
            lastMatch.pmvId = match.pmvId;
            lastMatch.pmvName = match.pmvName;
            lastMatch.cards = CloneUtil.cloneTopEntries(match.cards);
            lastMatch.wonCards = wonCards.size();
            lastMatch.at = now;
            game.lastMatch = lastMatch;

            GameAnimation animation = new GameAnimation();
            animation.id = randomId("anim_");
            animation.type = "success";
            animation.by = clientId;
            animation.username = player.username;
            animation.targetPlayerId = clientId;
            animation.startedAt = now;
            animation.highlightMs = SUCCESS_HIGHLIGHT_MS;
            animation.moveMs = SUCCESS_MOVE_MS;
            animation.durationMs = durationMs;
            animation.pmvId = match.pmvId;
            animation.pmvName = match.pmvName;
            animation.matchCardIds = new ArrayList<>();
            for (TopCardEntry entry : match.cards) {
                animation.matchCardIds.add(entry.card.id);
            }
            animation.piles = piles;
            game.lastAnimation = animation;

            game.lockedUntil = now + durationMs;
            game.lockMessage = player.username + "匹配成功：" + match.pmvName;
            game.turnIndex = indexOfClient(game.players, clientId);
            if (game.settings.allowEmptyBell) {
                eliminateEmptyDuelPlayers(game);
            }
            recordResultInfoRow(game);
            setTurnTiming(game, game.lockedUntil, 1000, true);
            addLog(game, player.username + "正确按铃，匹配 " + match.pmvName + "，赢得 " + wonCards.size() + " 张牌。");
        } else {
            List<Player> recipients = clockwisePlayersAfter(game, clientId);
            int given = 0;
            List<AnimationTransfer> transfers = new ArrayList<>();
            for (Player recipient : recipients) {
                if (player.drawPile.isEmpty()) {
                    break;
                }
                Card card = player.drawPile.remove(0);
                AnimationTransfer transfer = new AnimationTransfer();
                transfer.fromPlayerId = clientId;
                transfer.toPlayerId = recipient.clientId;
                transfer.card = CloneUtil.cloneCard(card);
                transfer.delayMs = given * FAIL_STAGGER_MS;
                transfers.add(transfer);
                recipient.drawPile.add(0, card);
                given += 1;
            }
            int durationMs = Math.max(
                    FAIL_MOVE_MS,
                    Math.max(0, transfers.size() - 1) * FAIL_STAGGER_MS + FAIL_MOVE_MS);

            player.stats.wrongRings += 1;
            game.failBellCount += 1;

            MatchInfo lastMatch = new MatchInfo();
            lastMatch.type = "fail";
            lastMatch.by = clientId;
            lastMatch.username = player.username;
            lastMatch.given = given;
            lastMatch.at = now;
            game.lastMatch = lastMatch;

            GameAnimation animation = new GameAnimation();
            animation.id = randomId("anim_");
            animation.type = "fail";
            animation.by = clientId;
            animation.username = player.username;
            animation.startedAt = now;
            animation.moveMs = FAIL_MOVE_MS;
            animation.staggerMs = FAIL_STAGGER_MS;
            animation.durationMs = durationMs;
            animation.transfers = transfers;
            game.lastAnimation = animation;

            game.lockedUntil = now + durationMs;
            game.lockMessage = player.username + "错误按铃";
            addLog(game, player.username + "错误按铃，交出 " + given + " 张牌。");
            if (game.settings.allowEmptyBell && player.drawPile.isEmpty()) {
                eliminatePlayer(game, player.clientId, "空牌错误按铃");
            } else if (!game.settings.allowEmptyBell && player.drawPile.isEmpty()) {
                eliminatePlayer(game, player.clientId, "牌堆耗尽");
            }
            if (game.settings.allowEmptyBell) {
                eliminateEmptyDuelPlayers(game);
            }
            recordResultInfoRow(game);
            setTurnTiming(game, game.lockedUntil, 1000, true);
        }

        checkGameOver(game, now);
        GameReplayRecorder.record(game, now);
        return ActionResult.okGame(game);
    }

    public static void eliminatePlayer(Game game, String clientId) {
        eliminatePlayer(game, clientId, "淘汰");
    }

    public static void eliminatePlayer(Game game, String clientId, String reason) {
        Player player = getPlayer(game, clientId);
        if (player == null || player.eliminated) {
            return;
        }
        player.eliminated = true;
        player.eliminatedAt = System.currentTimeMillis();
        player.rank = null;
        game.eliminatedOrder.add(clientId);
        addLog(game, player.username + "已被淘汰（" + reason + "）。");
        Player current = game.players.get(game.turnIndex);
        if (current != null && clientId.equals(current.clientId)) {
            game.turnIndex = nextTurnIndex(game, game.turnIndex);
        }
        GameReplayRecorder.record(game, System.currentTimeMillis());
    }

    public static void eliminateEmptyDuelPlayers(Game game) {
        List<Player> remaining = new ArrayList<>();
        for (Player player : game.players) {
            if (!player.eliminated && !player.exited) {
                remaining.add(player);
            }
        }
        if (remaining.size() != 2) {
            return;
        }
        for (Player player : remaining) {
            if (player.drawPile.isEmpty()) {
                eliminatePlayer(game, player.clientId, "双人局牌堆耗尽");
            }
        }
    }

    public static boolean checkGameOver(Game game) {
        return checkGameOver(game, System.currentTimeMillis());
    }

    public static boolean checkGameOver(Game game, long now) {
        if (!"playing".equals(game.status)) {
            return false;
        }
        List<Player> remaining = new ArrayList<>();
        for (Player player : game.players) {
            if (!player.eliminated && !player.exited) {
                remaining.add(player);
            }
        }
        if (remaining.size() <= 1) {
            Player winner = remaining.isEmpty() ? null : remaining.get(0);
            game.status = "finished";
            game.finishedAt = now;
            game.winnerId = winner != null ? winner.clientId : null;
            assignRanks(game);
            if (winner != null) {
                addLog(game, "祝贺 " + winner.username + " 胜利。");
            }
            GameReplayRecorder.record(game, now);
            return true;
        }
        ensureTurnPlayerCanPlay(game, now);
        return false;
    }

    public static void assignRanks(Game game) {
        int total = game.players.size();
        List<String> eliminated = new ArrayList<>(game.eliminatedOrder);
        for (Player player : game.players) {
            if (!player.eliminated && !player.exited) {
                player.rank = 1;
            }
        }
        for (int index = 0; index < eliminated.size(); index++) {
            Player player = getPlayer(game, eliminated.get(index));
            if (player != null) {
                player.rank = total - index;
            }
        }
        for (Player player : game.players) {
            if (player.rank == null) {
                player.rank = total;
            }
        }
    }

    public static boolean markConnection(Game game, String clientId, boolean connected) {
        return markConnection(game, clientId, connected, true);
    }

    public static boolean markConnection(
            Game game,
            String clientId,
            boolean connected,
            boolean disconnectProtection) {
        Player player = getPlayer(game, clientId);
        if (player == null) {
            return false;
        }
        player.connected = connected;
        if (!connected) {
            if (!disconnectProtection && !player.eliminated) {
                player.exited = true;
                eliminatePlayer(game, clientId, "掉线");
            }
        } else {
            player.exited = false;
        }
        return true;
    }

    public static GameSummary summarizeGameForStats(Game game) {
        GameSummary summary = new GameSummary();
        summary.gameId = game.id;
        summary.roomId = game.roomId;
        summary.at = game.finishedAt != null ? game.finishedAt : System.currentTimeMillis();
        summary.playerCount = game.players.size();
        summary.playCount = game.playCount;
        summary.bellCount = game.bellCount;
        summary.successBellCount = game.successBellCount;
        summary.failBellCount = game.failBellCount;
        summary.winnerId = game.winnerId;
        summary.averageRoundLength = game.successBellCount > 0
                ? (double) game.playCount / game.successBellCount
                : game.playCount;
        for (Player player : game.players) {
            GameSummary.SummaryPlayer sp = new GameSummary.SummaryPlayer();
            sp.clientId = player.clientId;
            sp.username = player.username;
            sp.isComputer = player.isComputer;
            sp.computerId = player.computerId;
            sp.statsId = player.statsId;
            sp.rank = player.rank;
            sp.finalDrawCount = player.drawPile.size();
            sp.stats = CloneUtil.cloneStats(player.stats);
            summary.players.add(sp);
        }
        return summary;
    }

    private static int indexOfClient(List<Player> players, String clientId) {
        for (int i = 0; i < players.size(); i++) {
            if (clientId.equals(players.get(i).clientId)) {
                return i;
            }
        }
        return -1;
    }

    private static ResultInfo createResultInfo(List<Player> gamePlayers) {
        ResultInfo info = new ResultInfo();
        for (Player player : gamePlayers) {
            ResultInfo.ResultPlayer rp = new ResultInfo.ResultPlayer();
            rp.clientId = player.clientId;
            rp.username = player.username;
            info.players.add(rp);
        }
        info.counts.add(drawCountRow(gamePlayers));
        return info;
    }

    private static List<Integer> drawCountRow(List<Player> players) {
        List<Integer> row = new ArrayList<>();
        for (Player player : players) {
            row.add(player.drawPile.size());
        }
        return row;
    }

    private static void recordResultInfoRow(Game game) {
        if (game.resultInfo == null) {
            return;
        }
        List<Integer> row = drawCountRow(game.players);
        while (game.resultInfo.counts.size() <= game.playCount) {
            game.resultInfo.counts.add(new ArrayList<>());
        }
        game.resultInfo.counts.set(game.playCount, row);
    }

    private static List<Integer> drawCountRow(Game game) {
        return drawCountRow(game.players);
    }

    private static PublicGame.PublicResultInfo publicResultInfo(Game game) {
        if (!"finished".equals(game.status) || game.resultInfo == null) {
            return null;
        }
        PublicGame.PublicResultInfo out = new PublicGame.PublicResultInfo();
        for (ResultInfo.ResultPlayer player : game.resultInfo.players) {
            ResultInfo.ResultPlayer copy = new ResultInfo.ResultPlayer();
            copy.clientId = player.clientId;
            copy.username = player.username;
            out.players.add(copy);
        }
        for (List<Integer> row : game.resultInfo.counts) {
            out.counts.add(new ArrayList<>(row));
        }
        return out;
    }

    private static PublicCard publicBackCard(Card card) {
        PublicCard out = new PublicCard();
        out.id = card.id;
        out.libraryId = card.libraryId;
        out.backUrl = card.backUrl;
        return out;
    }

    private static PublicCard publicFaceCard(Card card) {
        PublicCard out = new PublicCard();
        out.id = card.id;
        out.libraryId = card.libraryId;
        out.pmvId = card.pmvId;
        out.pmvName = card.pmvName;
        out.shot = card.shot;
        out.imageUrl = card.imageUrl;
        out.backUrl = card.backUrl;
        out.playedSeq = card.playedSeq;
        out.playedBy = card.playedBy;
        return out;
    }

    private static PublicCard publicStackFaceCard(Card card, boolean isTop) {
        if (isTop) {
            return publicFaceCard(card);
        }
        PublicCard out = new PublicCard();
        out.id = card.id;
        out.imageUrl = card.imageUrl;
        out.playedSeq = card.playedSeq;
        return out;
    }

    private static PublicGame.PublicAnimation publicAnimation(GameAnimation animation) {
        if (animation == null) {
            return null;
        }
        if ("success".equals(animation.type)) {
            PublicGame.PublicAnimation out = new PublicGame.PublicAnimation();
            out.id = animation.id;
            out.type = animation.type;
            out.by = animation.by;
            out.username = animation.username;
            out.targetPlayerId = animation.targetPlayerId;
            out.startedAt = animation.startedAt;
            out.highlightMs = animation.highlightMs;
            out.moveMs = animation.moveMs;
            out.durationMs = animation.durationMs;
            out.pmvId = animation.pmvId;
            out.pmvName = animation.pmvName;
            out.matchCardIds = animation.matchCardIds == null ? null : new ArrayList<>(animation.matchCardIds);
            for (AnimationPile pile : animation.piles) {
                PublicGame.PublicAnimationPile publicPile = new PublicGame.PublicAnimationPile();
                publicPile.playerId = pile.playerId;
                publicPile.username = pile.username;
                publicPile.cardCount = pile.cards.size();
                for (int i = 0; i < pile.cards.size(); i++) {
                    boolean isTop = i == pile.cards.size() - 1;
                    publicPile.cards.add(publicStackFaceCard(pile.cards.get(i), isTop));
                }
                out.piles.add(publicPile);
            }
            return out;
        }
        if ("fail".equals(animation.type)) {
            PublicGame.PublicAnimation out = new PublicGame.PublicAnimation();
            out.id = animation.id;
            out.type = animation.type;
            out.by = animation.by;
            out.username = animation.username;
            out.startedAt = animation.startedAt;
            out.moveMs = animation.moveMs;
            out.staggerMs = animation.staggerMs;
            out.durationMs = animation.durationMs;
            for (AnimationTransfer transfer : animation.transfers) {
                PublicGame.PublicAnimationTransfer publicTransfer = new PublicGame.PublicAnimationTransfer();
                publicTransfer.fromPlayerId = transfer.fromPlayerId;
                publicTransfer.toPlayerId = transfer.toPlayerId;
                publicTransfer.card = publicBackCard(transfer.card);
                publicTransfer.delayMs = transfer.delayMs;
                out.transfers.add(publicTransfer);
            }
            return out;
        }
        PublicGame.PublicAnimation out = new PublicGame.PublicAnimation();
        out.id = animation.id;
        out.type = animation.type;
        out.by = animation.by;
        out.username = animation.username;
        out.targetPlayerId = animation.targetPlayerId;
        out.startedAt = animation.startedAt;
        out.highlightMs = animation.highlightMs;
        out.moveMs = animation.moveMs;
        out.staggerMs = animation.staggerMs;
        out.durationMs = animation.durationMs;
        out.pmvId = animation.pmvId;
        out.pmvName = animation.pmvName;
        out.matchCardIds = animation.matchCardIds == null ? null : new ArrayList<>(animation.matchCardIds);
        return out;
    }

    private static PublicGame.PublicTopEntry publicTopEntry(TopCardEntry entry) {
        PublicGame.PublicTopEntry out = new PublicGame.PublicTopEntry();
        out.playerId = entry.playerId;
        out.username = entry.username;
        out.card = publicFaceCard(entry.card);
        out.playedSeq = entry.playedSeq;
        return out;
    }

    private static PublicGame.PublicMatch publicMatch(MatchInfo match) {
        if (match == null) {
            return null;
        }
        PublicGame.PublicMatch out = new PublicGame.PublicMatch();
        out.type = match.type;
        out.by = match.by;
        out.username = match.username;
        out.pmvId = match.pmvId;
        out.pmvName = match.pmvName;
        out.wonCards = match.wonCards;
        out.given = match.given;
        out.at = match.at;
        if (match.cards != null) {
            out.cards = new ArrayList<>();
            for (TopCardEntry entry : match.cards) {
                out.cards.add(publicTopEntry(entry));
            }
        }
        return out;
    }

    private static List<Player> clockwisePlayersAfter(Game game, String clientId) {
        int start = indexOfClient(game.players, clientId);
        if (start < 0) {
            return List.of();
        }
        List<Player> out = new ArrayList<>();
        for (int step = 1; step < game.players.size(); step++) {
            Player player = game.players.get((start + step) % game.players.size());
            if (!player.eliminated && !player.exited) {
                out.add(player);
            }
        }
        return out;
    }
}
