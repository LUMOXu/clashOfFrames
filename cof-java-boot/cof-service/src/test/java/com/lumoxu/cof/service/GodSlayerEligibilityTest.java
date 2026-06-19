package com.lumoxu.cof.service;

import com.lumoxu.cof.engine.Card;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.engine.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GodSlayerEligibilityTest {

    @Test
    void allowsDeckOneOnly() {
        Game game = eligibleGame(List.of("1"), Map.of());

        assertTrue(GodSlayerEligibility.eligible(game));
    }

    @Test
    void allowsDeckTwoOnly() {
        Game game = eligibleGame(List.of("2"), Map.of());

        assertTrue(GodSlayerEligibility.eligible(game));
    }

    @Test
    void allowsDecksOneAndTwoTogether() {
        Game game = eligibleGame(List.of("1", "2"), Map.of());

        assertTrue(GodSlayerEligibility.eligible(game));
    }

    @Test
    void ignoresLibraryCopyValues() {
        Game game = eligibleGame(
                List.of("1", "2"),
                Map.of("1", 0, "2", 42, "unselected", -7));

        assertTrue(GodSlayerEligibility.eligible(game));
    }

    @Test
    void rejectsAllowedDeckMixedWithAnotherDeck() {
        Game game = eligibleGame(List.of("1", "3"), Map.of());

        assertFalse(GodSlayerEligibility.eligible(game));
    }

    @Test
    void rejectsEmptyLibraryIds() {
        Game game = eligibleGame(List.of(), Map.of());

        assertFalse(GodSlayerEligibility.eligible(game));
    }

    @Test
    void rejectsWinnerWithOnlyTwoCards() {
        Game game = eligibleGame(List.of("1"), Map.of());
        winner(game).drawPile.remove(0);
        game.discardedCards += 1;

        assertFalse(GodSlayerEligibility.eligible(game));
    }

    @Test
    void rejectsGameWhereGodWasNotEliminated() {
        Game game = eligibleGame(List.of("1"), Map.of());
        god(game).eliminated = false;

        assertFalse(GodSlayerEligibility.eligible(game));
    }

    @Test
    void rejectsComputerWinner() {
        Game game = eligibleGame(List.of("1"), Map.of());
        winner(game).isComputer = true;

        assertFalse(GodSlayerEligibility.eligible(game));
    }

    @Test
    void rejectsWinnerWhoIsAlreadyGodSlayer() {
        Game game = eligibleGame(List.of("1"), Map.of());
        winner(game).godSlayer = true;

        assertFalse(GodSlayerEligibility.eligible(game));
    }

    @Test
    void rejectsUnfinishedGame() {
        Game game = eligibleGame(List.of("1"), Map.of());
        game.status = "playing";

        assertFalse(GodSlayerEligibility.eligible(game));
    }

    @Test
    void rejectsMissingWinner() {
        Game game = eligibleGame(List.of("1"), Map.of());
        game.winnerId = "missing";

        assertFalse(GodSlayerEligibility.eligible(game));
    }

    @Test
    void rejectsExactlyOneHundredFiftyTableCards() {
        Game game = eligibleGame(List.of("1"), Map.of());
        game.discardedCards = 147;

        assertFalse(GodSlayerEligibility.eligible(game));
    }

    @Test
    void allowsOneHundredFiftyOneTableCards() {
        Game game = eligibleGame(List.of("1"), Map.of());
        game.discardedCards = 148;

        assertTrue(GodSlayerEligibility.eligible(game));
    }

    private static Game eligibleGame(List<String> libraryIds, Map<String, Integer> libraryCopies) {
        Game game = new Game();
        game.status = "finished";
        game.settings = new GameSettings();
        game.settings.libraryIds = new ArrayList<>(libraryIds);
        game.settings.libraryCopies = new HashMap<>(libraryCopies);

        Player winner = new Player();
        winner.clientId = "winner";
        winner.drawPile = cards(3);

        Player god = new Player();
        god.clientId = "god";
        god.isComputer = true;
        god.computerId = "computer_god";
        god.eliminated = true;

        game.players = new ArrayList<>(List.of(winner, god));
        game.winnerId = winner.clientId;
        game.discardedCards = 148;
        return game;
    }

    private static Player winner(Game game) {
        return game.players.get(0);
    }

    private static Player god(Game game) {
        return game.players.get(1);
    }

    private static List<Card> cards(int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card());
        }
        return cards;
    }
}
