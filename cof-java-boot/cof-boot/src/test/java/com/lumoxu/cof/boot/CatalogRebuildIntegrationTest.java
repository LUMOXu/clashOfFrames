package com.lumoxu.cof.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.domain.entity.CofCard;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.mapper.CofCardMapper;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.service.CatalogRevisionHelper;
import com.lumoxu.cof.boot.support.ContractTestConfig;
import com.lumoxu.cof.service.DeckCatalogReviewService;
import com.lumoxu.cof.service.DeckCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end catalog rebuild: submit deck/pmv/card, approve, pending revision, public catalog.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ContractTestConfig.class)
class CatalogRebuildIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DeckCatalogReviewService reviewService;

    @Autowired
    DeckCatalogService catalogService;

    @Autowired
    CofDeckMapper deckMapper;

    @Autowired
    CofCardMapper cardMapper;

    @Test
    void submitApproveEditAndPlayableCatalog() throws Exception {
        String token = registerAndLogin("catalog_rebuild_user");

        MvcResult deckRes = mockMvc.perform(post("/api/v1/submissions/decks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"重构测试牌组\",\"description\":\"it\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long deckId = objectMapper.readTree(deckRes.getResponse().getContentAsString())
                .path("data").path("deck").path("id").asLong();

        mockMvc.perform(post("/api/v1/submissions/pmvs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"重构测试PMV\",\"author\":\"测试作者\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/submissions/pmvs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"重构测试PMV\",\"author\":\"重复\"}"))
                .andExpect(status().is4xxClientError());

        MvcResult pmvList = mockMvc.perform(get("/api/v1/submissions/pmvs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pmvs = objectMapper.readTree(pmvList.getResponse().getContentAsString())
                .path("data").path("pmvs");
        long pmvId = pmvs.get(0).path("id").asLong();

        byte[] jpeg = tinyJpeg();
        MvcResult cardRes = mockMvc.perform(multipart("/api/v1/submissions/decks/" + deckId + "/cards")
                        .file(new MockMultipartFile("file", "c.jpg", "image/jpeg", jpeg))
                        .param("pmvId", String.valueOf(pmvId))
                        .param("name", "镜头A")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long cardId = objectMapper.readTree(cardRes.getResponse().getContentAsString())
                .path("data").path("card").path("id").asLong();

        reviewService.approveDeck(deckId);
        reviewService.approvePmv(pmvId);
        reviewService.approveCard(cardId);

        mockMvc.perform(get("/api/v1/meta/card-libraries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.libraries[?(@.id=='" + deckId + "')]").exists());

        mockMvc.perform(patch("/api/v1/submissions/decks/" + deckId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"重构测试牌组-改\"}"))
                .andExpect(status().isOk());

        CofDeck deck = deckMapper.selectById(deckId);
        assertEquals("重构测试牌组", deck.name);
        assertTrue(CatalogRevisionHelper.hasPendingEdit(deck));
        assertEquals("重构测试牌组-改", deck.pendingName);

        reviewService.approveDeck(deckId);
        deck = deckMapper.selectById(deckId);
        assertEquals("重构测试牌组-改", deck.name);

        CofCard card = cardMapper.selectById(cardId);
        assertEquals(pmvId, card.pmvId.longValue());
        assertNotNull(catalogService.loadDeckBundle(deckId));
    }

    private String registerAndLogin(String username) throws Exception {
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

    private static byte[] tinyJpeg() throws Exception {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 64, 64);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}
