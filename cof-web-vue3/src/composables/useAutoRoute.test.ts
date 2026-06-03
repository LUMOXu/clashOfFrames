import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { defineComponent, nextTick } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAutoRoute } from "./useAutoRoute";
import { useAuthStore } from "@/stores/authStore";
import { useGameStore } from "@/stores/gameStore";
import { useRoomStore } from "@/stores/roomStore";

const routerMock = vi.hoisted(() => ({
  push: vi.fn(),
  route: { name: "waiting" as string },
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: routerMock.push }),
  useRoute: () => routerMock.route,
}));

const Harness = defineComponent({
  setup() {
    useAutoRoute();
    return () => null;
  },
});

describe("useAutoRoute", () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
    routerMock.push.mockClear();
    routerMock.route.name = "waiting";
  });

  it("returns a removed room player to the main menu", async () => {
    const auth = useAuthStore();
    auth.player = { clientId: "guest-2", username: "Guest 2" };
    const roomStore = useRoomStore();
    const gameStore = useGameStore();
    gameStore.currentGame = { id: "game-1", status: "loading" };

    mount(Harness);
    roomStore.setCurrentRoom({
      id: "room-1",
      status: "loading",
      gameId: "game-1",
      players: ["host-1"],
      spectators: [],
    });
    await nextTick();

    expect(routerMock.push).toHaveBeenCalledWith({ name: "home" });
    expect(roomStore.currentRoom).toBeNull();
    expect(gameStore.currentGame).toBeNull();
  });
});
