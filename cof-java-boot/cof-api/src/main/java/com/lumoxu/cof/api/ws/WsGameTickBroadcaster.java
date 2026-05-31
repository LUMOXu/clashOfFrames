package com.lumoxu.cof.api.ws;

import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.service.ComputerTickOutcome;
import com.lumoxu.cof.service.GameTickBroadcaster;
import com.lumoxu.cof.service.model.RoomState;
import org.springframework.stereotype.Component;

@Component
public class WsGameTickBroadcaster implements GameTickBroadcaster {

    private final WsBroadcastService broadcastService;

    public WsGameTickBroadcaster(WsBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @Override
    public void onGameUpdated(RoomState room, PublicGame game, ComputerTickOutcome outcome) {
        try {
            if (outcome.played) {
                broadcastService.broadcastAudio(room.id, game.id, "play-card");
            }
            if (outcome.rang) {
                broadcastService.broadcastAudio(room.id, game.id, "ring-bell");
            }
            broadcastService.broadcastGameSync(game);
        } catch (Exception ex) {
            // 广播失败不应影响 tick 持久化
        }
    }

    @Override
    public void onContinueStateChanged(RoomState room, PublicGame game) {
        try {
            broadcastService.broadcastRoom(room);
            broadcastService.broadcastGameSync(game);
        } catch (Exception ex) {
            // ignore
        }
    }
}
