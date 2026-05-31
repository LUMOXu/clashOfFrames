import { describe, expect, it } from "vitest";
import { buildGameSocketUrl, parseSyncMessage, parseWsMessage } from "./gameSocket";

describe("gameSocket", () => {
  it("builds websocket url with token", () => {
    const url = buildGameSocketUrl("tok123", "http://localhost:9001");
    expect(url).toBe("ws://localhost:9001/ws/v1/game?token=tok123");
  });

  it("parses generic ws message", () => {
    const msg = parseWsMessage(JSON.stringify({ t: "PING" }));
    expect(msg?.t).toBe("PING");
  });

  it("returns null for invalid json", () => {
    expect(parseWsMessage("not-json")).toBeNull();
  });

  it("parses SYNC message with delta payload", () => {
    const raw = JSON.stringify({
      t: "SYNC",
      g: "game-42",
      ti: 3,
      pc: "12",
      sync: { players: [{ id: "p1" }], piles: [] },
    });
    const parsed = parseSyncMessage(raw);
    expect(parsed).toEqual({
      type: "SYNC",
      gameId: "game-42",
      turnIndex: 3,
      playCount: 12,
      syncPayload: { players: [{ id: "p1" }], piles: [] },
    });
  });

  it("returns null when SYNC lacks game id", () => {
    expect(parseSyncMessage(JSON.stringify({ t: "SYNC", ti: 1 }))).toBeNull();
  });

  it("returns null for non-SYNC types", () => {
    expect(parseSyncMessage(JSON.stringify({ t: "AUDIO", au: "ring-bell" }))).toBeNull();
  });
});
