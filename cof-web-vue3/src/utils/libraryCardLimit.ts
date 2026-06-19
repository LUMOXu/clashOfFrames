import type { GameSettings } from "@/types/api";
import type { CardLibraryMeta } from "@/types/computer";

export const MAX_ROOM_CARDS = 216;

function cardCount(library: CardLibraryMeta): number {
  return Math.max(1, Number(library.cardCount) || 0);
}

export function selectedCardTotal(
  libraries: CardLibraryMeta[],
  settings: GameSettings,
  excludeLibraryId?: string,
): number {
  const selected = new Set(settings.libraryIds ?? []);
  return libraries.reduce((total, library) => {
    if (!selected.has(library.id) || library.id === excludeLibraryId) return total;
    const copies = Math.max(1, Number(settings.libraryCopies?.[library.id]) || 1);
    return total + cardCount(library) * copies;
  }, 0);
}

export function maxCopiesWithinRoomLimit(
  libraries: CardLibraryMeta[],
  settings: GameSettings,
  library: CardLibraryMeta,
): number {
  const otherCards = selectedCardTotal(libraries, settings, library.id);
  return Math.max(1, Math.floor(Math.max(0, MAX_ROOM_CARDS - otherCards) / cardCount(library)));
}

export function canAddLibrary(
  libraries: CardLibraryMeta[],
  settings: GameSettings,
  library: CardLibraryMeta,
): boolean {
  if (settings.libraryIds?.includes(library.id)) return true;
  return selectedCardTotal(libraries, settings) + cardCount(library) <= MAX_ROOM_CARDS;
}
