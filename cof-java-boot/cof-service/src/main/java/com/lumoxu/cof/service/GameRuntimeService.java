package com.lumoxu.cof.service;

import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.engine.ActionResult;
import com.lumoxu.cof.engine.Card;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameCore;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.engine.Room;
import com.lumoxu.cof.service.model.GameStateBundle;
import com.lumoxu.cof.service.redis.JsonRedisOps;
import com.lumoxu.cof.service.redis.RedisKeys;
import org.springframework.stereotype.Service;

import java.util.List;
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

    public void tickAll() {
        // No-op scan without index; scheduler uses active games from RoomService in boot layer.
    }
}
