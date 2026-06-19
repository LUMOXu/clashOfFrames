import { afterEach, describe, expect, it, vi } from "vitest";
import { copyText } from "./clipboard";

describe("copyText", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("copies through the legacy selection path on an HTTP page", async () => {
    Object.defineProperty(window, "isSecureContext", { configurable: true, value: false });
    const copy = vi.fn(() => true);
    Object.defineProperty(document, "execCommand", { configurable: true, value: copy });

    await expect(copyText("room-123")).resolves.toBe(true);
    expect(copy).toHaveBeenCalledWith("copy");
    expect(document.querySelector("textarea")).toBeNull();
  });
});
