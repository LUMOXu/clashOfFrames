import { describe, expect, it } from "vitest";
import type { PublicGame } from "@/types/api";
import {
  buildResultChartModel,
  canContinueAfterResultReplay,
  resultReplayProgress,
} from "./resultChart";

function sampleFinishedGame(): PublicGame {
  return {
    id: "g1",
    status: "finished",
    playCount: 4,
    winnerId: "b",
    resultInfo: {
      counts: [
        [3, 3],
        [2, 3],
        [2, 4],
        [1, 4],
        [1, 5],
      ],
      players: [
        { clientId: "a", username: "A" },
        { clientId: "b", username: "B" },
      ],
    },
  };
}

describe("resultChart", () => {
  it("builds model with clipped lines", () => {
    const model = buildResultChartModel(sampleFinishedGame(), 2.5);
    expect(model).not.toBeNull();
    expect(model!.xMax).toBe(4);
    expect(model!.yMax).toBe(5);
    expect(model!.series).toHaveLength(2);
    expect(model!.series[1].winner).toBe(true);
    expect(model!.series[0].points.map((p) => p.x)).toEqual([0, 1, 2, 2.5]);
    expect(model!.series[0].points[3].y).toBe(1.5);
  });

  it("replay progresses at twenty cards per second", () => {
    const game = { ...sampleFinishedGame(), playCount: 10 };
    expect(resultReplayProgress(game, 1000, 1250)).toBe(5);
    expect(canContinueAfterResultReplay(sampleFinishedGame(), 1000, 1199)).toBe(false);
    expect(canContinueAfterResultReplay(sampleFinishedGame(), 1000, 1200)).toBe(true);
  });
});
