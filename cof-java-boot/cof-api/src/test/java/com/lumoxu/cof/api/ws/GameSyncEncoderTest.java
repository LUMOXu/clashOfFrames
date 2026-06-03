package com.lumoxu.cof.api.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumoxu.cof.engine.ActionResult;
import com.lumoxu.cof.engine.Card;
import com.lumoxu.cof.engine.Game;
import com.lumoxu.cof.engine.GameCore;
import com.lumoxu.cof.engine.GameLog;
import com.lumoxu.cof.engine.GameSettings;
import com.lumoxu.cof.engine.Player;
import com.lumoxu.cof.engine.PublicCard;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.engine.PublicPlayer;
import com.lumoxu.cof.engine.Room;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSyncEncoderTest {

    private final GameSyncEncoder encoder = new GameSyncEncoder(new ObjectMapper());

    @Test
    void playCardDeltaJsonUnderTwoKb() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Game game = freshTwoPlayerGame();
        PublicGame before = GameCore.publicGame(game);
        String actor = game.players.get(game.turnIndex).clientId;
        ActionResult result = GameCore.performPlayCard(game, actor, 2_000L, false);
        assertTrue(result.ok, () -> "play failed: " + result.error);
        PublicGame after = GameCore.publicGame(game);
        JsonNode delta = encoder.encodeDelta(before, after);
        int bytes = mapper.writeValueAsBytes(delta).length;
        assertEquals("play", delta.path("ev").asText());
        assertFalse(delta.has("pl"), "play-card delta must not include player pile patches");
        assertFalse(delta.has("pt"), "play-card delta must not include table top patches");
        assertTrue(bytes < 512, "play-card delta json bytes=" + bytes);
        assertTrue(gzipJson(mapper.writeValueAsBytes(delta)).length < 512);
    }

    @Test
    void playCardDeltaIncludesNewestLog() {
        Game game = freshTwoPlayerGame();
        PublicGame before = GameCore.publicGame(game);
        String actor = game.players.get(game.turnIndex).clientId;

        ActionResult result = GameCore.performPlayCard(game, actor, 2_000L, false);
        assertTrue(result.ok, () -> "play failed: " + result.error);

        JsonNode delta = encoder.encodeDelta(before, GameCore.publicGame(game));
        assertEquals("play", delta.path("ev").asText());
        assertEquals(1, delta.path("lg").size());
        assertTrue(delta.path("lg").get(0).path("t").asText().contains("Player"));
        assertTrue(delta.path("lg").get(0).has("id"));
    }

    @Test
    void prependedLogDeltaEmitsNewestLog() {
        PublicGame previous = samplePublicGame(2, 4);
        previous.logs = List.of(log("old", "old", 100L));
        PublicGame current = samplePublicGame(2, 4);
        current.logs = List.of(log("new", "new", 200L), log("old", "old", 100L));

        JsonNode delta = encoder.encodeDelta(previous, current);

        assertEquals(1, delta.path("lg").size());
        assertEquals("new", delta.path("lg").get(0).path("id").asText());
        assertEquals("new", delta.path("lg").get(0).path("t").asText());
    }

    private static Game freshTwoPlayerGame() {
        Room room = new Room();
        room.id = "room-2";
        room.settings = GameCore.normalizeSettings(GameSettings.defaultSettings(), List.of("lib"), Map.of());
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Player p = new Player();
            p.clientId = "p" + i;
            p.username = "Player " + i;
            p.connected = true;
            players.add(p);
        }
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Card c = new Card();
            c.id = "card-" + i;
            c.libraryId = "lib";
            c.pmvId = (long) ((i % 2) + 1);
            c.pmvName = "PMV " + c.pmvId;
            c.shot = "a";
            c.imageUrl = "/cards/lib/cards/" + c.pmvId + "a.png";
            c.backUrl = "/cards/lib/back.png";
            cards.add(c);
        }
        Game game = GameCore.createGame(room, players, cards, 1000L, () -> 0.5);
        GameCore.startPlaying(game, 1000L);
        game.turnAvailableAt = 0;
        game.turnDeadlineAt = 100_000L;
        return game;
    }

    private static Game realisticEightPlayerGame() {
        Room room = new Room();
        room.id = "room-1";
        room.settings = GameCore.normalizeSettings(GameSettings.defaultSettings(), List.of("lib"), Map.of());
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Player p = new Player();
            p.clientId = "p" + i;
            p.username = "Player " + i;
            p.connected = true;
            players.add(p);
        }
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            Card c = new Card();
            c.id = "card-" + i;
            c.libraryId = "lib";
            c.pmvId = (long) ((i % 8) + 1);
            c.pmvName = "PMV " + c.pmvId;
            c.shot = "a";
            c.imageUrl = "/cards/lib/cards/" + c.pmvId + "a.png";
            c.backUrl = "/cards/lib/back.png";
            cards.add(c);
        }
        Game game = GameCore.createGame(room, players, cards, 1000L, () -> 0.5);
        GameCore.startPlaying(game, 1000L);
        for (int round = 0; round < 6; round++) {
            String actor = game.players.get(game.turnIndex).clientId;
            GameCore.performPlayCard(game, actor, 10_000L + round * 1000L, false);
        }
        return game;
    }

    @Test
    void deltaPayloadGzipUnderOneKbForTypicalTick() throws Exception {
        PublicGame previous = samplePublicGame(8, 10);
        PublicGame current = samplePublicGame(8, 11);
        current.turnIndex = 3;
        current.playCount = 11;
        current.players.get(0).drawCount = 3;
        current.logs.add(log("played"));
        JsonNode delta = encoder.encodeDelta(previous, current);
        byte[] gzip = gzipJson(new ObjectMapper().writeValueAsBytes(delta));
        assertTrue(gzip.length <= 1024, "gzip delta bytes=" + gzip.length);
    }

    @Test
    void playerPatchIncludesLoadingProgressFields() {
        PublicGame previous = samplePublicGame(2, 0);
        previous.status = "loading";
        previous.players.get(0).ready = false;
        previous.players.get(0).loadingLoaded = 1;
        previous.players.get(0).loadingTotal = 8;
        previous.players.get(0).loadingProgress = 10;

        PublicGame current = samplePublicGame(2, 0);
        current.status = "loading";
        current.players.get(0).ready = false;
        current.players.get(0).loadingLoaded = 6;
        current.players.get(0).loadingTotal = 10;
        current.players.get(0).loadingProgress = 60;

        JsonNode delta = encoder.encodeDelta(previous, current);
        JsonNode patch = delta.path("pl").get(0);
        assertEquals("p0", patch.path("id").asText());
        assertEquals(6, patch.path("ll").asInt());
        assertEquals(10, patch.path("lt").asInt());
        assertEquals(60, patch.path("lp").asInt());
    }

    @Test
    void nonPlayPilePatchUsesAbsolutePiles() {
        PublicGame previous = samplePublicGame(2, 6);
        previous.turnIndex = 0;
        PublicPlayer player = previous.players.get(0);
        player.drawCount = 3;
        player.displayCount = 0;
        player.drawPile = List.of(backCard("d0"), backCard("d1"), backCard("d2"));
        player.displayPile = List.of();

        PublicGame current = samplePublicGame(2, 6);
        current.turnIndex = 0;
        current.bellCount = previous.bellCount + 1;
        PublicPlayer currentPlayer = current.players.get(0);
        currentPlayer.drawCount = 2;
        currentPlayer.displayCount = 1;
        currentPlayer.drawPile = List.of(backCard("d1"), backCard("d2"));
        PublicCard face = new PublicCard();
        face.id = "d0";
        face.libraryId = "lib";
        face.pmvId = 2L;
        face.imageUrl = "/cards/lib/cards/2a.jpg";
        face.backUrl = "/cards/lib/back.png";
        face.playedSeq = 6;
        currentPlayer.displayPile = List.of(face);

        JsonNode delta = encoder.encodeDelta(previous, current);
        JsonNode patch = delta.path("pl").get(0);

        assertEquals("p0", patch.path("id").asText());
        assertTrue(patch.has("dr"), "non-play pile patches must be absolute for idempotency");
        assertTrue(patch.has("dp"), "non-play pile patches must be absolute for idempotency");
        assertFalse(patch.has("drm"));
        assertFalse(patch.has("dpa"));
    }

    @Test
    void bellPatchReissuesDrawPileWhenDisplayPileClears() {
        PublicGame previous = samplePublicGame(2, 6);
        PublicPlayer player = previous.players.get(0);
        player.drawCount = 2;
        player.drawPile = List.of(backCard("a0"), backCard("a1"));
        player.displayCount = 1;
        player.displayPile = List.of(faceCard("shown", 2L));

        PublicGame current = samplePublicGame(2, 6);
        current.bellCount = previous.bellCount + 1;
        PublicPlayer currentPlayer = current.players.get(0);
        currentPlayer.drawCount = 2;
        currentPlayer.drawPile = List.of(backCard("a0"), backCard("a1"));
        currentPlayer.displayCount = 0;
        currentPlayer.displayPile = List.of();

        JsonNode delta = encoder.encodeDelta(previous, current);
        JsonNode patch = delta.path("pl").get(0);

        assertEquals("p0", patch.path("id").asText());
        assertTrue(patch.has("dp"), "bell patches must clear the display pile explicitly");
        assertEquals(0, patch.path("dp").size());
        assertTrue(patch.has("dr"), "bell patches must reissue the real visible draw pile");
        assertEquals(2, patch.path("dr").size());
        assertEquals("a0", patch.path("dr").get(0).path("i").asText());
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
                face.pmvId = (long) (i % 5);
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

    private static PublicCard faceCard(String id, Long pmvId) {
        PublicCard c = new PublicCard();
        c.id = id;
        c.libraryId = "lib";
        c.pmvId = pmvId;
        c.imageUrl = "/cards/lib/cards/" + pmvId + "a.jpg";
        c.backUrl = "/cards/lib/back.png";
        return c;
    }

    private static GameLog log(String text) {
        GameLog entry = new GameLog();
        entry.text = text;
        entry.at = System.currentTimeMillis();
        return entry;
    }

    private static GameLog log(String id, String text, long at) {
        GameLog entry = new GameLog();
        entry.id = id;
        entry.text = text;
        entry.at = at;
        return entry;
    }
}
