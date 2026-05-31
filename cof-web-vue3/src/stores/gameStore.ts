import { defineStore } from "pinia";
import { ref } from "vue";
import * as gamesApi from "@/api/games";
import * as roomsApi from "@/api/rooms";
import { applyGameSync } from "@/utils/applyGameSync";
import { handleAudioEvent } from "@/composables/useGameAudio";
import { GameSocket } from "@/ws/gameSocket";
import type { PublicGame, RoomSummary } from "@/types/api";
import type { WsMessage } from "@/types/ws";

export const useGameStore = defineStore("game", () => {
  const currentGame = ref<PublicGame | null>(null);
  const loadingProgress = ref({ loaded: 0, total: 0, done: false });
  const socket = new GameSocket();
  const connected = ref(false);
  let roomHandler: ((room: RoomSummary) => void) | null = null;

  function applySync(sync: unknown): PublicGame | null {
    const next = applyGameSync(currentGame.value, sync);
    if (next) {
      currentGame.value = next;
    }
    return next;
  }

  async function loadGame(gameId: string): Promise<PublicGame> {
    const { game, sync } = await gamesApi.getGame(gameId);
    const merged = sync ? applySync(sync) : null;
    currentGame.value = merged ?? game;
    return currentGame.value;
  }

  async function startGame(roomId: string): Promise<PublicGame> {
    const result = await roomsApi.startRoom(roomId);
    currentGame.value = result.game;
    return result.game;
  }

  async function playCard(gameId: string): Promise<PublicGame | null> {
    const sync = await gamesApi.playCard(gameId);
    return applySync(sync);
  }

  async function ringBell(gameId: string): Promise<PublicGame | null> {
    const sync = await gamesApi.ringBell(gameId);
    return applySync(sync);
  }

  async function continueGame(gameId: string): Promise<void> {
    const result = await gamesApi.continueGame(gameId);
    currentGame.value = result.game;
    if (roomHandler) {
      roomHandler(result.room);
    }
  }

  function onRoomUpdate(handler: (room: RoomSummary) => void): void {
    roomHandler = handler;
  }

  function connectSocket(token: string): void {
    if (connected.value) return;
    socket.connect(token);
    connected.value = true;
    socket.onMessage((message: WsMessage) => {
      const type = message.t?.toUpperCase();
      if (type === "SYNC" && message.g) {
        if (!currentGame.value || currentGame.value.id === message.g) {
          applySync(message.sync);
        }
      }
      if (type === "ROOM" && message.room) {
        const room = message.room as RoomSummary;
        if (roomHandler) roomHandler(room);
        if (!room.gameId) {
          clearGame();
        } else if (!currentGame.value || currentGame.value.id !== room.gameId) {
          void loadGame(room.gameId);
        }
      }
      if (type === "AUDIO") {
        const roomId = message.r;
        if (!roomId || !currentGame.value?.roomId || roomId === currentGame.value.roomId) {
          handleAudioEvent(message.au);
        }
      }
    });
  }

  function disconnectSocket(): void {
    socket.disconnect();
    connected.value = false;
  }

  function requestLoad(roomId?: string, gameId?: string): void {
    socket.send({ t: "LOAD", r: roomId, g: gameId });
  }

  function setLoadingProgress(loaded: number, total: number, done = false): void {
    loadingProgress.value = { loaded, total, done };
  }

  function clearGame(): void {
    currentGame.value = null;
    loadingProgress.value = { loaded: 0, total: 0, done: false };
  }

  return {
    currentGame,
    loadingProgress,
    connected,
    loadGame,
    startGame,
    playCard,
    ringBell,
    continueGame,
    connectSocket,
    disconnectSocket,
    requestLoad,
    setLoadingProgress,
    clearGame,
    onRoomUpdate,
  };
});
