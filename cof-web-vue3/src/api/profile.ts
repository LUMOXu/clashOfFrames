import { unwrap } from "./client";
import type { ProfileData } from "@/types/api";

export async function fetchProfile(clientId: string): Promise<ProfileData> {
  return unwrap<ProfileData>({
    method: "GET",
    url: `/profile/${encodeURIComponent(clientId)}`,
  });
}

export interface MatchReplayData {
  replay: {
    gameId: string;
    roomId?: string;
    playedAt?: number;
    logText: string;
    summary?: Record<string, unknown>;
  };
}

export async function fetchMatchReplay(clientId: string, gameId: string): Promise<MatchReplayData> {
  return unwrap<MatchReplayData>({
    method: "GET",
    url: `/profile/${encodeURIComponent(clientId)}/games/${encodeURIComponent(gameId)}/replay`,
  });
}
