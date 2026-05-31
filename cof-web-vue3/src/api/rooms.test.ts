import { describe, expect, it, vi } from "vitest";
import { createRoom, listRooms, joinRoom } from "./rooms";
import { unwrap } from "./client";

vi.mock("./client", () => ({
  unwrap: vi.fn(),
}));

describe("rooms api", () => {
  it("lists rooms", async () => {
    vi.mocked(unwrap).mockResolvedValue({ rooms: [{ id: "r1", status: "waiting" }] });
    const result = await listRooms(true);
    expect(unwrap).toHaveBeenCalledWith(
      expect.objectContaining({ url: "/rooms", params: { all: "1" } }),
    );
    expect(result.rooms[0].id).toBe("r1");
  });

  it("creates room", async () => {
    vi.mocked(unwrap).mockResolvedValue({ room: { id: "r2", status: "waiting" } });
    await createRoom({ computerIds: [] });
    expect(unwrap).toHaveBeenCalledWith(expect.objectContaining({ method: "POST", url: "/rooms" }));
  });

  it("joins room", async () => {
    vi.mocked(unwrap).mockResolvedValue({ room: { id: "r3", status: "waiting" } });
    await joinRoom("r3");
    expect(unwrap).toHaveBeenCalledWith(
      expect.objectContaining({ url: "/rooms/r3/join" }),
    );
  });
});
