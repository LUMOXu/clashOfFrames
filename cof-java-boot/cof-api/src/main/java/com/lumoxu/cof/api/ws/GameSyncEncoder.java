package com.lumoxu.cof.api.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumoxu.cof.engine.GameLog;
import com.lumoxu.cof.engine.PlayerStats;
import com.lumoxu.cof.engine.PublicCard;
import com.lumoxu.cof.engine.PublicGame;
import com.lumoxu.cof.engine.PublicPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class GameSyncEncoder {

    private final ObjectMapper objectMapper;

    public GameSyncEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode encodeDelta(PublicGame previous, PublicGame current) {
        ObjectNode delta = objectMapper.createObjectNode();
        if (current == null) {
            return delta;
        }
        if (previous == null) {
            delta.set("full", objectMapper.valueToTree(current));
            return delta;
        }

        Optional<PlayCardEvent> playEvent = detectPlayCardEvent(previous, current);
        if (playEvent.isPresent()) {
            return encodePlayCardEvent(playEvent.get(), previous, current);
        }

        if (!safeEquals(previous.status, current.status)) {
            delta.put("st", current.status);
        }
        if (previous.turnIndex != current.turnIndex) {
            delta.put("ti", current.turnIndex);
        }
        if (previous.turnDeadlineAt != current.turnDeadlineAt) {
            delta.put("td", current.turnDeadlineAt);
        }
        if (previous.turnAvailableAt != current.turnAvailableAt) {
            delta.put("ta", current.turnAvailableAt);
        }
        if (previous.lockedUntil != current.lockedUntil) {
            delta.put("lu", current.lockedUntil);
        }
        if (!safeEquals(previous.lockMessage, current.lockMessage)) {
            delta.put("lm", current.lockMessage == null ? "" : current.lockMessage);
        }
        if (previous.playCount != current.playCount) {
            delta.put("pc", current.playCount);
        }
        if (previous.bellCount != current.bellCount) {
            delta.put("bc", current.bellCount);
        }
        if (previous.successBellCount != current.successBellCount) {
            delta.put("sbc", current.successBellCount);
        }
        if (previous.failBellCount != current.failBellCount) {
            delta.put("fbc", current.failBellCount);
        }
        if (previous.discardedCards != current.discardedCards) {
            delta.put("dc", current.discardedCards);
        }
        if (!safeEquals(previous.winnerId, current.winnerId)) {
            if (current.winnerId != null) {
                delta.put("w", current.winnerId);
            }
        }

        ArrayNode playerPatches = encodePlayerPatches(previous.players, current.players);
        if (!playerPatches.isEmpty()) {
            delta.set("pl", playerPatches);
        }

        ArrayNode newLogs = encodeNewLogs(previous.logs, current.logs);
        if (!newLogs.isEmpty()) {
            delta.set("lg", newLogs);
        }

        if (!jsonEquals(previous.lastAnimation, current.lastAnimation)) {
            delta.set("la", objectMapper.valueToTree(current.lastAnimation));
        }
        if (!jsonEquals(previous.lastMatch, current.lastMatch)) {
            delta.set("lmch", objectMapper.valueToTree(current.lastMatch));
        }
        ArrayNode topPatches = encodeTopCardPatches(previous.preLastTopCards, current.preLastTopCards);
        if (!topPatches.isEmpty()) {
            delta.set("pt", topPatches);
        }
        if (!jsonEquals(previous.eliminatedOrder, current.eliminatedOrder)) {
            delta.set("eo", objectMapper.valueToTree(current.eliminatedOrder));
        }
        if (!jsonEquals(previous.continueVotes, current.continueVotes)) {
            delta.set("cv", objectMapper.valueToTree(current.continueVotes));
        }
        if (!jsonEquals(previous.resultInfo, current.resultInfo)) {
            delta.set("ri", objectMapper.valueToTree(current.resultInfo));
        }
        if (previous.continueCountdownStartedAt == null
                ? current.continueCountdownStartedAt != null
                : !previous.continueCountdownStartedAt.equals(current.continueCountdownStartedAt)) {
            if (current.continueCountdownStartedAt != null) {
                delta.put("ccs", current.continueCountdownStartedAt);
            }
        }
        if (previous.continueReturnAt == null
                ? current.continueReturnAt != null
                : !previous.continueReturnAt.equals(current.continueReturnAt)) {
            if (current.continueReturnAt != null) {
                delta.put("cra", current.continueReturnAt);
            }
        }

        if (delta.isEmpty()) {
            delta.put("hb", true);
        }
        return delta;
    }

    private static final class PlayCardEvent {
        String actorId;
        PublicCard card;
        boolean actorEliminated;
        Integer actorRank;
    }

    private Optional<PlayCardEvent> detectPlayCardEvent(PublicGame previous, PublicGame current) {
        if (current.playCount != previous.playCount + 1) {
            return Optional.empty();
        }
        if (!jsonEquals(previous.lastAnimation, current.lastAnimation)
                && current.lastAnimation != null) {
            return Optional.empty();
        }
        if (!jsonEquals(previous.lastMatch, current.lastMatch) && current.lastMatch != null) {
            return Optional.empty();
        }
        Map<String, PublicPlayer> previousById = indexPlayers(previous.players);
        PlayCardEvent event = null;
        int pileChanges = 0;
        if (current.players == null) {
            return Optional.empty();
        }
        for (PublicPlayer currentPlayer : current.players) {
            if (currentPlayer == null || currentPlayer.clientId == null) {
                continue;
            }
            PublicPlayer previousPlayer = previousById.get(currentPlayer.clientId);
            if (previousPlayer == null) {
                return Optional.empty();
            }
            boolean drawRemoved = currentPlayer.drawCount == previousPlayer.drawCount - 1
                    && drawPileHeadRemoved(previousPlayer, currentPlayer);
            PublicCard appended = displayPileAppendedCard(previousPlayer.displayPile, currentPlayer.displayPile);
            boolean displayAdded = currentPlayer.displayCount == previousPlayer.displayCount + 1 && appended != null;
            if (!drawRemoved && !displayAdded) {
                if (previousPlayer.eliminated != currentPlayer.eliminated
                        || previousPlayer.drawCount != currentPlayer.drawCount
                        || previousPlayer.displayCount != currentPlayer.displayCount
                        || !pileIdsEqual(previousPlayer.drawPile, currentPlayer.drawPile)
                        || !pileIdsEqual(previousPlayer.displayPile, currentPlayer.displayPile)) {
                    return Optional.empty();
                }
                continue;
            }
            if (!drawRemoved || !displayAdded) {
                return Optional.empty();
            }
            pileChanges += 1;
            if (event != null) {
                return Optional.empty();
            }
            event = new PlayCardEvent();
            event.actorId = currentPlayer.clientId;
            event.card = appended;
            event.actorEliminated = currentPlayer.eliminated && !previousPlayer.eliminated;
            event.actorRank = currentPlayer.rank;
        }
        if (event == null || pileChanges != 1) {
            return Optional.empty();
        }
        if (event.card.playedSeq > 0 && event.card.playedSeq != current.playCount) {
            return Optional.empty();
        }
        return Optional.of(event);
    }

    private ObjectNode encodePlayCardEvent(PlayCardEvent change, PublicGame previous, PublicGame current) {
        ObjectNode delta = objectMapper.createObjectNode();
        delta.put("ev", "play");
        delta.put("by", change.actorId);
        delta.set("c", encodeCompactCard(change.card));
        putTurnScalars(delta, previous, current);
        if (change.actorEliminated) {
            delta.put("el", 1);
        }
        if (change.actorRank != null) {
            delta.put("rk", change.actorRank);
        }
        if (!safeEquals(previous.status, current.status)) {
            delta.put("st", current.status);
        }
        if (!safeEquals(previous.winnerId, current.winnerId) && current.winnerId != null) {
            delta.put("w", current.winnerId);
        }
        if (!jsonEquals(previous.eliminatedOrder, current.eliminatedOrder)) {
            delta.set("eo", objectMapper.valueToTree(current.eliminatedOrder));
        }
        if (!jsonEquals(previous.resultInfo, current.resultInfo)) {
            delta.set("ri", objectMapper.valueToTree(current.resultInfo));
        }
        return delta;
    }

    private void putTurnScalars(ObjectNode delta, PublicGame previous, PublicGame current) {
        if (previous.turnIndex != current.turnIndex) {
            delta.put("ti", current.turnIndex);
        }
        if (previous.turnDeadlineAt != current.turnDeadlineAt) {
            delta.put("td", current.turnDeadlineAt);
        }
        if (previous.turnAvailableAt != current.turnAvailableAt) {
            delta.put("ta", current.turnAvailableAt);
        }
        if (previous.lockedUntil != current.lockedUntil) {
            delta.put("lu", current.lockedUntil);
        }
        if (!safeEquals(previous.lockMessage, current.lockMessage)) {
            delta.put("lm", current.lockMessage == null ? "" : current.lockMessage);
        }
        if (previous.playCount != current.playCount) {
            delta.put("pc", current.playCount);
        }
    }

    private ArrayNode encodePlayerPatches(List<PublicPlayer> previousPlayers, List<PublicPlayer> currentPlayers) {
        Map<String, PublicPlayer> previousById = indexPlayers(previousPlayers);
        ArrayNode patches = objectMapper.createArrayNode();
        if (currentPlayers == null) {
            return patches;
        }
        for (PublicPlayer current : currentPlayers) {
            if (current == null || current.clientId == null) {
                continue;
            }
            PublicPlayer previous = previousById.get(current.clientId);
            if (previous == null) {
                patches.add(encodeFullPlayerPatch(current));
                continue;
            }
            ObjectNode patch = encodePlayerPatch(previous, current);
            if (patch.size() > 1) {
                patches.add(patch);
            }
        }
        return patches;
    }

    private ObjectNode encodeFullPlayerPatch(PublicPlayer player) {
        ObjectNode patch = objectMapper.createObjectNode();
        patch.put("id", player.clientId);
        patch.put("dc", player.drawCount);
        patch.put("xc", player.displayCount);
        patch.put("el", player.eliminated);
        patch.put("ex", player.exited);
        patch.put("co", player.connected);
        patch.put("rd", player.ready);
        patch.put("ll", player.loadingLoaded);
        patch.put("lt", player.loadingTotal);
        patch.put("lp", player.loadingProgress);
        patch.put("lc", player.loadingCached);
        if (player.loadingStartedAt != null) {
            patch.put("ls", player.loadingStartedAt);
        }
        if (player.loadingFinishedAt != null) {
            patch.put("lf", player.loadingFinishedAt);
        }
        if (player.drawPile != null && !player.drawPile.isEmpty()) {
            patch.set("dr", encodeCompactCards(player.drawPile));
        }
        if (player.displayPile != null && !player.displayPile.isEmpty()) {
            patch.set("dp", encodeCompactCards(player.displayPile));
        }
        if (player.stats != null) {
            patch.set("ps", encodeStats(player.stats));
        }
        return patch;
    }

    private ObjectNode encodePlayerPatch(PublicPlayer previous, PublicPlayer current) {
        ObjectNode patch = objectMapper.createObjectNode();
        patch.put("id", current.clientId);
        if (previous.drawCount != current.drawCount) {
            patch.put("dc", current.drawCount);
        }
        if (previous.displayCount != current.displayCount) {
            patch.put("xc", current.displayCount);
        }
        if (previous.eliminated != current.eliminated) {
            patch.put("el", current.eliminated);
        }
        if (previous.exited != current.exited) {
            patch.put("ex", current.exited);
        }
        if (previous.connected != current.connected) {
            patch.put("co", current.connected);
        }
        if (previous.ready != current.ready) {
            patch.put("rd", current.ready);
        }
        if (previous.loadingLoaded != current.loadingLoaded) {
            patch.put("ll", current.loadingLoaded);
        }
        if (previous.loadingTotal != current.loadingTotal) {
            patch.put("lt", current.loadingTotal);
        }
        if (previous.loadingProgress != current.loadingProgress) {
            patch.put("lp", current.loadingProgress);
        }
        if (previous.loadingCached != current.loadingCached) {
            patch.put("lc", current.loadingCached);
        }
        if (!Objects.equals(previous.loadingStartedAt, current.loadingStartedAt)) {
            if (current.loadingStartedAt != null) {
                patch.put("ls", current.loadingStartedAt);
            }
        }
        if (!Objects.equals(previous.loadingFinishedAt, current.loadingFinishedAt)) {
            if (current.loadingFinishedAt != null) {
                patch.put("lf", current.loadingFinishedAt);
            }
        }
        if (!Objects.equals(previous.rank, current.rank)) {
            if (current.rank != null) {
                patch.put("rk", current.rank);
            }
        }
        if (!Objects.equals(previous.eliminatedAt, current.eliminatedAt)) {
            if (current.eliminatedAt != null) {
                patch.put("ea", current.eliminatedAt);
            }
        }
        if (!pileIdsEqual(previous.drawPile, current.drawPile)) {
            if (isDrawPileHeadRemoved(previous.drawPile, current.drawPile)) {
                patch.put("drm", 1);
            } else {
                patch.set("dr", encodeCompactCards(current.drawPile));
            }
        }
        if (!pileIdsEqual(previous.displayPile, current.displayPile)) {
            PublicCard appended = displayPileAppendedCard(previous.displayPile, current.displayPile);
            if (appended != null) {
                patch.set("dpa", encodeCompactCard(appended));
            } else {
                patch.set("dp", encodeCompactCards(current.displayPile));
            }
        }
        if (statsChanged(previous.stats, current.stats) && current.stats != null) {
            patch.set("ps", encodeStats(current.stats));
        }
        return patch;
    }

    private static boolean statsChanged(PlayerStats previous, PlayerStats current) {
        if (previous == current) {
            return false;
        }
        if (previous == null || current == null) {
            return true;
        }
        return previous.plays != current.plays
                || previous.rings != current.rings
                || previous.correctRings != current.correctRings
                || previous.wrongRings != current.wrongRings
                || previous.wonCards != current.wonCards;
    }

    private static boolean pileIdsEqual(List<PublicCard> left, List<PublicCard> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index += 1) {
            PublicCard a = left.get(index);
            PublicCard b = right.get(index);
            if (a == null || b == null || !Objects.equals(a.id, b.id)) {
                return false;
            }
        }
        return true;
    }

    private ObjectNode encodeStats(PlayerStats stats) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("pl", stats.plays);
        node.put("rg", stats.rings);
        node.put("cr", stats.correctRings);
        node.put("wr", stats.wrongRings);
        node.put("wc", stats.wonCards);
        return node;
    }

    private ArrayNode encodeCompactCards(List<PublicCard> cards) {
        ArrayNode array = objectMapper.createArrayNode();
        if (cards == null) {
            return array;
        }
        for (PublicCard card : cards) {
            if (card != null) {
                array.add(encodeCompactCard(card));
            }
        }
        return array;
    }

    private ObjectNode encodeCompactCard(PublicCard card) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("i", card.id);
        if (card.libraryId != null && !card.libraryId.isBlank()) {
            node.put("l", card.libraryId);
        }
        if (card.pmvId != null) {
            node.put("p", card.pmvId);
        }
        if (card.shot != null && !card.shot.isBlank() && !"a".equals(card.shot)) {
            node.put("h", card.shot);
        }
        if (card.playedSeq > 0) {
            node.put("s", card.playedSeq);
        }
        boolean faceUp = card.imageUrl != null && !card.imageUrl.isBlank();
        if (!faceUp) {
            node.put("b", 1);
            if (card.backUrl != null && !card.backUrl.isBlank()) {
                node.put("bk", card.backUrl);
            }
        } else {
            node.put("u", card.imageUrl);
            if (card.backUrl != null && !card.backUrl.isBlank()) {
                node.put("bk", card.backUrl);
            }
        }
        return node;
    }

    private ArrayNode encodeTopCardPatches(
            List<PublicGame.PublicTopEntry> previousEntries,
            List<PublicGame.PublicTopEntry> currentEntries) {
        Map<String, PublicGame.PublicTopEntry> previousByPlayer = indexTopCards(previousEntries);
        ArrayNode patches = objectMapper.createArrayNode();
        if (currentEntries == null) {
            return patches;
        }
        for (PublicGame.PublicTopEntry current : currentEntries) {
            if (current == null || current.playerId == null) {
                continue;
            }
            PublicGame.PublicTopEntry previous = previousByPlayer.get(current.playerId);
            if (previous == null || topEntryChanged(previous, current)) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("pid", current.playerId);
                if (current.playedSeq > 0) {
                    node.put("s", current.playedSeq);
                }
                if (current.card != null) {
                    node.set("c", encodeCompactCard(current.card));
                }
                patches.add(node);
            }
        }
        return patches;
    }

    private static Map<String, PublicGame.PublicTopEntry> indexTopCards(List<PublicGame.PublicTopEntry> entries) {
        Map<String, PublicGame.PublicTopEntry> map = new HashMap<>();
        if (entries == null) {
            return map;
        }
        for (PublicGame.PublicTopEntry entry : entries) {
            if (entry != null && entry.playerId != null) {
                map.put(entry.playerId, entry);
            }
        }
        return map;
    }

    private static boolean topEntryChanged(PublicGame.PublicTopEntry previous, PublicGame.PublicTopEntry current) {
        if (previous.playedSeq != current.playedSeq) {
            return true;
        }
        if (previous.card == null || current.card == null) {
            return previous.card != current.card;
        }
        return !Objects.equals(previous.card.id, current.card.id)
                || !Objects.equals(previous.card.pmvId, current.card.pmvId);
    }

    private static boolean drawPileHeadRemoved(PublicPlayer previous, PublicPlayer current) {
        if (previous.drawPile == null || previous.drawPile.isEmpty()) {
            return true;
        }
        if (current.drawPile == null || current.drawPile.isEmpty()) {
            return previous.drawCount > 0;
        }
        return isDrawPileHeadRemoved(previous.drawPile, current.drawPile);
    }

    private static boolean isDrawPileHeadRemoved(List<PublicCard> previous, List<PublicCard> current) {
        if (previous == null || current == null || current.size() != previous.size() - 1) {
            return false;
        }
        for (int index = 0; index < current.size(); index += 1) {
            PublicCard left = previous.get(index + 1);
            PublicCard right = current.get(index);
            if (left == null || right == null || !Objects.equals(left.id, right.id)) {
                return false;
            }
        }
        return true;
    }

    private static PublicCard displayPileAppendedCard(List<PublicCard> previous, List<PublicCard> current) {
        if (previous == null || current == null || current.size() != previous.size() + 1) {
            return null;
        }
        for (int index = 0; index < previous.size(); index += 1) {
            PublicCard left = previous.get(index);
            PublicCard right = current.get(index);
            if (left == null || right == null || !Objects.equals(left.id, right.id)) {
                return null;
            }
        }
        return current.get(current.size() - 1);
    }

    private static Map<String, PublicPlayer> indexPlayers(List<PublicPlayer> players) {
        Map<String, PublicPlayer> map = new HashMap<>();
        if (players == null) {
            return map;
        }
        for (PublicPlayer player : players) {
            if (player != null && player.clientId != null) {
                map.put(player.clientId, player);
            }
        }
        return map;
    }

    private ArrayNode encodeNewLogs(List<GameLog> previousLogs, List<GameLog> currentLogs) {
        ArrayNode logs = objectMapper.createArrayNode();
        if (currentLogs == null || currentLogs.isEmpty()) {
            return logs;
        }
        int previousSize = previousLogs == null ? 0 : previousLogs.size();
        if (currentLogs.size() <= previousSize) {
            return logs;
        }
        for (int index = previousSize; index < currentLogs.size(); index += 1) {
            GameLog entry = currentLogs.get(index);
            ObjectNode compact = objectMapper.createObjectNode();
            if (entry.text != null) {
                compact.put("t", entry.text);
            }
            compact.put("at", entry.at);
            if (entry.playCount > 0) {
                compact.put("pc", entry.playCount);
            }
            logs.add(compact);
        }
        return logs;
    }

    private boolean jsonEquals(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return objectMapper.valueToTree(left).equals(objectMapper.valueToTree(right));
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }
}
