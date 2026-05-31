package com.lumoxu.cof.api.controller;

import com.lumoxu.cof.service.ComputerPlayerService;
import com.lumoxu.cof.service.MetaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MetaController.class)
@MvcTestConfig
class MetaControllerTest extends ControllerTestSupport {

    @MockBean
    private MetaService metaService;
    @MockBean
    private ComputerPlayerService computerPlayerService;

    @Test
    void cardLibrariesOk() throws Exception {
        when(metaService.publicLibrariesPayload()).thenReturn(Map.of("libraries", List.of()));
        mockMvc.perform(get("/api/v1/meta/card-libraries")).andExpect(status().isOk());
    }

    @Test
    void computerPlayersOk() throws Exception {
        when(computerPlayerService.publicPlayers()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/meta/computer-players")).andExpect(status().isOk());
    }
}
