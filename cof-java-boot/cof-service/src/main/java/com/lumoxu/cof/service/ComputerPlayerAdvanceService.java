package com.lumoxu.cof.service;

import com.lumoxu.cof.engine.ActionResult;
import com.lumoxu.cof.engine.ComputerState;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameCore;
import com.lumoxu.cof.engine.MatchInfo;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.service.model.ComputerPlayerDto;
import org.springframework.stereotype.Service;

/**
 * 人机状态机：开始 → 等待/出牌 → 分析 → 按铃/下一步 → …
 */
@Service
public class ComputerPlayerAdvanceService {

    private final ComputerPlayerService computerPlayerService;

    public ComputerPlayerAdvanceService(ComputerPlayerService computerPlayerService) {
        this.computerPlayerService = computerPlayerService;
    }

    private static final int MAX_CURRENT_COMPUTER_STEPS_PER_TICK = 16;

    public ComputerTickOutcome advance(Game game, long now) {
        Player current = game.players.isEmpty() ? null : game.players.get(game.turnIndex);
        if (current != null && current.isComputer && !current.eliminated && !current.exited) {
            ComputerPlayerDto profile = resolveProfile(current);
            if (profile != null) {
                ComputerTickOutcome fast = advanceCurrentComputerSteps(game, current, profile, now);
                if (fast.played || fast.rang) {
                    return fast;
                }
            }
        }
        for (Player player : game.players) {
            if (player == current) {
                continue;
            }
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
        return ComputerTickOutcome.none();
    }

    /**
     * 当前回合人机在同一 tick 内连续推进状态机（wait→analysis→next→play），避免每 100ms 只走一步导致长时间无出牌。
     */
    private ComputerTickOutcome advanceCurrentComputerSteps(
            Game game, Player player, ComputerPlayerDto profile, long now) {
        for (int step = 0; step < MAX_CURRENT_COMPUTER_STEPS_PER_TICK; step++) {
            String before = player.computerState != null ? player.computerState.name : "";
            Long beforeActionAt = player.computerState != null ? player.computerState.actionAt : null;
            ComputerTickOutcome outcome = advanceOne(game, player, profile, now);
            if (outcome.played || outcome.rang) {
                return outcome;
            }
            String after = player.computerState != null ? player.computerState.name : "";
            Long afterActionAt = player.computerState != null ? player.computerState.actionAt : null;
            if (before.equals(after) && java.util.Objects.equals(beforeActionAt, afterActionAt)) {
                break;
            }
        }
        return ComputerTickOutcome.none();
    }

    private ComputerTickOutcome advanceOne(Game game, Player player, ComputerPlayerDto profile, long now) {
        if (player.computerState == null) {
            player.computerState = freshState("start", game);
        }
        String state = player.computerState.name;

        if ("start".equals(state)) {
            if (!tableHasDisplayCards(game) && canComputerPlay(game, player, now)) {
                player.computerState = freshState("play", game);
            } else {
                player.computerState = freshState("wait", game);
            }
            return ComputerTickOutcome.none();
        }

        if ("wait".equals(state)) {
            if (game.playCount != player.computerState.observedPlayCount) {
                player.computerState = freshState("analysis", game);
            } else if (canComputerPlay(game, player, now)) {
                player.computerState = freshState("play", game);
            }
            return ComputerTickOutcome.none();
        }

        if ("play".equals(state)) {
            if (game.bellCount != player.computerState.observedBellCount) {
                player.computerState = settlingOrNext(game, now);
                return ComputerTickOutcome.none();
            }
            if (game.playCount != player.computerState.observedPlayCount) {
                player.computerState = freshState("analysis", game);
                return ComputerTickOutcome.none();
            }
            if (!canComputerPlay(game, player, now)) {
                if (!isCurrentPlayer(game, player)) {
                    player.computerState = freshState("analysis", game);
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
                player.computerState = freshState("analysis", game);
                if (played.ok) {
                    return ComputerTickOutcome.of(true, true, false);
                }
                player.computerState.actionAt = null;
            }
            return ComputerTickOutcome.none();
        }

        if ("analysis".equals(state)) {
            MatchInfo match = GameCore.findCurrentMatch(game);
            boolean shouldRing = match != null
                    ? Math.random() < profile.matchDetectionProbability
                    : Math.random() < profile.falseRingProbability;
            if (shouldRing) {
                ComputerState ring = freshState("ring", game);
                ring.actionAt = now + sampleClampedMs(
                        profile.reactionMeanSeconds,
                        profile.reactionStdSeconds,
                        100,
                        Integer.MAX_VALUE);
                player.computerState = ring;
            } else {
                player.computerState = freshState("next", game);
            }
            return ComputerTickOutcome.none();
        }

        if ("ring".equals(state)) {
            if (game.bellCount != player.computerState.observedBellCount) {
                player.computerState = settlingOrNext(game, now);
                return ComputerTickOutcome.none();
            }
            if (game.playCount != player.computerState.observedPlayCount) {
                player.computerState = freshState("analysis", game);
                return ComputerTickOutcome.none();
            }
            if (game.lockedUntil > now) {
                player.computerState = freshState("settling", game, game.lockedUntil);
                return ComputerTickOutcome.none();
            }
            if (player.computerState.actionAt != null && now >= player.computerState.actionAt) {
                ActionResult rang = GameCore.performRingBell(game, player.clientId, now, Math::random);
                if (rang.ok && game.lockedUntil > now) {
                    player.computerState = freshState("settling", game, game.lockedUntil);
                } else {
                    player.computerState = freshState("next", game);
                }
                if (rang.ok) {
                    return ComputerTickOutcome.of(true, false, true);
                }
            }
            return ComputerTickOutcome.none();
        }

        if ("settling".equals(state)) {
            long waitUntil = player.computerState.waitUntil != null ? player.computerState.waitUntil : 0L;
            if (now >= waitUntil) {
                player.computerState = freshState("next", game);
            }
            return ComputerTickOutcome.none();
        }

        if ("next".equals(state)) {
            player.computerState = freshState(canComputerPlay(game, player, now) ? "play" : "wait", game);
            return ComputerTickOutcome.none();
        }

        player.computerState = freshState("wait", game);
        return ComputerTickOutcome.none();
    }

    private static ComputerState settlingOrNext(Game game, long now) {
        return game.lockedUntil > now ? freshState("settling", game, game.lockedUntil) : freshState("next", game);
    }

    private static boolean isCurrentPlayer(Game game, Player player) {
        Player current = game.players.get(game.turnIndex);
        return current != null && player.clientId.equals(current.clientId);
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
        if (!isCurrentPlayer(game, player)) {
            return false;
        }
        return !player.eliminated && !player.exited && !player.drawPile.isEmpty() && now >= game.turnAvailableAt;
    }

    private static ComputerState freshState(String name, Game game) {
        return freshState(name, game, null);
    }

    private static ComputerState freshState(String name, Game game, Long waitUntil) {
        ComputerState state = new ComputerState();
        state.name = name;
        state.observedPlayCount = game.playCount;
        state.observedBellCount = game.bellCount;
        state.waitUntil = waitUntil;
        state.actionAt = null;
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
