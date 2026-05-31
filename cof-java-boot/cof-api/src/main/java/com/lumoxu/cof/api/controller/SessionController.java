package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.api.auth.AuthContext;
import com.lumoxu.cof.common.api.ApiResponse;
import com.lumoxu.cof.common.auth.TokenPayload;
import com.lumoxu.cof.service.ComputerPlayerService;
import com.lumoxu.cof.service.GameRuntimeService;
import com.lumoxu.cof.service.MetaService;
import com.lumoxu.cof.service.RoomService;
import com.lumoxu.cof.service.model.RoomState;
import com.lumoxu.cof.engine.PublicGame;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/session")
public class SessionController {

    private final MetaService metaService;
    private final ComputerPlayerService computerPlayerService;
    private final RoomService roomService;
    private final GameRuntimeService gameRuntimeService;

    public SessionController(
            MetaService metaService,
            ComputerPlayerService computerPlayerService,
            RoomService roomService,
            GameRuntimeService gameRuntimeService) {
        this.metaService = metaService;
        this.computerPlayerService = computerPlayerService;
        this.roomService = roomService;
        this.gameRuntimeService = gameRuntimeService;
    }

    @GetMapping("/bootstrap")
    public ApiResponse<Map<String, Object>> bootstrap(HttpServletRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("libraries", metaService.publicLibrariesPayload().get("libraries"));
        payload.put("computerPlayers", computerPlayerService.listPlayers());
        TokenPayload auth = AuthContext.get();
        if (auth != null) {
            String clientId = auth.clientId.toString();
            Map<String, Object> me = new HashMap<>();
            me.put("clientId", clientId);
            me.put("username", auth.username);
            payload.put("player", me);
            payload.put("rooms", roomService.listRooms(clientId, false));
            roomService.findRoomForPlayer(clientId).ifPresent(room -> {
                payload.put("currentRoom", roomService.summary(room));
                if (room.gameId != null) {
                    gameRuntimeService.get(room.gameId).ifPresent(bundle -> {
                        PublicGame game = gameRuntimeService.toPublicGame(bundle.game);
                        payload.put("currentGame", game);
                    });
                }
            });
        }
        return ApiResponse.ok(payload);
    }
}
