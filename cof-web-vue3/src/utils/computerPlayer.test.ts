import { describe, expect, it } from "vitest";
import { normalizeComputerPlayer } from "./computerPlayer";

describe("normalizeComputerPlayer", () => {
  it("reads camelCase stats", () => {
    const p = normalizeComputerPlayer({
      id: "computer_normal",
      name: "分心玩家",
      playDelayMeanSeconds: 2,
      playDelayStdSeconds: 1,
      reactionMeanSeconds: 3.5,
      reactionStdSeconds: 1,
      matchDetectionProbability: 0.99,
      falseRingProbability: 0.01,
    });
    expect(p.playDelayMeanSeconds).toBe(2);
    expect(p.matchDetectionProbability).toBe(0.99);
  });

  it("reads snake_case stats", () => {
    const p = normalizeComputerPlayer({
      computer_id: "computer_god",
      name: "GOD",
      play_delay_mean_seconds: 1.2,
      play_delay_std_seconds: 0,
      reaction_mean_seconds: 0.5,
      reaction_std_seconds: 0.3,
      match_detection_probability: 0.995,
      false_ring_probability: 0.002,
    });
    expect(p.id).toBe("computer_god");
    expect(p.playDelayMeanSeconds).toBe(1.2);
    expect(p.falseRingProbability).toBe(0.002);
  });
});
