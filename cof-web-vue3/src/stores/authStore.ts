import { defineStore } from "pinia";
import { ref, computed } from "vue";
import * as authApi from "@/api/auth";
import * as sessionApi from "@/api/session";
import { setTokenProvider, setUnauthorizedHandler } from "@/api/client";
import type { BootstrapData, PublicPlayer } from "@/types/api";

const TOKEN_KEY = "cof.token";
const PLAYER_KEY = "cof.player";

function loadStoredPlayer(): PublicPlayer | null {
  try {
    const raw = localStorage.getItem(PLAYER_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<PublicPlayer>;
    if (typeof parsed.clientId === "string" && typeof parsed.username === "string") {
      return { clientId: parsed.clientId, username: parsed.username };
    }
  } catch {
    localStorage.removeItem(PLAYER_KEY);
  }
  return null;
}

export const useAuthStore = defineStore("auth", () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || "");
  const player = ref<PublicPlayer | null>(token.value ? loadStoredPlayer() : null);
  const bootstrap = ref<BootstrapData | null>(null);
  const loading = ref(false);
  const message = ref("");

  const isAuthenticated = computed(() => Boolean(token.value && player.value));
  const clientId = computed(() => player.value?.clientId ?? "");

  function applyToken(next: string): void {
    token.value = next;
    if (next) {
      localStorage.setItem(TOKEN_KEY, next);
    } else {
      localStorage.removeItem(TOKEN_KEY);
    }
  }

  function applyPlayer(next: PublicPlayer | null): void {
    player.value = next;
    if (next) {
      localStorage.setItem(
        PLAYER_KEY,
        JSON.stringify({ clientId: next.clientId, username: next.username }),
      );
    } else {
      localStorage.removeItem(PLAYER_KEY);
    }
  }

  function clearSession(): void {
    applyToken("");
    applyPlayer(null);
    bootstrap.value = null;
  }

  setTokenProvider(() => token.value || null);
  setUnauthorizedHandler(() => {
    clearSession();
    message.value = "请重新登录。";
  });

  async function refreshBootstrap(): Promise<BootstrapData> {
    loading.value = true;
    try {
      const data = await sessionApi.bootstrap();
      bootstrap.value = data;
      applyPlayer(data.player ?? null);
      if (!data.player && token.value) {
        clearSession();
      }
      const { useRoomStore } = await import("@/stores/roomStore");
      const { useGameStore } = await import("@/stores/gameStore");
      const roomStore = useRoomStore();
      const gameStore = useGameStore();
      if (data.currentRoom) {
        roomStore.setCurrentRoom(data.currentRoom);
      }
      if (data.currentGame) {
        gameStore.currentGame = data.currentGame;
      }
      return data;
    } finally {
      loading.value = false;
    }
  }

  async function login(username: string, password: string): Promise<void> {
    message.value = "";
    const result = await authApi.login({ username, password });
    applyToken(result.token);
    applyPlayer(result.player);
    await refreshBootstrap().catch(() => undefined);
  }

  async function register(username: string, password: string): Promise<void> {
    message.value = "";
    const result = await authApi.register({ username, password });
    applyToken(result.token);
    applyPlayer(result.player);
    await refreshBootstrap().catch(() => undefined);
  }

  async function logout(): Promise<void> {
    if (token.value) {
      try {
        await authApi.logout();
      } catch {
        /* ignore logout errors */
      }
    }
    clearSession();
  }

  return {
    token,
    player,
    bootstrap,
    loading,
    message,
    isAuthenticated,
    clientId,
    refreshBootstrap,
    login,
    register,
    logout,
    clearSession,
  };
});
