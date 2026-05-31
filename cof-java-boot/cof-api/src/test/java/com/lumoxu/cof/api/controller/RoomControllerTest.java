package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.service.GameRuntimeService;
import com.lumoxu.cof.service.RoomService;
import com.lumoxu.cof.service.model.RoomState;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RoomController.class)
@MvcTestConfig
class RoomControllerTest extends ControllerTestSupport {

    @MockBean
    private RoomService roomService;
    @MockBean
    private GameRuntimeService gameRuntimeService;

    @Test
    void listRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/rooms")).andExpect(status().isUnauthorized());
    }

    @Test
    void listOkWithToken() throws Exception {
        when(roomService.listRooms(anyString(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/rooms").header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void createOk() throws Exception {
        RoomState room = new RoomState();
        room.id = "r1";
        when(roomService.createRoom(anyString(), any(), anyList())).thenReturn(room);
        when(roomService.summary(any())).thenReturn(Map.of("id", "r1"));
        mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
