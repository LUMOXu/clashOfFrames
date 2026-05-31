import { describe, expect, it } from "vitest";
import { playerLayouts } from "./playerLayouts";

describe("playerLayouts", () => {
  it("places self at bottom of table", () => {
    const layouts = playerLayouts(
      [
        { clientId: "a", username: "A" },
        { clientId: "b", username: "B" },
        { clientId: "c", username: "C" },
      ],
      "b",
    );
    expect(layouts[0].player.clientId).toBe("b");
    expect(layouts[0].y).toBeGreaterThan(40);
  });
});
