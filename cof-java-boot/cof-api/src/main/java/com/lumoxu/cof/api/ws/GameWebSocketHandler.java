package com.lumoxu.cof.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.common.auth.TokenPayload;
import com.lumoxu.cof.common.ws.WsMessage;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.service.GameRuntimeService;
import com.lumoxu.cof.service.RoomService;
import com.lumoxu.cof.service.SessionService;
import com.lumoxu.cof.service.model.RoomState;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final GameRuntimeService gameRuntimeService;
    private final RoomService roomService;
    private final WsBroadcastService broadcastService;

    public GameWebSocketHandler(
            ObjectMapper objectMapper,
            SessionService sessionService,
            GameRuntimeService gameRuntimeService,
            RoomService roomService,
            WsBroadcastService broadcastService) {
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.gameRuntimeService = gameRuntimeService;
        this.roomService = roomService;
        this.broadcastService = broadcastService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WsMessage incoming = objectMapper.readValue(message.getPayload(), WsMessage.class);
        if (incoming == null || incoming.t == null) {
            return;
        }
        TokenPayload auth = sessionService.requireToken((String) session.getAttributes().get("token"));
        switch (incoming.t.toUpperCase()) {
            case "PING" -> send(session, WsMessage.ofType("PING"));
            case "LOAD" -> handleLoad(session, incoming, auth);
            case "PLAY" -> handlePlay(incoming, auth);
            case "RING" -> handleRing(incoming, auth);
            default -> {
                WsMessage err = WsMessage.ofType("ERR");
                err.err = "unknown type";
                send(session, err);
            }
        }
    }

    private void handleLoad(WebSocketSession session, WsMessage incoming, TokenPayload auth) throws Exception {
        String roomId = incoming.r;
        String gameId = incoming.g;
        if (roomId == null) {
            roomService.findRoomForPlayer(auth.clientId.toString()).ifPresent(room -> {
                session.getAttributes().put("roomId", room.id);
            });
            roomId = (String) session.getAttributes().get("roomId");
        }
        if (gameId == null && roomId != null) {
            RoomState room = roomService.get(roomId).orElse(null);
            if (room != null) {
                gameId = room.gameId;
            }
        }
        broadcastService.registerSession(session, roomId, gameId);
        if (roomId != null) {
            RoomState room = roomService.getRequired(roomId);
            broadcastService.broadcastRoom(room);
        }
        if (gameId != null) {
            PublicGame game = gameRuntimeService.toPublicGame(gameRuntimeService.getRequired(gameId).game);
            broadcastService.broadcastGameSync(game);
        }
    }

    private void handlePlay(WsMessage incoming, TokenPayload auth) throws Exception {
        PublicGame game = gameRuntimeService.playCard(incoming.g, auth.clientId.toString());
        broadcastService.broadcastGameSync(game);
    }

    private void handleRing(WsMessage incoming, TokenPayload auth) throws Exception {
        PublicGame game = gameRuntimeService.ringBell(incoming.g, auth.clientId.toString());
        broadcastService.broadcastGameSync(game);
        broadcastService.broadcastAudio(game.roomId, game.id, "ring-bell");
    }

    private void send(WebSocketSession session, WsMessage message) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcastService.unregisterSession(session);
    }
}
