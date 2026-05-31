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
});
