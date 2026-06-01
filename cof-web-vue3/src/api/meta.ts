import { unwrap } from "./client";

export async function fetchCardLibraries(): Promise<{ libraries: unknown[] }> {
  return unwrap<{ libraries: unknown[] }>({
    method: "GET",
    url: "/meta/card-libraries",
  });
}

export async function fetchComputerPlayers(): Promise<{ players: unknown[] }> {
  return unwrap<{ players: unknown[] }>({
    method: "GET",
    url: "/meta/computer-players",
  });
}

export async function fetchPmvIndex(): Promise<unknown[]> {
  return unwrap<unknown[]>({ method: "GET", url: "/meta/pmv-index" });
}

export async function fetchCardViewer(libraryIds: string[]): Promise<import("@/types/api").CardViewerPayload> {
  const ids = libraryIds.map((id) => id.trim()).filter(Boolean);
  return unwrap({
    method: "GET",
    url: "/meta/card-viewer",
    params: { libraryIds: ids.join(",") },
    paramsSerializer: {
      indexes: null,
    },
  });
}
