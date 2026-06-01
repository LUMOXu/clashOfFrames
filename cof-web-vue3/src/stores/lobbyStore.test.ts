import { describe, expect, it, vi, beforeEach } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useLobbyStore } from "./lobbyStore";
import * as metaApi from "@/api/meta";
import { fetchLeaderboard } from "@/api/leaderboard";
import { fetchProfile } from "@/api/profile";

vi.mock("@/api/meta");
vi.mock("@/api/leaderboard");
vi.mock("@/api/profile");

describe("lobbyStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("loads meta libraries and computers", async () => {
    vi.mocked(metaApi.fetchCardLibraries).mockResolvedValue({ libraries: [{ id: "lib1" }] });
    vi.mocked(metaApi.fetchComputerPlayers).mockResolvedValue({
      players: [{ id: "cpu1", name: "Test", playDelayMeanSeconds: 2, matchDetectionProbability: 0.5 }],
    });

    const store = useLobbyStore();
    await store.loadMeta();

    expect(store.cardLibraries).toHaveLength(1);
    expect(store.computerPlayers).toHaveLength(1);
    expect(store.computerPlayers[0]?.playDelayMeanSeconds).toBe(2);
  });

  it("loads profile and leaderboard", async () => {
    vi.mocked(fetchProfile).mockResolvedValue({ profile: { wins: 3 } });
    vi.mocked(fetchLeaderboard).mockResolvedValue({
      players: [{ username: "a", wins: 1 }],
      matches: [],
    });

    const store = useLobbyStore();
    await store.loadProfile("c1");
    await store.loadLeaderboard();

    expect(store.profile).toEqual({ wins: 3 });
    expect(store.leaderboard).toHaveLength(1);
  });
});
