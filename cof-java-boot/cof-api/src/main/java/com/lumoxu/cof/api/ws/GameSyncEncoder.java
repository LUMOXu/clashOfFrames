package com.lumoxu.cof.api.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumoxu.cof.engine.PublicGame;

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
        if (previous.playCount != current.playCount) {
            delta.put("pc", current.playCount);
        }
        if (previous.bellCount != current.bellCount) {
            delta.put("bc", current.bellCount);
        }
        if (!safeEquals(previous.winnerId, current.winnerId)) {
            delta.put("w", current.winnerId);
        }
        if (delta.isEmpty()) {
            delta.put("hb", true);
        }
        return delta;
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }
}
