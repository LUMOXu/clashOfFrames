package com.lumoxu.cof.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.common.ws.WsMessage;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.service.RoomService;
import com.lumoxu.cof.service.model.RoomState;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WsBroadcastService {

    private final ObjectMapper objectMapper;
    private final GameSyncEncoder syncEncoder;
    private final WsSessionRegistry registry;
    private final RoomService roomService;
    private final Map<String, PublicGame> lastGameSnapshots = new ConcurrentHashMap<>();

    public WsBroadcastService(
            ObjectMapper objectMapper,
            GameSyncEncoder syncEncoder,
            WsSessionRegistry registry,
            RoomService roomService) {
        this.objectMapper = objectMapper;
        this.syncEncoder = syncEncoder;
        this.registry = registry;
        this.roomService = roomService;
    }

    public void registerSession(org.springframework.web.socket.WebSocketSession session, String roomId, String gameId) {
        registry.bind(session, roomId, gameId);
    }

    public void unregisterSession(org.springframework.web.socket.WebSocketSession session) {
        registry.unbind(session);
        lastGameSnapshots.remove(session.getId());
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
        lastGameSnapshots.put(game.id, game);
        WsMessage sync = WsMessage.ofType("SYNC");
        sync.g = game.id;
        sync.r = game.roomId;
        sync.ti = game.turnIndex;
        sync.pc = Integer.toString(game.playCount);
        sync.sync = objectMapper.createObjectNode().set("full", objectMapper.valueToTree(game));
        String json = objectMapper.writeValueAsString(sync);
        registry.broadcastGame(game.id, json);
        if (game.roomId != null) {
            registry.broadcastRoom(game.roomId, json);
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
