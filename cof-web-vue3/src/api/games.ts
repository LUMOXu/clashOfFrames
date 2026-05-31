import { unwrap } from "./client";
import type { PublicGame } from "@/types/api";

export interface GameLoadResponse {
  game: PublicGame;
  sync?: unknown;
}

export interface GameActionResponse {
  sync: unknown;
}

export async function getGame(gameId: string): Promise<GameLoadResponse> {
  const result = await unwrap<GameLoadResponse | PublicGame>({
    method: "GET",
    url: `/games/${encodeURIComponent(gameId)}`,
  });
  if (result && typeof result === "object" && "game" in result) {
    return result as GameLoadResponse;
  }
  return { game: result as PublicGame };
}

export async function playCard(gameId: string): Promise<unknown> {
  const result = await unwrap<GameActionResponse | PublicGame>({
    method: "POST",
    url: `/games/${encodeURIComponent(gameId)}/play-card`,
  });
  if (result && typeof result === "object" && "sync" in result) {
    return (result as GameActionResponse).sync;
  }
  return { full: result };
}

export async function ringBell(gameId: string): Promise<unknown> {
  const result = await unwrap<GameActionResponse | PublicGame>({
    method: "POST",
    url: `/games/${encodeURIComponent(gameId)}/ring-bell`,
  });
  if (result && typeof result === "object" && "sync" in result) {
    return (result as GameActionResponse).sync;
  }
  return { full: result };
}

export async function continueGame(gameId: string): Promise<{ room: import("@/types/api").RoomSummary; game: PublicGame }> {
  return unwrap({
    method: "POST",
    url: `/games/${encodeURIComponent(gameId)}/continue`,
  });
}
