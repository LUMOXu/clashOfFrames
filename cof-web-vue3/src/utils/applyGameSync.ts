import type { PublicGame } from "@/types/api";

export function applyGameSync(previous: PublicGame | null, sync: unknown): PublicGame | null {
  if (!sync || typeof sync !== "object") {
    return previous;
  }
  const node = sync as Record<string, unknown>;
  if (node.full && typeof node.full === "object") {
    return node.full as PublicGame;
  }
  if (!previous) {
    return null;
  }
  const next: PublicGame = { ...previous, ...(node as Partial<PublicGame>) };
  if (typeof node.st === "string") next.status = node.st;
  if (typeof node.ti === "number") next.turnIndex = node.ti;
  if (typeof node.td === "number") next.turnDeadlineAt = node.td;
  if (typeof node.ta === "number") next.turnAvailableAt = node.ta;
  if (typeof node.lu === "number") next.lockedUntil = node.lu;
  if (typeof node.pc === "number") next.playCount = node.pc;
  if (typeof node.bc === "number") next.bellCount = node.bc;
  if (typeof node.w === "string") next.winnerId = node.w;
  return next;
}
