import { unwrap } from "./client";
import type { PublicGame } from "@/types/api";

export async function getGame(gameId: string): Promise<PublicGame> {
  return unwrap<PublicGame>({
    method: "GET",
    url: `/games/${encodeURIComponent(gameId)}`,
  });
}

export async function playCard(gameId: string): Promise<PublicGame> {
  return unwrap<PublicGame>({
    method: "POST",
    url: `/games/${encodeURIComponent(gameId)}/play-card`,
  });
}

export async function ringBell(gameId: string): Promise<PublicGame> {
  return unwrap<PublicGame>({
    method: "POST",
    url: `/games/${encodeURIComponent(gameId)}/ring-bell`,
  });
}
