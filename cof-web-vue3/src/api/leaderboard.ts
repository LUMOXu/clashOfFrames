import { unwrap } from "./client";
import type { LeaderboardData, LeaderboardEntry } from "@/types/api";

export async function fetchLeaderboard(): Promise<LeaderboardData> {
  const data = await unwrap<LeaderboardData | LeaderboardEntry[]>({ method: "GET", url: "/leaderboard" });
  if (Array.isArray(data)) {
    return { players: data, matches: [] };
  }
  return {
    players: data.players ?? [],
    matches: data.matches ?? [],
  };
}
