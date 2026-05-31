import { unwrap } from "./client";
import type { GameSettings, PublicGame, RoomSummary } from "@/types/api";

export async function listRooms(all = false): Promise<{ rooms: RoomSummary[] }> {
  return unwrap<{ rooms: RoomSummary[] }>({
    method: "GET",
    url: "/rooms",
    params: all ? { all: "1" } : undefined,
  });
}

export async function createRoom(body: {
  settings?: GameSettings;
  computerIds?: string[];
}): Promise<{ room: RoomSummary }> {
  return unwrap<{ room: RoomSummary }>({
    method: "POST",
    url: "/rooms",
    data: body,
  });
}

export async function joinRoom(roomId: string): Promise<{
  room: RoomSummary;
  game?: PublicGame;
  spectator?: boolean;
}> {
  return unwrap({
    method: "POST",
    url: `/rooms/${encodeURIComponent(roomId)}/join`,
  });
}

export async function leaveRoom(roomId: string): Promise<Record<string, unknown>> {
  return unwrap({
    method: "POST",
    url: `/rooms/${encodeURIComponent(roomId)}/leave`,
  });
}

export async function disbandRoom(roomId: string): Promise<void> {
  await unwrap({
    method: "POST",
    url: `/rooms/${encodeURIComponent(roomId)}/disband`,
  });
}

export async function updateRoomSettings(
  roomId: string,
  settings: GameSettings,
): Promise<{ room: RoomSummary }> {
  return unwrap({
    method: "PATCH",
    url: `/rooms/${encodeURIComponent(roomId)}/settings`,
    data: { settings },
  });
}

export async function startRoom(roomId: string): Promise<{ room: RoomSummary; game: PublicGame }> {
  return unwrap({
    method: "POST",
    url: `/rooms/${encodeURIComponent(roomId)}/start`,
  });
}

export async function startVote(roomId: string): Promise<{ room: RoomSummary }> {
  return unwrap({
    method: "POST",
    url: `/rooms/${encodeURIComponent(roomId)}/start-vote`,
  });
}

export async function cancelStartVote(roomId: string): Promise<{ room: RoomSummary }> {
  return unwrap({
    method: "POST",
    url: `/rooms/${encodeURIComponent(roomId)}/cancel-start-vote`,
  });
}

export async function addComputer(roomId: string, computerId: string): Promise<{ room: RoomSummary }> {
  return unwrap({
    method: "POST",
    url: `/rooms/${encodeURIComponent(roomId)}/add-computer`,
    data: { computerId },
  });
}

export async function removeComputer(roomId: string, computerId: string): Promise<{ room: RoomSummary }> {
  return unwrap({
    method: "POST",
    url: `/rooms/${encodeURIComponent(roomId)}/remove-computer`,
    data: { computerId },
  });
}

export async function transferHost(roomId: string, newHostId: string): Promise<{ room: RoomSummary }> {
  return unwrap({
    method: "POST",
    url: `/rooms/${encodeURIComponent(roomId)}/transfer-host`,
    data: { newHostId },
  });
}

export async function postChat(roomId: string, message: string): Promise<void> {
  await unwrap({
    method: "POST",
    url: `/rooms/${encodeURIComponent(roomId)}/chat`,
    data: { message },
  });
}

export async function reportLoadingProgress(
  roomId: string,
  body: { loaded: number; total: number; done?: boolean; cached?: boolean },
): Promise<{ room: RoomSummary; game: PublicGame }> {
  return unwrap({
    method: "POST",
    url: `/rooms/${encodeURIComponent(roomId)}/loading-progress`,
    data: body,
  });
}
