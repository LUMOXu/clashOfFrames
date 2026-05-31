import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { handleAudioEvent } from "./useGameAudio";

describe("useGameAudio", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "Audio",
      vi.fn(() => ({
        preload: "",
        muted: false,
        currentTime: 0,
        play: vi.fn().mockResolvedValue(undefined),
        pause: vi.fn(),
      })),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("plays new-game and end-game clips", () => {
    handleAudioEvent("new-game");
    handleAudioEvent("end-game");
    expect(Audio).toHaveBeenCalledWith("/audio/newgame.wav");
    expect(Audio).toHaveBeenCalledWith("/audio/endgame.wav");
  });
});
