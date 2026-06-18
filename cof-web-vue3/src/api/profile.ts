import { unwrap } from "./client";
import type { ProfileData } from "@/types/api";

export async function fetchProfile(clientId: string): Promise<ProfileData> {
  return unwrap<ProfileData>({
    method: "GET",
    url: `/profile/${encodeURIComponent(clientId)}`,
  });
}

export async function acknowledgeGodSlayerReward(clientId: string): Promise<ProfileData> {
  return unwrap<ProfileData>({
    method: "POST",
    url: `/profile/${encodeURIComponent(clientId)}/god-slayer-reward/acknowledge`,
  });
}

export interface MatchReplayData {
  replay: {
    gameId: string;
    roomId?: string;
    playedAt?: number;
    logText?: string;
    replayJson?: string;
    summary?: Record<string, unknown>;
  };
}

export async function fetchMatchReplay(clientId: string, gameId: string): Promise<MatchReplayData> {
  return unwrap<MatchReplayData>({
    method: "GET",
    url: `/profile/${encodeURIComponent(clientId)}/games/${encodeURIComponent(gameId)}/replay`,
  });
}
