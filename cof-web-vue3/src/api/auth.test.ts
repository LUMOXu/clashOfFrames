import { describe, expect, it, vi } from "vitest";
import * as authApi from "./auth";
import { unwrap } from "./client";

vi.mock("./client", () => ({
  unwrap: vi.fn(),
}));

describe("auth api", () => {
  it("login posts credentials", async () => {
    vi.mocked(unwrap).mockResolvedValue({
      token: "t1",
      player: { clientId: "c1", username: "alice" },
    });
    const result = await authApi.login({ username: "alice", password: "secret" });
    expect(unwrap).toHaveBeenCalledWith(
      expect.objectContaining({ method: "POST", url: "/auth/login" }),
    );
    expect(result.token).toBe("t1");
  });

  it("register posts credentials", async () => {
    vi.mocked(unwrap).mockResolvedValue({
      token: "t2",
      player: { clientId: "c2", username: "bob" },
    });
    await authApi.register({ username: "bob", password: "secret" });
    expect(unwrap).toHaveBeenCalledWith(
      expect.objectContaining({ method: "POST", url: "/auth/register" }),
    );
  });
});
