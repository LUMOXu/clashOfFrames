import { describe, expect, it } from "vitest";
import type { GameSettings } from "@/types/api";
import type { CardLibraryMeta } from "@/types/computer";
import { canAddLibrary, maxCopiesWithinRoomLimit, selectedCardTotal } from "./libraryCardLimit";

const libraries: CardLibraryMeta[] = [
  { id: "a", name: "A", cardCount: 60 },
  { id: "b", name: "B", cardCount: 40 },
  { id: "small", name: "Small", cardCount: 10 },
];

function settings(ids: string[], copies: Record<string, number>): GameSettings {
  return { libraryIds: ids, libraryCopies: copies };
}

describe("library card room limit", () => {
  it("calculates total cards across selected libraries", () => {
    expect(selectedCardTotal(libraries, settings(["a", "b"], { a: 2, b: 2 }))).toBe(200);
  });

  it("shares the 216-card capacity between libraries", () => {
    expect(maxCopiesWithinRoomLimit(libraries, settings(["a", "b"], { a: 2, b: 1 }), libraries[1])).toBe(2);
  });

  it("allows one small library beyond the former 120-card cap", () => {
    expect(maxCopiesWithinRoomLimit(libraries, settings(["small"], { small: 1 }), libraries[2])).toBe(21);
  });

  it("rejects adding a first copy when no global capacity remains", () => {
    expect(canAddLibrary(libraries, settings(["a"], { a: 3 }), libraries[1])).toBe(false);
  });
});
