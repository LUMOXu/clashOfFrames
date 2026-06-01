import type { PublicGame } from "@/types/api";

export interface GameReplayFrame {
  t: number;
  state: PublicGame;
}

export interface GameReplayTimeline {
  startedAt?: number;
  defaultViewerId?: string;
  frames: GameReplayFrame[];
}
