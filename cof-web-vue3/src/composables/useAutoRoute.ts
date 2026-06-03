import { watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore } from "@/stores/authStore";
import { useRoomStore } from "@/stores/roomStore";
import { useGameStore } from "@/stores/gameStore";
import type { RoomSummary } from "@/types/api";

const ROOM_ROUTES = new Set(["waiting", "settings", "loading", "game"]);

export function isRoomParticipant(room: RoomSummary, clientId: string): boolean {
  if (!clientId) {
    return true;
  }
  return Boolean(room.players?.includes(clientId) || room.spectators?.includes(clientId));
}

export function useAutoRoute(): void {
  const router = useRouter();
  const route = useRoute();
  const auth = useAuthStore();
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
      if (!isRoomParticipant(room, auth.clientId)) {
        roomStore.clearRoom();
        gameStore.clearGame();
        void router.push({ name: "home" });
        return;
      }

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

      const gameId = room.gameId || gameStore.currentGame?.id;
      const gameLoading = gameStatus === "loading" || (!gameStatus && status === "loading");

      if (gameLoading && currentRoute !== "loading") {
        void router.push({
          name: "loading",
          params: { roomId },
          query: gameId ? { gameId } : undefined,
        });
        return;
      }

      if (status === "playing" && gameStatus === "playing" && currentRoute !== "game") {
        void router.push({
          name: "game",
          params: { roomId },
          query: gameId ? { gameId } : undefined,
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
