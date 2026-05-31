import { unwrap } from "./client";
import type { PublicGame, RoomSummary } from "@/types/api";

export async function listRooms(all = false): Promise<{ rooms: RoomSummary[] }> {
  return unwrap<{ rooms: RoomSummary[] }>({
    method: "GET",
    url: "/rooms",
    params: all ? { all: "1" } : undefined,
  });
}

export async function createRoom(body: {
  settings?: Record<string, unknown>;
  computerIds?: string[];
}): Promise<{ room: RoomSummary }> {
  return unwrap<{ room: RoomSummary }>({
    method: "POST",
    url: "/rooms",
    data: body,
  });
}

export async function joinRoom(roomId: string): Promise<Record<string, unknown>> {
  return unwrap<Record<string, unknown>>({
    method: "POST",
    url: `/rooms/${encodeURIComponent(roomId)}/join`,
  });
}

export async function startRoom(roomId: string): Promise<{
  room: RoomSummary;
  game: PublicGame;
}> {
  return unwrap<{ room: RoomSummary; game: PublicGame }>({
    method: "POST",
    url: `/rooms/${encodeURIComponent(roomId)}/start`,
  });
}
