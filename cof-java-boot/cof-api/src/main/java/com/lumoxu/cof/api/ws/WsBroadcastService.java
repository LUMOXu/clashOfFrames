package com.lumoxu.cof.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.common.ws.WsMessage;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.service.RoomService;
import com.lumoxu.cof.service.model.RoomState;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

@Service
public class WsBroadcastService {

    private final ObjectMapper objectMapper;
    private final GameSyncTracker syncTracker;
    private final WsSessionRegistry registry;
    private final RoomService roomService;

    public WsBroadcastService(
            ObjectMapper objectMapper,
            GameSyncTracker syncTracker,
            WsSessionRegistry registry,
            RoomService roomService) {
        this.objectMapper = objectMapper;
        this.syncTracker = syncTracker;
        this.registry = registry;
        this.roomService = roomService;
    }

    public void registerSession(WebSocketSession session, String roomId, String gameId) {
        registry.bind(session, roomId, gameId);
    }

    public void unregisterSession(WebSocketSession session) {
        syncTracker.clearSession(session.getId());
        registry.unbind(session);
    }

    public void resetSessionSync(WebSocketSession session) {
        syncTracker.resetSession(session.getId());
    }

    public void resetClientSync(String clientId, String gameId) {
        syncTracker.resetClient(clientId, gameId);
    }

    public void broadcastRoom(RoomState room) throws Exception {
        WsMessage roomMsg = WsMessage.ofType("ROOM");
        roomMsg.r = room.id;
        roomMsg.room = objectMapper.valueToTree(roomService.summary(room));
        String json = objectMapper.writeValueAsString(roomMsg);
        registry.broadcastRoom(room.id, json);
        if (room.gameId != null) {
            registry.broadcastGame(room.gameId, json);
        }
    }

    public void broadcastGameSync(PublicGame game) throws Exception {
        if (game == null || game.id == null) {
            return;
        }
        for (WebSocketSession session : registry.gameSessions(game.id)) {
            if (!session.isOpen()) {
                continue;
            }
            WsMessage sync = WsMessage.ofType("SYNC");
            sync.g = game.id;
            sync.r = game.roomId;
            sync.ti = game.turnIndex;
            sync.pc = Integer.toString(game.playCount);
            sync.sync = syncTracker.publishForSession(session.getId(), game);
            registry.sendToSession(session, objectMapper.writeValueAsString(sync));
        }
    }

    public void broadcastAudio(String roomId, String gameId, String audioType) throws Exception {
        WsMessage audio = WsMessage.ofType("AUDIO");
        audio.au = audioType;
        audio.r = roomId;
        audio.g = gameId;
        String json = objectMapper.writeValueAsString(audio);
        registry.broadcastRoomAndGame(roomId, gameId, json);
    }
}
