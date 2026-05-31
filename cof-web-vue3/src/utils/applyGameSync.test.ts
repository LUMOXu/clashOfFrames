import { describe, expect, it } from "vitest";
import { applyGameSync } from "./applyGameSync";
import type { PublicGame } from "@/types/api";

describe("applyGameSync", () => {
  it("applies full snapshot", () => {
    const full: PublicGame = { id: "g1", status: "playing", playCount: 2 };
    const result = applyGameSync(null, { full });
    expect(result?.id).toBe("g1");
    expect(result?.playCount).toBe(2);
  });

  it("merges partial delta", () => {
    const prev: PublicGame = { id: "g1", status: "playing", playCount: 1, turnIndex: 0 };
    const result = applyGameSync(prev, { ti: 1, pc: 2 });
    expect(result?.turnIndex).toBe(1);
    expect(result?.playCount).toBe(2);
  });

  it("merges compact player patches and logs", () => {
    const prev: PublicGame = {
      id: "g1",
      status: "playing",
      settings: { libraryIds: ["deck-a"] },
      players: [
        {
          clientId: "a",
          username: "A",
          drawCount: 5,
          displayCount: 1,
          drawPile: [{ id: "c0", backUrl: "/cards/deck-a/back.png" }],
          displayPile: [{ id: "c9", pmvId: 2, imageUrl: "/cards/deck-a/2/a.jpg" }],
        },
      ],
      logs: [{ at: 1, text: "old" }],
    };
    const result = applyGameSync(prev, {
      pl: [
        {
          id: "a",
          dc: 4,
          xc: 2,
          dr: [{ i: "c1", l: "deck-a", b: 1 }],
          dp: [
            { i: "c9", l: "deck-a", p: 2, s: 1 },
            { i: "c10", l: "deck-a", p: 3, s: 2 },
          ],
        },
      ],
      lg: [{ t: "new", at: 2 }],
    });
    expect(result?.players?.[0]?.drawCount).toBe(4);
    expect(result?.players?.[0]?.drawPile?.[0]?.backUrl).toContain("/cards/deck-a/back.png");
    expect(result?.players?.[0]?.displayPile?.[1]?.imageUrl).toContain("/cards/deck-a/3/a.jpg");
    expect(result?.logs).toHaveLength(2);
    expect(result?.logs?.[1]?.text).toBe("new");
  });

  it("merges compact preLastTopCards patches", () => {
    const prev: PublicGame = {
      id: "g1",
      settings: { libraryIds: ["deck-a"] },
      preLastTopCards: [{ playerId: "b", playedSeq: 1, card: { id: "old" } }],
    };
    const result = applyGameSync(prev, {
      pt: [{ pid: "a", s: 3, c: { i: "c1", l: "deck-a", p: 5, s: 3 } }],
    });
    expect(result?.preLastTopCards).toHaveLength(2);
    expect(result?.preLastTopCards?.find((e) => e.playerId === "a")?.card?.id).toBe("c1");
  });

  it("applies play event without full pile sync", () => {
    const prev: PublicGame = {
      id: "g1",
      status: "playing",
      playCount: 5,
      turnIndex: 0,
      settings: { libraryIds: ["deck-a"] },
      players: [
        {
          clientId: "a",
          username: "A",
          drawCount: 2,
          displayCount: 1,
          drawPile: [{ id: "c0" }, { id: "c1" }],
          displayPile: [{ id: "d0", pmvId: 1, imageUrl: "/x", playedSeq: 3 }],
        },
        {
          clientId: "b",
          username: "B",
          drawCount: 3,
          displayPile: [],
        },
      ],
      preLastTopCards: [],
    };
    const result = applyGameSync(prev, {
      ev: "play",
      by: "a",
      c: { i: "c0", l: "deck-a", p: 4, s: 6 },
      pc: 6,
      ti: 1,
    });
    expect(result?.playCount).toBe(6);
    expect(result?.turnIndex).toBe(1);
    expect(result?.players?.[0]?.drawPile?.map((c) => c.id)).toEqual(["c1"]);
    expect(result?.players?.[0]?.displayPile).toHaveLength(2);
    expect(result?.players?.[0]?.displayPile?.[1]?.pmvId).toBe(4);
    expect(result?.preLastTopCards?.find((e) => e.playerId === "a")?.card?.id).toBe("d0");
  });

  it("preserves player order when merging patches", () => {
    const prev: PublicGame = {
      id: "g1",
      status: "playing",
      turnIndex: 1,
      players: [
        { clientId: "a", username: "A", drawCount: 3 },
        { clientId: "b", username: "B", drawCount: 2 },
        { clientId: "c", username: "C", drawCount: 1 },
      ],
    };
    const result = applyGameSync(prev, {
      ti: 2,
      pl: [{ id: "b", dc: 1 }],
    });
    expect(result?.players?.map((p) => p.clientId)).toEqual(["a", "b", "c"]);
    expect(result?.turnIndex).toBe(2);
    expect(result?.players?.[1]?.drawCount).toBe(1);
  });

  it("applies draw and display pile append patches", () => {
    const prev: PublicGame = {
      id: "g1",
      settings: { libraryIds: ["deck-a"] },
      players: [
        {
          clientId: "a",
          username: "A",
          drawPile: [{ id: "c0" }, { id: "c1" }],
          displayPile: [{ id: "d0", pmvId: 1, imageUrl: "/x" }],
        },
      ],
    };
    const result = applyGameSync(prev, {
      pl: [{ id: "a", drm: 1, dpa: { i: "d1", l: "deck-a", p: 2, s: 2 } }],
    });
    expect(result?.players?.[0]?.drawPile?.map((c) => c.id)).toEqual(["c1"]);
    expect(result?.players?.[0]?.displayPile).toHaveLength(2);
    expect(result?.players?.[0]?.displayPile?.[1]?.imageUrl).toContain("/cards/deck-a/2/a.jpg");
  });
});
