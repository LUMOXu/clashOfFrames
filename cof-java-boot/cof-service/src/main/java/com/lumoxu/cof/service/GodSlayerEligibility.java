package com.lumoxu.cof.service;

import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.Player;

import java.util.Set;

public final class GodSlayerEligibility {

    private static final String GOD_COMPUTER_ID = "computer_god";
    private static final Set<String> ALLOWED_LIBRARY_IDS = Set.of("1", "2");

    private GodSlayerEligibility() {
    }

    public static boolean eligible(Game game) {
        if (game == null
                || !"finished".equals(game.status)
                || game.settings == null
                || game.settings.libraryIds == null
                || game.settings.libraryIds.isEmpty()
                || !game.settings.libraryIds.stream().allMatch(ALLOWED_LIBRARY_IDS::contains)) {
            return false;
        }

        boolean godEliminated = game.players.stream()
                .anyMatch(player -> GOD_COMPUTER_ID.equals(player.computerId) && player.eliminated);
        if (!godEliminated) {
            return false;
        }

        Player winner = winner(game);
        return winner != null
                && !winner.isComputer
                && winner.drawPile.size() >= 3
                && !winner.godSlayer;
    }

    static Player winner(Game game) {
        if (game == null) {
            return null;
        }
        return game.players.stream()
                .filter(player -> player.clientId != null && player.clientId.equals(game.winnerId))
                .findFirst()
                .orElse(null);
    }
}
