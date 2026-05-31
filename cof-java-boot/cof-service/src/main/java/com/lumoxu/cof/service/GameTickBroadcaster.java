package com.lumoxu.cof.service;

import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.service.model.RoomState;

public interface GameTickBroadcaster {

    void onGameUpdated(RoomState room, PublicGame game, ComputerTickOutcome outcome);

    void onContinueStateChanged(RoomState room, PublicGame game);
}
