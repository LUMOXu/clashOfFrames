export interface PlayerIdentity {
  username?: string;
  statsId?: string;
  computerId?: string;
  isComputer?: boolean;
  godSlayer?: boolean;
}

export type PlayerNameKind = "normal" | "god" | "slayer";

export function playerNameKind(player: PlayerIdentity): PlayerNameKind {
  if (player.computerId === "computer_god" || player.username?.trim().toUpperCase() === "GOD") return "god";
  if (player.godSlayer) return "slayer";
  return "normal";
}

export function playerFontUrl(player: PlayerIdentity): string | null {
  const kind = playerNameKind(player);
  if (kind === "god") return "/api/v1/fonts/god-name-subset.woff2";
  if (kind === "slayer" && player.statsId) {
    return `/api/v1/fonts/players/${encodeURIComponent(player.statsId)}.woff2`;
  }
  return null;
}
