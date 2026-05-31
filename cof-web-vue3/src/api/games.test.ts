import { describe, expect, it, vi } from "vitest";
import { getGame, playCard, ringBell } from "./games";
import { unwrap } from "./client";

vi.mock("./client", () => ({
  unwrap: vi.fn(),
}));

describe("games api", () => {
  it("gets game by id", async () => {
    vi.mocked(unwrap).mockResolvedValue({ id: "g1", status: "playing" });
    const game = await getGame("g1");
    expect(game.id).toBe("g1");
  });

  it("plays card", async () => {
    vi.mocked(unwrap).mockResolvedValue({ id: "g1", playCount: 2 });
    await playCard("g1");
    expect(unwrap).toHaveBeenCalledWith(
      expect.objectContaining({ method: "POST", url: "/games/g1/play-card" }),
    );
  });

  it("rings bell", async () => {
    vi.mocked(unwrap).mockResolvedValue({ id: "g1" });
    await ringBell("g1");
    expect(unwrap).toHaveBeenCalledWith(
      expect.objectContaining({ url: "/games/g1/ring-bell" }),
    );
  });
});
