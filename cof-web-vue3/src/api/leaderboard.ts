import { unwrap } from "./client";
import type { LeaderboardEntry } from "@/types/api";

export async function fetchLeaderboard(): Promise<LeaderboardEntry[]> {
  return unwrap<LeaderboardEntry[]>({ method: "GET", url: "/leaderboard" });
}
