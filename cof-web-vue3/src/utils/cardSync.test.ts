import { describe, expect, it } from "vitest";
import { expandCompactCard } from "./cardSync";

describe("cardSync", () => {
  it("builds canonical face and back urls", () => {
    const face = expandCompactCard(
      { i: "c1", l: "基础包@ThePMVPanel'25", p: 4, s: 2 },
      { libraryIds: ["基础包@ThePMVPanel'25"] },
    );
    expect(face.imageUrl).toContain("/cards/");
    expect(face.imageUrl).toContain("/4/a.jpg");
    const back = expandCompactCard({ i: "c2", l: "基础包@ThePMVPanel'25", b: 1 });
    expect(back.imageUrl).toBeUndefined();
    expect(back.backUrl).toContain("/back.png");
  });

  it("uses server-provided urls when present", () => {
    const card = expandCompactCard({
      i: "c1",
      l: "1",
      p: 5,
      u: "/cards/1/5/a.jpg",
      bk: "/cards/基础包@ThePMVPanel'25/back.png",
    });
    expect(card.imageUrl).toBe("/cards/1/5/a.jpg");
    expect(card.backUrl).toBe("/cards/基础包@ThePMVPanel'25/back.png");
  });
});
