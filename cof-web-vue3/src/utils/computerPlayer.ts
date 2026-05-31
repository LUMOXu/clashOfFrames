import type { ComputerPlayer } from "@/types/computer";

type Raw = Record<string, unknown>;

function pickNum(raw: Raw, camel: string, snake: string): number | undefined {
  const v = raw[camel] ?? raw[snake];
  if (v === null || v === undefined || v === "") return undefined;
  const n = Number(v);
  return Number.isFinite(n) ? n : undefined;
}

/** 兼容 API camelCase / snake_case，并合并缺省数值 */
export function normalizeComputerPlayer(raw: unknown): ComputerPlayer {
  const r = (raw && typeof raw === "object" ? raw : {}) as Raw;
  const id = String(r.id ?? r.computer_id ?? "");
  return {
    id,
    name: String(r.name ?? ""),
    description: r.description != null ? String(r.description) : undefined,
    playDelayMeanSeconds: pickNum(r, "playDelayMeanSeconds", "play_delay_mean_seconds"),
    playDelayStdSeconds: pickNum(r, "playDelayStdSeconds", "play_delay_std_seconds"),
    reactionMeanSeconds: pickNum(r, "reactionMeanSeconds", "reaction_mean_seconds"),
    reactionStdSeconds: pickNum(r, "reactionStdSeconds", "reaction_std_seconds"),
    matchDetectionProbability: pickNum(r, "matchDetectionProbability", "match_detection_probability"),
    falseRingProbability: pickNum(r, "falseRingProbability", "false_ring_probability"),
  };
}

export function normalizeComputerPlayers(list: unknown[]): ComputerPlayer[] {
  return list.map(normalizeComputerPlayer).filter((p) => p.id);
}

export function resolveComputerId(player: {
  clientId?: string;
  computerId?: string;
  isComputer?: boolean;
}): string | undefined {
  if (player.computerId) return player.computerId;
  if (player.isComputer && player.clientId?.startsWith("computer:")) {
    return player.clientId.slice(player.clientId.lastIndexOf(":") + 1);
  }
  return undefined;
}
