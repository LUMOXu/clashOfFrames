package com.lumoxu.cof.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.engine.PublicCard;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.engine.PublicPlayer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSyncEncoderTest {

    private final GameSyncEncoder encoder = new GameSyncEncoder(new ObjectMapper());

    @Test
    void deltaPayloadGzipUnderOneKbForTypicalTick() throws Exception {
        PublicGame previous = samplePublicGame(8, 10);
        PublicGame current = samplePublicGame(8, 11);
        current.turnIndex = 3;
        current.playCount = 11;
        var delta = encoder.encodeDelta(previous, current);
        byte[] gzip = gzipJson(new ObjectMapper().writeValueAsBytes(delta));
        assertTrue(gzip.length <= 1024, "gzip delta bytes=" + gzip.length);
    }

    private static byte[] gzipJson(byte[] raw) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(raw);
        }
        return baos.toByteArray();
    }

    private static PublicGame samplePublicGame(int players, int playCount) {
        PublicGame game = new PublicGame();
        game.status = "playing";
        game.playCount = playCount;
        game.turnIndex = 2;
        game.bellCount = 1;
        game.settings = GameSettings.defaultSettings();
        game.players = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            PublicPlayer p = new PublicPlayer();
            p.clientId = "p" + i;
            p.username = "Player " + i;
            p.drawCount = 4;
            p.displayCount = i % 3;
            p.connected = true;
            p.eliminated = false;
            p.drawPile = List.of(backCard("d" + i));
            if (p.displayCount > 0) {
                PublicCard face = new PublicCard();
                face.id = "c" + i;
                face.pmvId = i % 5;
                face.imageUrl = "/cards/lib/cards/" + i + "a.jpg";
                face.backUrl = "/cards/lib/back.png";
                p.displayPile = List.of(face);
            } else {
                p.displayPile = List.of();
            }
            game.players.add(p);
        }
        return game;
    }

    private static PublicCard backCard(String id) {
        PublicCard c = new PublicCard();
        c.id = id;
        c.backUrl = "/cards/lib/back.png";
        c.libraryId = "lib";
        return c;
    }
}
