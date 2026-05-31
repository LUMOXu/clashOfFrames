package com.lumoxu.cof.service;

import com.lumoxu.cof.engine.ActionResult;
import com.lumoxu.cof.engine.Game;
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
    private final Optional<GameTickBroadcaster> broadcaster;

    public GameTickOrchestrator(
            JsonRedisOps redis,
            GameRuntimeService gameRuntimeService,
            RoomService roomService,
            ComputerPlayerAdvanceService computerAdvanceService,
            Optional<GameTickBroadcaster> broadcaster) {
        this.redis = redis;
        this.gameRuntimeService = gameRuntimeService;
        this.roomService = roomService;
        this.computerAdvanceService = computerAdvanceService;
        this.broadcaster = broadcaster;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (String roomId : redis.setMembers(RedisKeys.ROOM_INDEX)) {
            roomService.get(roomId).ifPresent(room -> tickRoom(room, now));
        }
    }

    private void tickRoom(RoomState room, long now) {
        if (room.gameId == null || room.gameId.isBlank()) {
            return;
        }
        var bundleOpt = gameRuntimeService.get(room.gameId);
        if (bundleOpt.isEmpty()) {
            return;
        }
        Game game = bundleOpt.get().game;

        if ("finished".equals(game.status)) {
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

        boolean changed = false;
        ComputerTickOutcome computerOutcome = computerAdvanceService.advance(game, now);
        if (computerOutcome.changed) {
            changed = true;
        }

        ComputerTickOutcome broadcastOutcome = computerOutcome;
        if (!changed && game.lockedUntil <= now) {
            Player current = game.players.get(game.turnIndex);
            if (current != null
                    && !current.eliminated
                    && !current.exited
                    && !current.drawPile.isEmpty()
                    && now >= game.turnDeadlineAt) {
                ActionResult timeout = GameCore.performPlayCard(game, current.clientId, now, true);
                if (timeout.ok) {
                    changed = true;
                    broadcastOutcome = ComputerTickOutcome.of(true, true, false);
                }
            }
        }

        if (!changed) {
            return;
        }

        gameRuntimeService.save(game);
        PublicGame publicGame = gameRuntimeService.toPublicGame(game);
        ComputerTickOutcome outcome = broadcastOutcome;
        broadcaster.ifPresent(b -> b.onGameUpdated(room, publicGame, outcome));
    }
}
