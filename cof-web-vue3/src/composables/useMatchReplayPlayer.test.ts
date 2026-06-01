import { describe, expect, it } from "vitest";
import { ref } from "vue";
import { useMatchReplayPlayer } from "./useMatchReplayPlayer";
import type { GameReplayTimeline } from "@/types/replay";

describe("useMatchReplayPlayer", () => {
  it("seeks and exposes display state", () => {
    const timeline = ref<GameReplayTimeline>({
      startedAt: 1000,
      defaultViewerId: "p1",
      frames: [
        {
          t: 0,
          state: {
            id: "g1",
            status: "playing",
            playCount: 0,
            players: [{ clientId: "p1", username: "A", drawCount: 2, displayCount: 0 }],
          },
        },
        {
          t: 500,
          state: {
            id: "g1",
            status: "playing",
            playCount: 1,
            players: [{ clientId: "p1", username: "A", drawCount: 1, displayCount: 1 }],
          },
        },
      ],
    });
    const { displayGame, seek, replayMs } = useMatchReplayPlayer(timeline);
    expect(displayGame.value?.playCount).toBe(0);
    seek(500);
    expect(replayMs.value).toBe(500);
    expect(displayGame.value?.playCount).toBe(1);
  });
});
