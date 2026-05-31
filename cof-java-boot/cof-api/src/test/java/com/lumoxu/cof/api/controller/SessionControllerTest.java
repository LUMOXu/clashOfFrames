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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SessionController.class)
@MvcTestConfig
class SessionControllerTest extends ControllerTestSupport {

    @MockBean
    private MetaService metaService;
    @MockBean
    private ComputerPlayerService computerPlayerService;

    @Test
    void bootstrapOk() throws Exception {
        when(metaService.publicLibrariesPayload()).thenReturn(Map.of("libraries", List.of()));
        when(computerPlayerService.publicPlayers()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/session/bootstrap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
