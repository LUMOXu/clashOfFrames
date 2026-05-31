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
