import { unwrap } from "./client";

export async function getRoomAssets(roomId: string): Promise<{
  key: string;
  assets: string[];
  libraries: unknown[];
}> {
  return unwrap({
    method: "GET",
    url: `/assets/rooms/${encodeURIComponent(roomId)}`,
  });
}
