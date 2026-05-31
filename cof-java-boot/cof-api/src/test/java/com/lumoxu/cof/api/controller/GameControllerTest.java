package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.api.ws.WsBroadcastService;
import com.lumoxu.cof.service.GameRuntimeService;
import com.lumoxu.cof.service.RoomService;
import com.lumoxu.cof.service.model.GameStateBundle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GameController.class)
@MvcTestConfig
class GameControllerTest extends ControllerTestSupport {

    @MockBean
    private GameRuntimeService gameRuntimeService;
    @MockBean
    private RoomService roomService;
    @MockBean
    private WsBroadcastService broadcastService;

    @Test
    void getUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/games/g1")).andExpect(status().isUnauthorized());
    }

    @Test
    void getOk() throws Exception {
        Game game = new Game();
        game.id = "g1";
        when(gameRuntimeService.getRequired("g1")).thenReturn(new GameStateBundle(game, 1));
        when(gameRuntimeService.toPublicGame(game)).thenReturn(new PublicGame());
        mockMvc.perform(get("/api/v1/games/g1").header("Authorization", bearer()))
                .andExpect(status().isOk());
    }
}
