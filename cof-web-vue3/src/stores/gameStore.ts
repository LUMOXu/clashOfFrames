import { defineStore } from "pinia";
import { ref } from "vue";
import * as gamesApi from "@/api/games";
import * as roomsApi from "@/api/rooms";
import { GameSocket, parseSyncMessage } from "@/ws/gameSocket";
import type { PublicGame } from "@/types/api";
import type { ParsedSyncMessage } from "@/types/ws";

export const useGameStore = defineStore("game", () => {
  const currentGame = ref<PublicGame | null>(null);
  const lastSync = ref<ParsedSyncMessage | null>(null);
  const loadingProgress = ref({ loaded: 0, total: 0, done: false });
  const socket = new GameSocket();
  const connected = ref(false);

  async function loadGame(gameId: string): Promise<PublicGame> {
    const game = await gamesApi.getGame(gameId);
    currentGame.value = game;
    return game;
  }

  async function startGame(roomId: string): Promise<PublicGame> {
    const result = await roomsApi.startRoom(roomId);
    currentGame.value = result.game;
    return result.game;
  }

  async function playCard(gameId: string): Promise<PublicGame> {
    const game = await gamesApi.playCard(gameId);
    currentGame.value = game;
    return game;
  }

  async function ringBell(gameId: string): Promise<PublicGame> {
    const game = await gamesApi.ringBell(gameId);
    currentGame.value = game;
    return game;
  }

  function connectSocket(token: string): void {
    socket.connect(token);
    connected.value = true;
    socket.onMessage((message) => {
      if (message.t.toUpperCase() === "SYNC") {
        const parsed = parseSyncMessage(JSON.stringify(message));
        if (parsed) {
          lastSync.value = parsed;
          if (currentGame.value && currentGame.value.id === parsed.gameId) {
            currentGame.value = {
              ...currentGame.value,
              turnIndex: parsed.turnIndex,
              playCount: parsed.playCount,
            };
          }
        }
      }
    });
  }

  function disconnectSocket(): void {
    socket.disconnect();
    connected.value = false;
  }

  function requestLoad(roomId?: string, gameId?: string): void {
    socket.send({
      t: "LOAD",
      r: roomId,
      g: gameId,
    });
  }

  function setLoadingProgress(loaded: number, total: number, done = false): void {
    loadingProgress.value = { loaded, total, done };
  }

  function clearGame(): void {
    currentGame.value = null;
    lastSync.value = null;
    loadingProgress.value = { loaded: 0, total: 0, done: false };
    disconnectSocket();
  }

  return {
    currentGame,
    lastSync,
    loadingProgress,
    connected,
    loadGame,
    startGame,
    playCard,
    ringBell,
    connectSocket,
    disconnectSocket,
    requestLoad,
    setLoadingProgress,
    clearGame,
  };
});
