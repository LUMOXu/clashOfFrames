package com.lumoxu.cof.service;

import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.engine.ActionResult;
import com.lumoxu.cof.engine.Card;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameCore;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.engine.Room;
import com.lumoxu.cof.service.model.GameStateBundle;
import com.lumoxu.cof.service.model.RoomState;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import com.lumoxu.cof.service.redis.RedisKeys;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GameRuntimeService {

    private final JsonRedisOps redis;

    public GameRuntimeService(JsonRedisOps redis) {
        this.redis = redis;
    }

    public Game createGame(Room room, List<Player> players, List<Card> cards) {
        Game game = GameCore.createGame(room, players, cards);
        save(game);
        return game;
    }

    public GameStateBundle getRequired(String gameId) {
        return get(gameId).orElseThrow(() -> new CofException(ErrorCode.NOT_FOUND, "对局不存在。"));
    }

    public Optional<GameStateBundle> get(String gameId) {
        return redis.get(RedisKeys.game(gameId), GameStateBundle.class);
    }

    public Game save(Game game) {
        redis.set(RedisKeys.game(game.id), new GameStateBundle(game, System.currentTimeMillis()), null);
        return game;
    }

    public PublicGame playCard(String gameId, String clientId) {
        GameStateBundle bundle = getRequired(gameId);
        ActionResult result = GameCore.performPlayCard(bundle.game, clientId, System.currentTimeMillis(), false);
        if (!result.ok) {
            throw new CofException(ErrorCode.CONFLICT, result.error);
        }
        save(bundle.game);
        return toPublicGame(bundle.game);
    }

    public PublicGame ringBell(String gameId, String clientId) {
        GameStateBundle bundle = getRequired(gameId);
        ActionResult result = GameCore.performRingBell(bundle.game, clientId, System.currentTimeMillis(), Math::random);
        if (!result.ok) {
            throw new CofException(ErrorCode.CONFLICT, result.error);
        }
        save(bundle.game);
        return toPublicGame(bundle.game);
    }

    public PublicGame toPublicGame(Game game) {
        return GameCore.publicGame(game);
    }

    public PublicGame updateLoadingProgress(String gameId, String clientId, Map<String, Object> body, RoomState room) {
        GameStateBundle bundle = getRequired(gameId);
        Game game = bundle.game;
        if ("playing".equals(game.status) || "finished".equals(game.status)) {
            return toPublicGame(game);
        }
        if (!"loading".equals(game.status)) {
            throw new CofException(ErrorCode.CONFLICT, "当前房间不在加载阶段。");
        }
        Player player = game.players.stream()
                .filter(p -> clientId.equals(p.clientId))
                .findFirst()
                .orElseThrow(() -> new CofException(ErrorCode.FORBIDDEN, "你不在这局游戏中。"));
        int reportedTotal = body.get("total") instanceof Number n ? n.intValue() : 0;
        int reportedLoaded = body.get("loaded") instanceof Number n ? n.intValue() : 0;
        boolean cached = Boolean.TRUE.equals(body.get("cached"));
        boolean done = Boolean.TRUE.equals(body.get("done"));
        int total = Math.max(player.loadingTotal, Math.max(0, reportedTotal));
        int loaded = Math.max(player.loadingLoaded, Math.min(total > 0 ? total : Integer.MAX_VALUE, Math.max(0, reportedLoaded)));
        player.loadingTotal = total;
        player.loadingLoaded = loaded;
        player.loadingProgress = total > 0 ? Math.round((loaded * 100f) / total) : 0;
        player.loadingCached = player.loadingCached || cached;
        player.loadingStartedAt = player.loadingStartedAt == null ? System.currentTimeMillis() : player.loadingStartedAt;
        if (done || (total > 0 && loaded >= total)) {
            player.loadingLoaded = total > 0 ? total : loaded;
            player.loadingProgress = 100;
            player.ready = true;
            player.loadingFinishedAt = System.currentTimeMillis();
        }
        save(game);
        advanceLoading(room, game);
        return toPublicGame(game);
    }

    public void advanceLoading(RoomState room, Game game) {
        if (game.settings == null) {
            game.settings = room.settings != null ? room.settings : GameSettings.defaultSettings();
        }
        java.util.Set<String> expected = new java.util.HashSet<>(room.players);
        List<Player> expectedPlayers = game.players.stream()
                .filter(p -> expected.contains(p.clientId) && !p.exited)
                .toList();
        if (expectedPlayers.isEmpty()) {
            room.status = "waiting";
            room.gameId = null;
            game.status = "aborted";
            save(game);
            return;
        }
        boolean allReady = expectedPlayers.stream().allMatch(p -> p.ready || p.isComputer);
        if (allReady) {
            room.status = "playing";
            GameCore.startPlaying(game, System.currentTimeMillis());
            save(game);
        }
    }

    public PublicGame continueGame(String gameId, String clientId, RoomState room) {
        GameStateBundle bundle = getRequired(gameId);
        Game game = bundle.game;
        if (!"finished".equals(game.status)) {
            throw new CofException(ErrorCode.CONFLICT, "对局尚未结束。");
        }
        if (!game.continueVotes.contains(clientId)) {
            game.continueVotes.add(clientId);
        }
        maybeReturnToWaiting(room, game, System.currentTimeMillis());
        return toPublicGame(game);
    }

    /**
     * 与 old/server.js maybeReturnToWaiting 一致：达票数启动倒计时，倒计时结束后回到等待室。
     */
    public ContinueRoomResult maybeReturnToWaiting(RoomState room, Game game, long now) {
        if (!"finished".equals(game.status)) {
            return ContinueRoomResult.none();
        }
        List<String> humans = game.players.stream()
                .filter(p -> !p.isComputer && p.connected && !p.exited)
                .map(p -> p.clientId)
                .toList();
        List<String> validVotes = game.continueVotes.stream().filter(humans::contains).toList();
        int threshold = Math.max(1, humans.size() / 2 + 1);

        if (game.continueReturnAt == null) {
            if (validVotes.size() < threshold) {
                save(game);
                return ContinueRoomResult.none();
            }
            game.continueCountdownStartedAt = now;
            game.continueReturnAt = now + 10_000L;
            if (room.gameId != null && game.winnerId != null) {
                room.status = "finished";
                room.lastWinnerId = game.winnerId;
            }
            save(game);
            return ContinueRoomResult.countdownStarted();
        }

        if (now < game.continueReturnAt && validVotes.size() < humans.size()) {
            save(game);
            return ContinueRoomResult.none();
        }

        applyReturnToWaiting(room, game, validVotes);
        save(game);
        return ContinueRoomResult.returnedToWaiting();
    }

    private void applyReturnToWaiting(RoomState room, Game game, List<String> validVotes) {
        room.status = "waiting";
        room.gameId = null;
        room.players = new java.util.ArrayList<>(validVotes);
        for (Player p : game.players) {
            if (p.isComputer && !p.exited && !room.players.contains(p.clientId)) {
                room.players.add(p.clientId);
            }
        }
        room.spectators.clear();
        room.startVotes.clear();
        room.startAt = null;
        room.startCountdownStartedAt = null;
        game.continueVotes.clear();
        game.continueCountdownStartedAt = null;
        game.continueReturnAt = null;
    }

}
