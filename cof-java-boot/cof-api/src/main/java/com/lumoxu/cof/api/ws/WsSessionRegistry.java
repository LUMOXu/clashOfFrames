package com.lumoxu.cof.api.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class WsSessionRegistry {

    private final Map<String, Set<WebSocketSession>> byRoom = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> byGame = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRoom = new ConcurrentHashMap<>();
    private final Map<String, String> sessionGame = new ConcurrentHashMap<>();

    public void bind(WebSocketSession session, String roomId, String gameId) {
        if (roomId != null && !roomId.isBlank()) {
            byRoom.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(session);
            sessionRoom.put(session.getId(), roomId);
        }
        if (gameId != null && !gameId.isBlank()) {
            byGame.computeIfAbsent(gameId, k -> new CopyOnWriteArraySet<>()).add(session);
            sessionGame.put(session.getId(), gameId);
        }
    }

    public void unbind(WebSocketSession session) {
        String roomId = sessionRoom.remove(session.getId());
        if (roomId != null) {
            Set<WebSocketSession> set = byRoom.get(roomId);
            if (set != null) {
                set.remove(session);
            }
        }
        String gameId = sessionGame.remove(session.getId());
        if (gameId != null) {
            Set<WebSocketSession> set = byGame.get(gameId);
            if (set != null) {
                set.remove(session);
            }
        }
    }

    public void broadcastRoom(String roomId, String json) {
        broadcast(byRoom.get(roomId), json);
    }

    public void broadcastGame(String gameId, String json) {
        broadcast(byGame.get(gameId), json);
    }

    public void broadcastRoomAndGame(String roomId, String gameId, String json) {
        broadcastRoom(roomId, json);
        if (gameId != null && !gameId.isBlank()) {
            broadcastGame(gameId, json);
        }
    }

    private static void broadcast(Set<WebSocketSession> sessions, String json) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException ignored) {
                    // drop broken sessions
                }
            }
        }
    }
}
