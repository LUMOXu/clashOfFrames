import { describe, expect, it, vi } from "vitest";
import { bootstrap } from "./session";
import { unwrap } from "./client";

vi.mock("./client", () => ({
  unwrap: vi.fn(),
}));

describe("session api", () => {
  it("bootstrap calls session endpoint", async () => {
    vi.mocked(unwrap).mockResolvedValue({ libraries: [], player: null });
    const data = await bootstrap();
    expect(unwrap).toHaveBeenCalledWith(
      expect.objectContaining({ method: "GET", url: "/session/bootstrap" }),
    );
    expect(data.libraries).toEqual([]);
  });
});
