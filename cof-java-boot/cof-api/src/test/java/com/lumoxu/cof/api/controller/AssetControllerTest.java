package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.service.MetaService;
import com.lumoxu.cof.service.RoomService;
import com.lumoxu.cof.service.model.RoomState;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.nio.file.Path;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AssetController.class)
@MvcTestConfig
class AssetControllerTest extends ControllerTestSupport {

    @MockBean
    private MetaService metaService;
    @MockBean
    private RoomService roomService;

    @Test
    void roomAssetsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/assets/rooms/r1")).andExpect(status().isUnauthorized());
    }

    @Test
    void roomAssetsOk() throws Exception {
        RoomState room = new RoomState();
        room.id = "r1";
        when(roomService.getRequired("r1")).thenReturn(room);
        when(roomService.assetManifest(any())).thenReturn(Map.of("libraries", java.util.List.of()));
        mockMvc.perform(get("/api/v1/assets/rooms/r1").header("Authorization", bearer()))
                .andExpect(status().isOk());
    }
}
