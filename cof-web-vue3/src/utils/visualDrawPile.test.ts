import { describe, expect, it } from "vitest";
import { visualDrawPile } from "./visualDrawPile";

describe("visualDrawPile", () => {
  it("does not invent placeholder backs when visible drawPile is empty", () => {
    const cards = visualDrawPile(
      {
        clientId: "a",
        username: "A",
        drawCount: 4,
        drawPile: [],
      },
      null,
      1_000,
    );

    expect(cards).toEqual([]);
  });
});
