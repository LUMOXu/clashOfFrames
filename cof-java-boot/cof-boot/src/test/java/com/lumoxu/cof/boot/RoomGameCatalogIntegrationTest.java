package com.lumoxu.cof.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.boot.support.ContractTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Room start + game deal using rebuilt catalog (deck id = library id, cof_pmv.id = pmvId).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContractTestConfig.class)
class RoomGameCatalogIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void startRoomGameWithSeedCatalog() throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String host = register("rgH" + suffix);
        String guest = register("rgG" + suffix);

        MvcResult create = mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + host)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settings": {
                                    "minPlayers": 2,
                                    "maxPlayers": 4,
                                    "isPublic": true,
                                    "libraryIds": ["1"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String roomId = objectMapper.readTree(create.getResponse().getContentAsString())
                .path("data").path("room").path("id").asText();

        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/join")
                        .header("Authorization", "Bearer " + guest))
                .andExpect(status().isOk());

        MvcResult start = mockMvc.perform(post("/api/v1/rooms/" + roomId + "/start")
                        .header("Authorization", "Bearer " + host))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.game.id").isNotEmpty())
                .andReturn();

        JsonNode game = objectMapper.readTree(start.getResponse().getContentAsString())
                .path("data").path("game");
        String gameId = game.path("id").asText();
        assertEquals("loading", game.path("status").asText());

        JsonNode players = game.path("players");
        assertTrue(players.size() >= 2);
        int hostDraw = players.get(0).path("drawCount").asInt();
        int guestDraw = players.get(1).path("drawCount").asInt();
        assertEquals(2, hostDraw, "seed deck has 4 cards → 2 per player");
        assertEquals(2, guestDraw);
        JsonNode hidden = players.get(0).path("drawPile").get(0);
        assertTrue(hidden.has("backUrl") || hidden.has("bk"), "hand cards are dealt face-down in public snapshot");

        mockMvc.perform(get("/api/v1/games/" + gameId)
                        .header("Authorization", "Bearer " + host))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.game.id").value(gameId));
    }

    private String register(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }
}
