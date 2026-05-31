import { describe, expect, it, vi, beforeEach } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useRoomStore } from "./roomStore";
import * as roomsApi from "@/api/rooms";

vi.mock("@/api/rooms");

describe("roomStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("fetches room list", async () => {
    vi.mocked(roomsApi.listRooms).mockResolvedValue({
      rooms: [{ id: "room-a", status: "waiting" }],
    });
    const store = useRoomStore();
    await store.fetchRooms();
    expect(store.rooms[0].id).toBe("room-a");
  });

  it("creates and tracks current room", async () => {
    vi.mocked(roomsApi.createRoom).mockResolvedValue({
      room: { id: "room-b", status: "waiting" },
    });
    const store = useRoomStore();
    const room = await store.createRoom();
    expect(room.id).toBe("room-b");
    expect(store.activeRoomId).toBe("room-b");
  });
});
