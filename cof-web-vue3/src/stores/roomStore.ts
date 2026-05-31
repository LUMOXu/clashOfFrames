import { defineStore } from "pinia";
import { ref } from "vue";
import * as roomsApi from "@/api/rooms";
import type { GameSettings, RoomSummary } from "@/types/api";

export const useRoomStore = defineStore("room", () => {
  const rooms = ref<RoomSummary[]>([]);
  const currentRoom = ref<RoomSummary | null>(null);
  const activeRoomId = ref<string | null>(null);
  const loading = ref(false);
  const message = ref("");

  async function fetchRooms(all = false): Promise<void> {
    loading.value = true;
    try {
      const result = await roomsApi.listRooms(all);
      rooms.value = result.rooms ?? [];
    } finally {
      loading.value = false;
    }
  }

  async function createRoom(settings?: GameSettings, computerIds: string[] = []): Promise<RoomSummary> {
    loading.value = true;
    try {
      const result = await roomsApi.createRoom({ settings, computerIds });
      currentRoom.value = result.room;
      activeRoomId.value = result.room.id;
      return result.room;
    } finally {
      loading.value = false;
    }
  }

  async function joinRoom(roomId: string): Promise<RoomSummary> {
    loading.value = true;
    try {
      const result = await roomsApi.joinRoom(roomId);
      const room = result.room ?? { id: roomId, status: "waiting" };
      currentRoom.value = room;
      activeRoomId.value = room.id;
      return room;
    } finally {
      loading.value = false;
    }
  }

  async function refreshRoom(roomId: string): Promise<void> {
    const result = await roomsApi.joinRoom(roomId);
    if (result.room) {
      currentRoom.value = result.room;
    }
  }

  function setCurrentRoom(room: RoomSummary | null): void {
    currentRoom.value = room;
    activeRoomId.value = room?.id ?? null;
  }

  function applyRoomUpdate(room: RoomSummary): void {
    currentRoom.value = room;
    activeRoomId.value = room.id;
  }

  function clearRoom(): void {
    currentRoom.value = null;
    activeRoomId.value = null;
  }

  return {
    rooms,
    currentRoom,
    activeRoomId,
    loading,
    message,
    fetchRooms,
    createRoom,
    joinRoom,
    refreshRoom,
    setCurrentRoom,
    applyRoomUpdate,
    clearRoom,
  };
});
