package com.lumoxu.cof.api.ws;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final GameRuntimeService gameRuntimeService;
    private final RoomService roomService;
    private final GameSyncEncoder syncEncoder;
    private final Map<String, PublicGame> lastSnapshots = new ConcurrentHashMap<>();

    public GameWebSocketHandler(
            ObjectMapper objectMapper,
            SessionService sessionService,
            GameRuntimeService gameRuntimeService,
            RoomService roomService,
            GameSyncEncoder syncEncoder) {
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.gameRuntimeService = gameRuntimeService;
        this.roomService = roomService;
        this.syncEncoder = syncEncoder;
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
            case "PLAY" -> handlePlay(session, incoming, auth);
            case "RING" -> handleRing(session, incoming, auth);
            default -> {
                WsMessage err = WsMessage.ofType("ERR");
                err.err = "unknown type";
                send(session, err);
            }
        }
    }

    private void handleLoad(WebSocketSession session, WsMessage incoming, TokenPayload auth) throws Exception {
        if (incoming.r != null) {
            RoomState room = roomService.getRequired(incoming.r);
            WsMessage roomMsg = WsMessage.ofType("ROOM");
            roomMsg.r = room.id;
            roomMsg.room = objectMapper.valueToTree(roomService.summary(room));
            send(session, roomMsg);
        }
        if (incoming.g != null) {
            PublicGame game = gameRuntimeService.toPublicGame(gameRuntimeService.getRequired(incoming.g).game);
            pushSync(session, game);
        }
    }

    private void handlePlay(WebSocketSession session, WsMessage incoming, TokenPayload auth) throws Exception {
        PublicGame game = gameRuntimeService.playCard(incoming.g, auth.clientId.toString());
        pushSync(session, game);
    }

    private void handleRing(WebSocketSession session, WsMessage incoming, TokenPayload auth) throws Exception {
        PublicGame game = gameRuntimeService.ringBell(incoming.g, auth.clientId.toString());
        pushSync(session, game);
        WsMessage audio = WsMessage.ofType("AUDIO");
        audio.au = "ring-bell";
        audio.g = game.id;
        audio.r = game.roomId;
        send(session, audio);
    }

    public void pushSync(WebSocketSession session, PublicGame game) throws Exception {
        PublicGame previous = lastSnapshots.put(session.getId(), game);
        WsMessage sync = WsMessage.ofType("SYNC");
        sync.g = game.id;
        sync.ti = game.turnIndex;
        sync.pc = Integer.toString(game.playCount);
        sync.sync = syncEncoder.encodeDelta(previous, game);
        send(session, sync);
    }

    private void send(WebSocketSession session, WsMessage message) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        lastSnapshots.remove(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        lastSnapshots.remove(session.getId());
    }
}
