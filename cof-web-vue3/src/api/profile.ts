import { unwrap } from "./client";
import type { ProfileData } from "@/types/api";

export async function fetchProfile(clientId: string): Promise<ProfileData> {
  return unwrap<ProfileData>({
    method: "GET",
    url: `/profile/${encodeURIComponent(clientId)}`,
  });
}
