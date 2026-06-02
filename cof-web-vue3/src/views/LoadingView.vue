<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import RoomChat from "@/components/RoomChat.vue";
import { getRoomAssets } from "@/api/assets";
import * as roomsApi from "@/api/rooms";
import { unlockGameAudio } from "@/composables/useGameAudio";
import { useAuthStore } from "@/stores/authStore";
import { useGameStore } from "@/stores/gameStore";
import { useRoomStore } from "@/stores/roomStore";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const gameStore = useGameStore();
const roomStore = useRoomStore();

const roomId = computed(() => String(route.params.roomId || roomStore.activeRoomId || ""));
const gameId = computed(() => String(route.query.gameId || gameStore.currentGame?.id || ""));
const game = computed(() => gameStore.currentGame);
const progress = ref(0);
const errorMessage = ref("");
const finished = ref(false);
const chatMessages = computed(() => roomStore.currentRoom?.chatMessages ?? []);

let lastReportedLoaded = 0;
let lastReportedAt = 0;

const loadingPlayers = computed(() => {
  const selfId = auth.clientId;
  return (game.value?.players ?? []).map((player) => {
    const isSelf = player.clientId === selfId;
    const reported = Math.max(0, Math.min(100, player.loadingProgress ?? 0));
    const pct = isSelf ? Math.max(reported, progress.value) : reported;
    return {
      id: player.clientId,
      name: player.username || player.clientId,
      isSelf,
      ready: Boolean(player.ready || pct >= 100),
      cached: Boolean(player.loadingCached),
      loaded: player.loadingLoaded ?? 0,
      total: player.loadingTotal ?? 0,
      pct,
    };
  });
});

const allReady = computed(() =>
  Boolean(loadingPlayers.value.length && loadingPlayers.value.every((player) => player.ready)),
);

async function maybeReportProgress(loaded: number, total: number, done = false, cached = false): Promise<void> {
  if (!roomId.value) return;
  const now = Date.now();
  const step = Math.max(1, Math.ceil(total / 20));
  if (!done && loaded - lastReportedLoaded < step && now - lastReportedAt < 500) {
    return;
  }
  lastReportedLoaded = loaded;
  lastReportedAt = now;
  const result = await roomsApi.reportLoadingProgress(roomId.value, { loaded, total, done, cached });
  roomStore.applyRoomUpdate(result.room);
  gameStore.currentGame = result.game;
}

async function preloadAssets(urls: string[], cached: boolean): Promise<void> {
  const total = Math.max(urls.length, 1);
  if (cached) {
    progress.value = 100;
    await maybeReportProgress(total, total, true, true);
    return;
  }

  let loaded = 0;
  for (const url of urls) {
    await new Promise<void>((resolve) => {
      const img = new Image();
      img.onload = () => resolve();
      img.onerror = () => resolve();
      img.src = url;
    });
    loaded += 1;
    progress.value = Math.max(progress.value, Math.round((loaded / total) * 100));
    await maybeReportProgress(loaded, total, loaded >= total);
  }
}

function goToGame(): void {
  if (finished.value || !roomId.value) return;
  finished.value = true;
  const gid = gameStore.currentGame?.id || gameId.value;
  void router.replace({
    name: "game",
    params: { roomId: roomId.value },
    query: gid ? { gameId: gid } : undefined,
  });
}

function onFirstInteraction(): void {
  void unlockGameAudio();
}

watch(
  () => [roomStore.currentRoom?.status, game.value?.status, allReady.value] as const,
  ([roomStatus, gameStatus]) => {
    if (roomStatus === "playing" || gameStatus === "playing") {
      goToGame();
    }
  },
);

onMounted(async () => {
  window.addEventListener("pointerdown", onFirstInteraction, { once: true });
  window.addEventListener("keydown", onFirstInteraction, { once: true });
  if (!roomId.value) return;
  if (auth.token) {
    gameStore.requestLoad(roomId.value, gameId.value || undefined);
  }
  if (gameId.value) {
    try {
      await gameStore.loadGame(gameId.value);
    } catch {
      /* ignore */
    }
  }
  try {
    const manifest = await getRoomAssets(roomId.value);
    const urls = manifest.assets || [];
    const cacheKey = `cof.assets.${manifest.key}`;
    const cached = localStorage.getItem(cacheKey) === "1";
    if (urls.length > 0) {
      await preloadAssets(urls, cached);
      localStorage.setItem(cacheKey, "1");
    } else {
      progress.value = 100;
      await maybeReportProgress(1, 1, true, cached);
    }
    if (roomStore.currentRoom?.status === "playing" || gameStore.currentGame?.status === "playing") {
      goToGame();
    } else {
      errorMessage.value = "仍在等待其他玩家完成加载...";
    }
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : "加载失败，请确认后端已重启到最新版本。";
  }
});

onUnmounted(() => {
  window.removeEventListener("pointerdown", onFirstInteraction);
  window.removeEventListener("keydown", onFirstInteraction);
});
</script>

<template>
  <AppShell>
    <PagePanel title="加载卡牌">
      <p class="muted">正在加载本机资源...{{ progress }}%</p>
      <div class="loading-bar">
        <div class="loading-fill" :style="{ width: `${progress}%` }" />
      </div>

      <div v-if="loadingPlayers.length" class="loading-list">
        <div v-for="player in loadingPlayers" :key="player.id" class="loading-player-row">
          <div class="loading-player-head">
            <strong>{{ player.name }}<span v-if="player.isSelf" class="muted">（你）</span></strong>
            <span class="loading-detail">
              {{ player.ready ? "完成" : `${player.pct}%` }}
              <template v-if="player.cached"> · 缓存</template>
            </span>
          </div>
          <div class="loading-bar">
            <div class="loading-fill" :style="{ width: `${player.pct}%` }" />
          </div>
          <div class="loading-detail">{{ player.loaded }}/{{ player.total || "?" }}</div>
        </div>
      </div>

      <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
      <RoomChat
        v-if="roomId"
        :room-id="roomId"
        :messages="chatMessages"
        @sent="roomStore.refreshRoom(roomId)"
      />
    </PagePanel>
  </AppShell>
</template>
