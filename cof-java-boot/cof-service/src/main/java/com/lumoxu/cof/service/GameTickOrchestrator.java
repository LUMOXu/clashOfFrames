package com.lumoxu.cof.service;

import com.lumoxu.cof.engine.ActionResult;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameConstants;
import com.lumoxu.cof.engine.GameCore;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.service.model.RoomState;
import com.lumoxu.cof.service.redis.RedisKeys;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GameTickOrchestrator {

    private final JsonRedisOps redis;
    private final GameRuntimeService gameRuntimeService;
    private final RoomService roomService;
    private final ComputerPlayerAdvanceService computerAdvanceService;
    private final RoomMaintenanceService roomMaintenanceService;
    private final UserStatsService userStatsService;
    private final Optional<GameTickBroadcaster> broadcaster;

    public GameTickOrchestrator(
            JsonRedisOps redis,
            GameRuntimeService gameRuntimeService,
            RoomService roomService,
            ComputerPlayerAdvanceService computerAdvanceService,
            RoomMaintenanceService roomMaintenanceService,
            UserStatsService userStatsService,
            Optional<GameTickBroadcaster> broadcaster) {
        this.redis = redis;
        this.gameRuntimeService = gameRuntimeService;
        this.roomService = roomService;
        this.computerAdvanceService = computerAdvanceService;
        this.roomMaintenanceService = roomMaintenanceService;
        this.userStatsService = userStatsService;
        this.broadcaster = broadcaster;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (String roomId : redis.setMembers(RedisKeys.ROOM_INDEX)) {
            roomService.get(roomId).ifPresent(room -> {
                if (roomMaintenanceService.maintain(room, now)) {
                    return;
                }
                tickRoom(room, now);
            });
        }
    }

    private void tickRoom(RoomState room, long now) {
        if (room.gameId == null || room.gameId.isBlank()) {
            if ("waiting".equals(room.status)) {
                boolean votesChanged = roomService.evaluateStartVotes(room, now);
                if (roomService.tryAutoStartFromVotes(room, now)) {
                    gameRuntimeService.get(room.gameId).ifPresent(bundle -> {
                        PublicGame publicGame = gameRuntimeService.toPublicGame(bundle.game);
                        broadcaster.ifPresent(b -> b.onGameUpdated(room, publicGame, ComputerTickOutcome.none()));
                    });
                } else if (votesChanged || room.startAt != null) {
                    roomService.save(room);
                }
            }
            return;
        }
        var bundleOpt = gameRuntimeService.get(room.gameId);
        if (bundleOpt.isEmpty()) {
            return;
        }
        Game game = bundleOpt.get().game;

        if ("finished".equals(game.status)) {
            if (userStatsService.recordFinishedGame(game)) {
                gameRuntimeService.save(game);
            }
            if (!"finished".equals(room.status)) {
                room.status = "finished";
                room.lastWinnerId = game.winnerId;
            }
            ContinueRoomResult continueResult = gameRuntimeService.maybeReturnToWaiting(room, game, now);
            if (continueResult.changed()) {
                roomService.save(room);
                PublicGame publicGame = gameRuntimeService.toPublicGame(game);
                broadcaster.ifPresent(b -> b.onContinueStateChanged(room, publicGame));
            }
            return;
        }

        if (!"playing".equals(game.status)) {
            return;
        }

        String statusBefore = game.status;
        ComputerTickOutcome computerOutcome = computerAdvanceService.advance(game, now);
        boolean changed = computerOutcome.played || computerOutcome.rang;
        ComputerTickOutcome broadcastOutcome = computerOutcome;

        if (!changed && applyTurnTimeoutIfDue(game, now)) {
            changed = true;
            broadcastOutcome = ComputerTickOutcome.of(true, true, false);
        }

        if (!changed) {
            return;
        }

        if ("finished".equals(game.status)) {
            userStatsService.recordFinishedGame(game);
        }
        gameRuntimeService.save(game);
        PublicGame publicGame = gameRuntimeService.toPublicGame(game);
        ComputerTickOutcome outcome = broadcastOutcome;
        outcome.justFinished = !"finished".equals(statusBefore) && "finished".equals(game.status);
        broadcaster.ifPresent(b -> b.onGameUpdated(room, publicGame, outcome));
    }

    /**
     * 与 old/server.js tick 一致：出牌截止时间到后自动出牌（含真人玩家）。
     * 仅在人机本 tick 未出牌/按铃时检查，避免 AI 状态机占位导致永不触发。
     */
    static boolean applyTurnTimeoutIfDue(Game game, long now) {
        if (game.lockedUntil > now) {
            return false;
        }
        Player current = game.players.get(game.turnIndex);
        if (current == null
                || current.eliminated
                || current.exited
                || current.drawPile.isEmpty()) {
            return false;
        }
        if (!current.connected
                && game.settings != null
                && game.settings.disconnectProtection) {
            long base = Math.max(game.turnStartedAt, game.turnAvailableAt);
            long disconnectedDeadline = base + GameConstants.DISCONNECTED_TURN_TIMEOUT_MS;
            if (game.turnDeadlineAt > disconnectedDeadline) {
                game.turnDeadlineAt = disconnectedDeadline;
            }
        }
        if (now < game.turnDeadlineAt) {
            return false;
        }
        ActionResult timeout = GameCore.performPlayCard(game, current.clientId, now, true);
        return timeout.ok;
    }
}
