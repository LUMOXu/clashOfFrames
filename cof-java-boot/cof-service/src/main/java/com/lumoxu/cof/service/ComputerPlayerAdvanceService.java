package com.lumoxu.cof.service;

import com.lumoxu.cof.engine.ActionResult;
import com.lumoxu.cof.engine.ComputerState;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameCore;
import com.lumoxu.cof.engine.MatchInfo;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.service.model.ComputerPlayerDto;
import org.springframework.stereotype.Service;

@Service
public class ComputerPlayerAdvanceService {

    private final ComputerPlayerService computerPlayerService;

    public ComputerPlayerAdvanceService(ComputerPlayerService computerPlayerService) {
        this.computerPlayerService = computerPlayerService;
    }

    public ComputerTickOutcome advance(Game game, long now) {
        ComputerTickOutcome result = ComputerTickOutcome.none();
        for (Player player : game.players) {
            if (!player.isComputer || player.eliminated || player.exited) {
                continue;
            }
            ComputerPlayerDto profile = resolveProfile(player);
            if (profile == null) {
                continue;
            }
            ComputerTickOutcome step = advanceOne(game, player, profile, now);
            if (step.played || step.rang) {
                return ComputerTickOutcome.of(true, step.played, step.rang);
            }
        }
        return result;
    }

    private ComputerTickOutcome advanceOne(Game game, Player player, ComputerPlayerDto profile, long now) {
        if (player.computerState == null) {
            player.computerState = computerState("start", game);
        }
        String stateName = player.computerState.name;

        if ("start".equals(stateName)) {
            player.computerState = computerState(
                    !tableHasDisplayCards(game) && canComputerPlay(game, player, now) ? "play" : "wait",
                    game);
            return ComputerTickOutcome.none();
        }

        if ("wait".equals(stateName)) {
            if (game.playCount != player.computerState.observedPlayCount) {
                player.computerState = computerState("analysis", game);
            } else if (canComputerPlay(game, player, now)) {
                player.computerState = computerState("play", game);
            }
            return ComputerTickOutcome.none();
        }

        if ("play".equals(stateName)) {
            if (game.bellCount != player.computerState.observedBellCount) {
                player.computerState = game.lockedUntil > now
                        ? computerState("settling", game, game.lockedUntil)
                        : computerState("next", game);
                return ComputerTickOutcome.none();
            }
            if (game.playCount != player.computerState.observedPlayCount) {
                player.computerState = computerState("analysis", game);
                return ComputerTickOutcome.none();
            }
            if (!canComputerPlay(game, player, now)) {
                if (!player.clientId.equals(game.players.get(game.turnIndex).clientId)) {
                    player.computerState = computerState("analysis", game);
                }
                return ComputerTickOutcome.none();
            }
            if (player.computerState.actionAt == null) {
                player.computerState.actionAt = Math.max(now, game.turnAvailableAt)
                        + sampleClampedMs(
                                profile.playDelayMeanSeconds,
                                profile.playDelayStdSeconds,
                                1500,
                                7000);
                return ComputerTickOutcome.none();
            }
            if (now >= player.computerState.actionAt) {
                ActionResult played = GameCore.performPlayCard(game, player.clientId, now, true);
                player.computerState = computerState("analysis", game);
                if (played.ok) {
                    return ComputerTickOutcome.of(true, true, false);
                }
            }
            return ComputerTickOutcome.none();
        }

        if ("analysis".equals(stateName)) {
            MatchInfo match = GameCore.findCurrentMatch(game);
            boolean shouldRing = match != null
                    ? Math.random() < profile.matchDetectionProbability
                    : Math.random() < profile.falseRingProbability;
            if (shouldRing) {
                player.computerState = computerState("ring", game);
                player.computerState.actionAt = now + sampleClampedMs(
                        profile.reactionMeanSeconds,
                        profile.reactionStdSeconds,
                        100,
                        Integer.MAX_VALUE);
            } else {
                player.computerState = computerState("next", game);
            }
            return ComputerTickOutcome.none();
        }

        if ("ring".equals(stateName)) {
            if (game.bellCount != player.computerState.observedBellCount) {
                player.computerState = game.lockedUntil > now
                        ? computerState("settling", game, game.lockedUntil)
                        : computerState("next", game);
                return ComputerTickOutcome.none();
            }
            if (game.playCount != player.computerState.observedPlayCount) {
                player.computerState = computerState("analysis", game);
                return ComputerTickOutcome.none();
            }
            if (game.lockedUntil > now) {
                player.computerState = computerState("settling", game, game.lockedUntil);
                return ComputerTickOutcome.none();
            }
            if (player.computerState.actionAt != null && now >= player.computerState.actionAt) {
                ActionResult rang = GameCore.performRingBell(game, player.clientId, now, Math::random);
                player.computerState = rang.ok && game.lockedUntil > now
                        ? computerState("settling", game, game.lockedUntil)
                        : computerState("next", game);
                if (rang.ok) {
                    return ComputerTickOutcome.of(true, false, true);
                }
            }
            return ComputerTickOutcome.none();
        }

        if ("settling".equals(stateName)) {
            long waitUntil = player.computerState.waitUntil != null ? player.computerState.waitUntil : 0L;
            if (now >= waitUntil) {
                player.computerState = computerState("next", game);
            }
            return ComputerTickOutcome.none();
        }

        if ("next".equals(stateName)) {
            player.computerState = computerState(canComputerPlay(game, player, now) ? "play" : "wait", game);
            return ComputerTickOutcome.none();
        }

        player.computerState = computerState("wait", game);
        return ComputerTickOutcome.none();
    }

    private ComputerPlayerDto resolveProfile(Player player) {
        if (player.computerId == null || player.computerId.isBlank()) {
            return null;
        }
        try {
            return computerPlayerService.findRequired(player.computerId);
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean tableHasDisplayCards(Game game) {
        return game.players.stream().anyMatch(p -> !p.displayPile.isEmpty());
    }

    private static boolean canComputerPlay(Game game, Player player, long now) {
        if (!"playing".equals(game.status) || game.lockedUntil > now) {
            return false;
        }
        Player current = game.players.get(game.turnIndex);
        return current != null
                && player.clientId.equals(current.clientId)
                && !player.eliminated
                && !player.exited
                && !player.drawPile.isEmpty()
                && now >= game.turnAvailableAt;
    }

    private static ComputerState computerState(String name, Game game) {
        return computerState(name, game, null);
    }

    private static ComputerState computerState(String name, Game game, Long waitUntil) {
        ComputerState state = new ComputerState();
        state.name = name;
        state.observedPlayCount = game.playCount;
        state.observedBellCount = game.bellCount;
        state.waitUntil = waitUntil;
        return state;
    }

    static long sampleClampedMs(double meanSeconds, double stdSeconds, long minMs, long maxMs) {
        double sampled = stdSeconds == 0 ? meanSeconds : meanSeconds + gaussianRandom() * stdSeconds;
        long ms = Math.round(sampled * 1000);
        return Math.max(minMs, Math.min(maxMs, ms));
    }

    private static double gaussianRandom() {
        double u = 0;
        double v = 0;
        while (u == 0) {
            u = Math.random();
        }
        while (v == 0) {
            v = Math.random();
        }
        return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
    }
}
