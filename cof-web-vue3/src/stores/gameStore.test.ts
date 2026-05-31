import { describe, expect, it, vi, beforeEach } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useGameStore } from "./gameStore";
import * as gamesApi from "@/api/games";

vi.mock("@/api/games");

describe("gameStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("loads game state", async () => {
    vi.mocked(gamesApi.getGame).mockResolvedValue({
      game: { id: "g1", status: "playing", playCount: 1 },
      sync: { full: { id: "g1", status: "playing", playCount: 1 } },
    });
    const store = useGameStore();
    const game = await store.loadGame("g1");
    expect(game.id).toBe("g1");
    expect(store.currentGame?.playCount).toBe(1);
  });

  it("applies incremental sync on playCard", async () => {
    vi.mocked(gamesApi.getGame).mockResolvedValue({
      game: { id: "g1", status: "playing", playCount: 1, players: [] },
    });
    vi.mocked(gamesApi.playCard).mockResolvedValue({ pc: 2, ti: 1 });
    const store = useGameStore();
    await store.loadGame("g1");
    await store.playCard("g1");
    expect(store.currentGame?.playCount).toBe(2);
    expect(store.currentGame?.turnIndex).toBe(1);
  });

  it("tracks loading progress", () => {
    const store = useGameStore();
    store.setLoadingProgress(3, 10);
    expect(store.loadingProgress).toEqual({ loaded: 3, total: 10, done: false });
    store.setLoadingProgress(10, 10, true);
    expect(store.loadingProgress.done).toBe(true);
  });

  it("clears game on clearGame", async () => {
    vi.mocked(gamesApi.getGame).mockResolvedValue({ game: { id: "g1" } });
    const store = useGameStore();
    await store.loadGame("g1");
    store.clearGame();
    expect(store.currentGame).toBeNull();
  });
});
