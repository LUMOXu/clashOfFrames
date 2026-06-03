import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import SettingsView from "./SettingsView.vue";
import * as metaApi from "@/api/meta";
import * as roomsApi from "@/api/rooms";
import { useAuthStore } from "@/stores/authStore";
import { useRoomStore } from "@/stores/roomStore";
import type { RoomSummary } from "@/types/api";

const routerMock = vi.hoisted(() => ({
  push: vi.fn(),
  route: { params: { roomId: "room-1" }, query: {} as Record<string, string> },
}));

vi.mock("vue-router", () => ({
  RouterLink: {
    props: ["to"],
    template: "<a><slot /></a>",
  },
  useRouter: () => ({ push: routerMock.push }),
  useRoute: () => routerMock.route,
}));

vi.mock("@/api/meta");
vi.mock("@/api/rooms");

const room: RoomSummary = {
  id: "room-1",
  hostId: "host-1",
  status: "waiting",
  players: ["host-1"],
  settings: {
    minPlayers: 2,
    maxPlayers: 8,
    isPublic: true,
    libraryIds: ["deck-a"],
    libraryCopies: { "deck-a": 1 },
    startVoteThresholdMode: "auto",
    startVoteThreshold: 2,
    allowEmptyBell: false,
    randomBacks: false,
    conflictResolution: true,
    disconnectProtection: true,
  },
};

async function flushPromises(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
}

describe("SettingsView", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    setActivePinia(createPinia());
    routerMock.push.mockClear();
    vi.mocked(metaApi.fetchCardLibraries).mockResolvedValue({ libraries: [] });
    vi.mocked(metaApi.fetchComputerPlayers).mockResolvedValue({ players: [] });
    vi.mocked(roomsApi.joinRoom).mockResolvedValue({ room });
    vi.mocked(roomsApi.updateRoomSettings).mockResolvedValue({ room });
    const auth = useAuthStore();
    auth.player = { clientId: "host-1", username: "Host" };
    const roomStore = useRoomStore();
    roomStore.setCurrentRoom(room);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows auto-save feedback as a fixed toast instead of a layout row", async () => {
    const wrapper = mount(SettingsView, {
      global: {
        stubs: {
          AppShell: { template: "<div><slot /></div>" },
          LibraryPicker: { template: "<div />" },
        },
      },
    });
    await flushPromises();
    vi.mocked(roomsApi.updateRoomSettings).mockClear();

    await wrapper.find('input[type="number"]').setValue(3);
    await vi.advanceTimersByTimeAsync(220);
    await flushPromises();

    expect(roomsApi.updateRoomSettings).toHaveBeenCalled();
    expect(wrapper.find(".settings-save-toast").exists()).toBe(true);
    expect(wrapper.find("p.muted").exists()).toBe(false);
  });
});
