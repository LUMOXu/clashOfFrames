package com.lumoxu.cof.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameCoreTest {

    private static Card sampleCard(String id, int pmvId) {
        Card card = new Card();
        card.id = id;
        card.libraryId = "lib";
        card.fileName = pmvId + "a.png";
        card.pmvId = (long) pmvId;
        card.pmvName = "PMV " + pmvId;
        card.shot = "a";
        card.imageUrl = "/cards/lib/cards/" + pmvId + "a.png";
        card.backUrl = "/cards/lib/back.png";
        return card;
    }

    private static Game sampleGame(GameSettings settingsOverrides) {
        Room room = new Room();
        room.id = "1";
        GameSettings input = new GameSettings();
        if (settingsOverrides != null) {
            input.minPlayers = settingsOverrides.minPlayers;
            input.maxPlayers = settingsOverrides.maxPlayers;
            input.libraryIds = settingsOverrides.libraryIds != null
                    ? new ArrayList<>(settingsOverrides.libraryIds) : null;
            input.libraryCopies = settingsOverrides.libraryCopies != null
                    ? new java.util.HashMap<>(settingsOverrides.libraryCopies) : null;
            input.startVoteThresholdMode = settingsOverrides.startVoteThresholdMode;
            input.startVoteThreshold = settingsOverrides.startVoteThreshold;
            input.allowEmptyBell = settingsOverrides.allowEmptyBell;
            input.conflictResolution = settingsOverrides.conflictResolution;
            input.disconnectProtection = settingsOverrides.disconnectProtection;
        }
        room.settings = GameCore.normalizeSettings(input, List.of("lib"), Map.of());

        List<Player> players = List.of(
                player("a", "A"),
                player("b", "B"),
                player("c", "C")
        );

        List<Card> cards = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            cards.add(sampleCard("card-" + index, (index / 2) + 1));
        }

        Game game = GameCore.createGame(room, players, cards, 1000L, () -> 0.42);
        GameCore.startPlaying(game, 1000L);
        return game;
    }

    private static Game sampleGame() {
        return sampleGame(null);
    }

    private static Player player(String clientId, String username) {
        Player player = new Player();
        player.clientId = clientId;
        player.username = username;
        player.connected = true;
        return player;
    }

    @Test
    void normalizesRoomSettingsAndClampsPlayerCounts() {
        GameSettings input = new GameSettings();
        input.minPlayers = 1;
        input.maxPlayers = 1;
        input.libraryIds = List.of("x", "lib");
        input.libraryCopies = Map.of("lib", 99);
        input.startVoteThresholdMode = "manual";
        input.startVoteThreshold = 99;

        GameSettings settings = GameCore.normalizeSettings(
                input, List.of("lib"), Map.of("lib", 50));

        assertEquals(2, settings.minPlayers);
        assertEquals(2, settings.maxPlayers);
        assertEquals(List.of("lib"), settings.libraryIds);
        assertEquals(Map.of("lib", 2), settings.libraryCopies);
        assertEquals("manual", settings.startVoteThresholdMode);
        assertEquals(8, settings.startVoteThreshold);
        assertTrue(settings.conflictResolution);
    }

    @Test
    void publicGameExposesComputerIdentityWithoutHidingNormalState() {
        Room room = new Room();
        room.id = "1";
        GameSettings input = new GameSettings();
        input.minPlayers = 2;
        input.libraryIds = List.of("lib");
        room.settings = GameCore.normalizeSettings(input, List.of("lib"), Map.of());

        Player human = player("human", "Human");
        Player computer = player("computer:1:easy", "Computer Easy");
        computer.isComputer = true;
        computer.computerId = "easy";
        computer.statsId = "computer:easy";

        List<Card> cards = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            cards.add(sampleCard("card-" + index, index));
        }

        Game game = GameCore.createGame(room, List.of(human, computer), cards, 1000L, () -> 0.1);
        PublicGame snapshot = GameCore.publicGame(game);

        assertEquals(true, snapshot.players.get(1).isComputer);
        assertEquals("easy", snapshot.players.get(1).computerId);
        assertEquals("computer:easy", snapshot.players.get(1).statsId);
        assertEquals(true, snapshot.players.get(1).ready);
    }

    @Test
    void dealsEqualCardCountsAndDiscardsLeftovers() {
        List<Card> cards = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            cards.add(sampleCard("c" + index, index));
        }
        DealResult dealt = GameCore.dealCards(cards, 3);
        assertEquals(3, dealt.perPlayer);
        assertEquals(1, dealt.discarded);
        assertIterableEquals(
                List.of(3, 3, 3),
                dealt.hands.stream().map(List::size).toList());
    }

    @Test
    void detectsMatchingTopDisplayCardsByPmvId() {
        Game game = sampleGame();
        game.players.get(0).displayPile.add(cardWithSeq(sampleCard("a1", 7), 1));
        game.players.get(1).displayPile.add(cardWithSeq(sampleCard("b1", 7), 2));
        game.players.get(2).displayPile.add(cardWithSeq(sampleCard("c1", 8), 3));

        MatchInfo match = GameCore.findCurrentMatch(game);
        assertEquals(7, match.pmvId);
        assertEquals(2, match.cards.size());
    }

    @Test
    void matchesTopDisplayCardsWithSamePmvIdAcrossDifferentLibraries() {
        Game game = sampleGame();
        Card base = cardWithSeq(sampleCard("base/100a", 100), 1);
        base.libraryId = "base";
        base.pmvName = "Name from base";
        game.players.get(0).displayPile.add(base);

        Card extra = cardWithSeq(sampleCard("extra/100b", 100), 2);
        extra.libraryId = "extra";
        extra.pmvName = "Name from extra";
        game.players.get(1).displayPile.add(extra);

        MatchInfo match = GameCore.findCurrentMatch(game);
        assertEquals(100, match.pmvId);
        assertEquals(2, match.cards.size());
        assertEquals("Name from extra", match.pmvName);
    }

    @Test
    void correctBellClearsDisplayPilesAndAwardsWonCards() {
        Game game = sampleGame();
        game.players.get(0).displayPile.add(cardWithSeq(sampleCard("a1", 2), 1));
        game.players.get(1).displayPile.add(cardWithSeq(sampleCard("b1", 2), 2));
        game.players.get(2).displayPile.add(cardWithSeq(sampleCard("c1", 3), 3));

        int before = game.players.get(0).drawPile.size();
        ActionResult result = GameCore.performRingBell(game, "a", 2000L, () -> 0.1);
        assertTrue(result.ok);
        assertIterableEquals(
                List.of(0, 0, 0),
                game.players.stream().map(p -> p.displayPile.size()).toList());
        assertEquals(before + 3, game.players.get(0).drawPile.size());
        assertEquals(1, game.successBellCount);
        assertEquals(2, game.lastMatch.pmvId);
        assertEquals("success", game.lastAnimation.type);
        assertEquals(3, game.lastAnimation.piles.size());
        assertEquals(6200L, game.lockedUntil);
    }

    @Test
    void wrongBellGivesCardsToOtherActivePlayers() {
        Game game = sampleGame();
        game.players.get(0).drawPile = new ArrayList<>(List.of(
                sampleCard("x1", 1),
                sampleCard("x2", 2),
                sampleCard("x3", 3)));
        game.players.get(1).drawPile = new ArrayList<>();
        game.players.get(2).drawPile = new ArrayList<>();

        ActionResult result = GameCore.performRingBell(game, "a", 2000L, Math::random);
        assertTrue(result.ok);
        assertEquals(1, game.players.get(0).drawPile.size());
        assertEquals(1, game.players.get(1).drawPile.size());
        assertEquals(1, game.players.get(2).drawPile.size());
        assertEquals(1, game.failBellCount);
        assertEquals("fail", game.lastAnimation.type);
        assertEquals(2, game.lastAnimation.transfers.size());
    }

    @Test
    void conflictResolutionAcceptsPreviousTableMatch() {
        GameSettings overrides = new GameSettings();
        overrides.conflictResolution = true;
        Game game = sampleGame(overrides);

        TopCardEntry oldA = topEntry("a", "A", cardWithSeq(sampleCard("old-a", 5), 1), 1);
        TopCardEntry oldB = topEntry("b", "B", cardWithSeq(sampleCard("old-b", 5), 2), 2);
        game.preLastTopCards = List.of(oldA, oldB);

        game.players.get(0).displayPile.add(cardWithSeq(sampleCard("new-a", 1), 3));
        game.players.get(1).displayPile.add(cardWithSeq(sampleCard("new-b", 2), 4));

        ActionResult result = GameCore.performRingBell(game, "c", 2000L, () -> 0.1);
        assertTrue(result.ok);
        assertEquals(1, game.successBellCount);
        assertEquals(5, game.lastMatch.pmvId);
    }

    @Test
    void disabledConflictResolutionIgnoresPreviousTableMatch() {
        GameSettings overrides = new GameSettings();
        overrides.conflictResolution = false;
        Game game = sampleGame(overrides);

        TopCardEntry oldA = topEntry("a", "A", cardWithSeq(sampleCard("old-a", 5), 1), 1);
        TopCardEntry oldB = topEntry("b", "B", cardWithSeq(sampleCard("old-b", 5), 2), 2);
        game.preLastTopCards = List.of(oldA, oldB);

        game.players.get(0).displayPile.add(cardWithSeq(sampleCard("new-a", 1), 3));
        game.players.get(1).displayPile.add(cardWithSeq(sampleCard("new-b", 2), 4));

        ActionResult result = GameCore.performRingBell(game, "c", 2000L, Math::random);
        assertTrue(result.ok);
        assertEquals(1, game.failBellCount);
    }

    @Test
    void playingLastDrawCardEliminatesPlayerWhenEmptyBellDisabled() {
        Game game = sampleGame();
        game.turnIndex = 0;
        game.players.get(0).drawPile = new ArrayList<>(List.of(sampleCard("last", 9)));

        ActionResult result = GameCore.performPlayCard(game, "a", 2000L, false);
        assertTrue(result.ok);
        assertTrue(game.players.get(0).eliminated);
    }

    @Test
    void resultInfoStoresDrawCountsByCumulativePlayCount() {
        Game game = sampleGame();
        game.turnIndex = 0;

        assertIterableEquals(List.of(4, 4, 4), game.resultInfo.counts.get(0));

        ActionResult played = GameCore.performPlayCard(game, "a", 2000L, false);
        assertTrue(played.ok);
        assertIterableEquals(List.of(3, 4, 4), game.resultInfo.counts.get(1));

        ActionResult rang = GameCore.performRingBell(game, "b", 3000L, Math::random);
        assertTrue(rang.ok);
        assertIterableEquals(List.of(4, 2, 5), game.resultInfo.counts.get(1));
        assertEquals(2, game.resultInfo.counts.size());
    }

    @Test
    void disconnectedProtectedPlayerGetsTwoSecondAutoPlayDeadline() {
        GameSettings overrides = new GameSettings();
        overrides.disconnectProtection = true;
        Game game = sampleGame(overrides);
        game.turnIndex = 1;
        GameCore.markConnection(game, "b", false, true);
        GameCore.setTurnTiming(game, 3000L, 0);
        assertEquals(5000L, game.turnDeadlineAt);
        assertEquals(false, game.players.get(1).eliminated);
    }

    @Test
    void allowEmptyBellEliminatesEmptyPlayerInTwoPlayerDuel() {
        GameSettings overrides = new GameSettings();
        overrides.allowEmptyBell = true;
        Game game = sampleGame(overrides);
        game.players.get(2).eliminated = true;
        game.eliminatedOrder = new ArrayList<>(List.of("c"));
        game.turnIndex = 0;
        game.players.get(0).drawPile = new ArrayList<>(List.of(sampleCard("last", 9)));
        game.players.get(1).drawPile = new ArrayList<>(List.of(sampleCard("other", 10)));

        ActionResult result = GameCore.performPlayCard(game, "a", 2000L, false);
        assertTrue(result.ok);
        assertTrue(game.players.get(0).eliminated);
        assertEquals("finished", game.status);
        assertEquals("b", game.winnerId);
    }

    @Test
    void finishedGameSummaryIncludesWinnerAndBellTotals() {
        Game game = sampleGame();
        game.players.get(1).eliminated = true;
        game.players.get(2).eliminated = true;
        game.eliminatedOrder = new ArrayList<>(List.of("b", "c"));
        game.status = "playing";
        game.successBellCount = 2;
        game.bellCount = 3;

        GameCore.checkGameOver(game, 5000L);
        GameSummary summary = GameCore.summarizeGameForStats(game);

        assertEquals("a", summary.winnerId);
        assertEquals(3, summary.bellCount);
        assertEquals((double) game.playCount / 2, summary.averageRoundLength);
    }

    @Test
    void publicGameStateKeepsFullTablePilesWithCompactNonTopCards() {
        Game game = sampleGame();
        List<Card> pile = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            Card card = cardWithSeq(sampleCard("shown-" + index, index), index + 1);
            card.playedBy = "a";
            pile.add(card);
        }
        game.players.get(0).displayPile = pile;

        PublicGame snapshot = GameCore.publicGame(game);
        PublicPlayer player = snapshot.players.get(0);

        assertEquals(20, player.displayCount);
        assertEquals(20, player.displayPile.size());
        assertEquals(Set.of("id", "imageUrl", "playedSeq"), new TreeSet<>(keys(player.displayPile.get(0))));
        assertEquals("shown-0", player.displayPile.get(0).id);
        assertEquals("shown-19", player.displayPile.get(19).id);
        assertEquals(19, player.displayPile.get(19).pmvId);
    }

    @Test
    void publicSuccessAnimationOnlyIncludesVisibleCardDetails() {
        Game game = sampleGame();
        List<Card> cards = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            Card card = cardWithSeq(sampleCard("anim-" + index, index), index + 1);
            card.playedBy = "a";
            cards.add(card);
        }

        GameAnimation animation = new GameAnimation();
        animation.id = "anim-test";
        animation.type = "success";
        animation.by = "a";
        animation.username = "A";
        animation.targetPlayerId = "a";
        animation.startedAt = 2000L;
        animation.highlightMs = 3000;
        animation.moveMs = 1200;
        animation.durationMs = 4200;
        animation.pmvId = 1L;
        animation.pmvName = "PMV 1";
        animation.matchCardIds = List.of("anim-19");
        AnimationPile pile = new AnimationPile();
        pile.playerId = "a";
        pile.username = "A";
        pile.cards = cards;
        animation.piles = List.of(pile);
        game.lastAnimation = animation;

        PublicGame snapshot = GameCore.publicGame(game);
        PublicGame.PublicAnimationPile publicPile = snapshot.lastAnimation.piles.get(0);

        assertEquals(20, publicPile.cardCount);
        assertEquals(20, publicPile.cards.size());
        assertEquals(Set.of("id", "imageUrl", "playedSeq"), new TreeSet<>(keys(publicPile.cards.get(0))));
        assertEquals("anim-0", publicPile.cards.get(0).id);
        assertEquals("anim-19", publicPile.cards.get(19).id);
        assertEquals(19, publicPile.cards.get(19).pmvId);
    }

    @Test
    void publicGameExposesResultInfoOnlyAfterFinish() {
        Game game = sampleGame();
        assertNull(GameCore.publicGame(game).resultInfo);

        game.status = "finished";
        game.winnerId = "a";
        game.finishedAt = 5000L;
        PublicGame snapshot = GameCore.publicGame(game);

        assertEquals(3, snapshot.resultInfo.players.size());
        assertEquals("a", snapshot.resultInfo.players.get(0).clientId);
        assertEquals("A", snapshot.resultInfo.players.get(0).username);
        assertEquals("b", snapshot.resultInfo.players.get(1).clientId);
        assertEquals("B", snapshot.resultInfo.players.get(1).username);
        assertEquals("c", snapshot.resultInfo.players.get(2).clientId);
        assertEquals("C", snapshot.resultInfo.players.get(2).username);
        assertIterableEquals(List.of(4, 4, 4), snapshot.resultInfo.counts.get(0));
    }

    private static Card cardWithSeq(Card card, int playedSeq) {
        Card copy = CloneUtil.cloneCard(card);
        copy.playedSeq = playedSeq;
        return copy;
    }

    private static TopCardEntry topEntry(String playerId, String username, Card card, int playedSeq) {
        TopCardEntry entry = new TopCardEntry();
        entry.playerId = playerId;
        entry.username = username;
        entry.card = card;
        entry.playedSeq = playedSeq;
        return entry;
    }

    private static Set<String> keys(PublicCard card) {
        List<String> names = new ArrayList<>();
        if (card.id != null) names.add("id");
        if (card.libraryId != null) names.add("libraryId");
        if (card.pmvId != null) names.add("pmvId");
        if (card.pmvName != null) names.add("pmvName");
        if (card.shot != null) names.add("shot");
        if (card.imageUrl != null) names.add("imageUrl");
        if (card.backUrl != null) names.add("backUrl");
        if (card.playedSeq > 0 || card.playedSeq == 0) names.add("playedSeq");
        if (card.playedBy != null) names.add("playedBy");
        return new TreeSet<>(names);
    }
}
