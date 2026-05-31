import { describe, expect, it, vi, beforeEach } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useAuthStore } from "./authStore";
import * as authApi from "@/api/auth";
import * as sessionApi from "@/api/session";

vi.mock("@/api/auth");
vi.mock("@/api/session");

describe("authStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
  });

  it("login stores token and player", async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      token: "tok",
      player: { clientId: "c1", username: "tester" },
    });
    vi.mocked(sessionApi.bootstrap).mockResolvedValue({
      player: { clientId: "c1", username: "tester" },
      libraries: [],
    });

    const store = useAuthStore();
    await store.login("tester", "pass");

    expect(store.token).toBe("tok");
    expect(store.player?.username).toBe("tester");
    expect(localStorage.getItem("cof.token")).toBe("tok");
  });

  it("logout clears session", async () => {
    vi.mocked(authApi.logout).mockResolvedValue(undefined);
    const store = useAuthStore();
    store.token = "tok";
    store.player = { clientId: "c1", username: "tester" };
    await store.logout();
    expect(store.token).toBe("");
    expect(store.player).toBeNull();
  });
});
