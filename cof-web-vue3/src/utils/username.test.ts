import { describe, expect, it } from "vitest";
import { GOD_USERNAME_HINT, godUsernameError } from "./username";

describe("godUsernameError", () => {
  it.each(["GOD", "god", "myGodName", "godzilla", "  myGodName  "])(
    "rejects GOD-containing username %s",
    (username) => {
      expect(godUsernameError(username)).toBe(GOD_USERNAME_HINT);
    },
  );

  it.each(["good-player", "g0d", "deity", "  alice  "])(
    "accepts unrelated username %s",
    (username) => {
      expect(godUsernameError(username)).toBeNull();
    },
  );

  it("exports the visible registration hint", () => {
    expect(GOD_USERNAME_HINT).toBe("用户名不能包含神的名讳");
  });
});
