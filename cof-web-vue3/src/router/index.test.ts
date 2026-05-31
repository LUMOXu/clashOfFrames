import { describe, expect, it } from "vitest";
import { router } from "./index";

describe("router", () => {
  it("exposes canonical room paths", () => {
    const waiting = router.getRoutes().find((r) => r.name === "waiting");
    expect(waiting?.path).toBe("/room/:roomId/waiting");
    const create = router.getRoutes().find((r) => r.name === "create-room");
    expect(create?.path).toBe("/rooms/create");
  });

  it("redirects legacy create-room path", () => {
    const legacy = router.getRoutes().find((r) => r.path === "/create-room");
    expect(legacy?.redirect).toBe("/rooms/create");
  });

  it("redirects legacy waiting path", () => {
    const legacy = router.getRoutes().find((r) => r.path === "/waiting/:roomId?");
    expect(typeof legacy?.redirect).toBe("function");
  });
});
