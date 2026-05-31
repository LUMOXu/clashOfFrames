import { watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useRoomStore } from "@/stores/roomStore";
import { useGameStore } from "@/stores/gameStore";
import type { RoomSummary } from "@/types/api";

const ROOM_ROUTES = new Set(["waiting", "settings", "loading", "game"]);

export function useAutoRoute(): void {
  const router = useRouter();
  const route = useRoute();
  const roomStore = useRoomStore();
  const gameStore = useGameStore();

  watch(
    () => [roomStore.currentRoom?.status, roomStore.currentRoom?.id, gameStore.currentGame?.status] as const,
    ([status, roomId, gameStatus]) => {
      if (!roomId || !ROOM_ROUTES.has(String(route.name))) {
        return;
      }
      const room = roomStore.currentRoom;
      if (!room) return;

      const currentRoute = String(route.name);

      if (status === "waiting" && currentRoute !== "waiting" && currentRoute !== "settings") {
        if (currentRoute === "loading") {
          return;
        }
        if (gameStore.currentGame) {
          gameStore.clearGame();
        }
        void router.push({ name: "waiting", params: { roomId } });
        return;
      }

      if (status === "loading" && currentRoute !== "loading") {
        void router.push({
          name: "loading",
          params: { roomId },
          query: { gameId: room.gameId || gameStore.currentGame?.id },
        });
        return;
      }

      if ((status === "playing" || gameStatus === "playing") && currentRoute !== "game") {
        void router.push({
          name: "game",
          params: { roomId },
          query: { gameId: room.gameId || gameStore.currentGame?.id },
        });
      }
    },
  );
}

export function syncRoomFromBootstrap(currentRoom: RoomSummary | null, currentGame: unknown): void {
  const roomStore = useRoomStore();
  const gameStore = useGameStore();
  if (currentRoom) {
    roomStore.setCurrentRoom(currentRoom);
  }
  if (currentGame && typeof currentGame === "object") {
    gameStore.currentGame = currentGame as import("@/types/api").PublicGame;
  }
}
