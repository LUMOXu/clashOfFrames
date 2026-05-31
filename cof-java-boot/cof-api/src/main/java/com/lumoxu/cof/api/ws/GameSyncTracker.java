package com.lumoxu.cof.api.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.engine.PublicGame;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameSyncTracker {

    private final GameSyncEncoder encoder;
    private final ObjectMapper objectMapper;
    private final Map<String, PublicGame> bySession = new ConcurrentHashMap<>();
    private final Map<String, PublicGame> byClientGame = new ConcurrentHashMap<>();

    public GameSyncTracker(GameSyncEncoder encoder, ObjectMapper objectMapper) {
        this.encoder = encoder;
        this.objectMapper = objectMapper;
    }

    public JsonNode publishForClient(String clientId, String gameId, PublicGame current) {
        String key = clientGameKey(clientId, gameId);
        PublicGame previous = byClientGame.get(key);
        JsonNode delta = encoder.encodeDelta(previous, current);
        byClientGame.put(key, clone(current));
        return delta;
    }

    public JsonNode publishForSession(String sessionId, PublicGame current) {
        PublicGame previous = bySession.get(sessionId);
        JsonNode delta = encoder.encodeDelta(previous, current);
        bySession.put(sessionId, clone(current));
        return delta;
    }

    public void resetClient(String clientId, String gameId) {
        if (clientId != null && gameId != null) {
            byClientGame.remove(clientGameKey(clientId, gameId));
        }
    }

    public void resetSession(String sessionId) {
        if (sessionId != null) {
            bySession.remove(sessionId);
        }
    }

    public void clearSession(String sessionId) {
        resetSession(sessionId);
    }

    private static String clientGameKey(String clientId, String gameId) {
        return clientId + ":" + gameId;
    }

    private PublicGame clone(PublicGame game) {
        if (game == null) {
            return null;
        }
        return objectMapper.convertValue(game, PublicGame.class);
    }
}
