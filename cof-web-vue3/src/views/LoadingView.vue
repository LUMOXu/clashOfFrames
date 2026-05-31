<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
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
const progress = ref(0);
const errorMessage = ref("");
const finished = ref(false);
const chatMessages = computed(() => roomStore.currentRoom?.chatMessages ?? []);

async function preloadAssets(urls: string[]): Promise<void> {
  let loaded = 0;
  const total = Math.max(urls.length, 1);
  for (const url of urls) {
    await new Promise<void>((resolve) => {
      const img = new Image();
      img.onload = () => resolve();
      img.onerror = () => resolve();
      img.src = url;
    });
    loaded += 1;
    progress.value = Math.round((loaded / total) * 100);
  }
}

async function reportDone(total: number, cached: boolean): Promise<void> {
  if (!roomId.value) return;
  const result = await roomsApi.reportLoadingProgress(roomId.value, {
    loaded: total,
    total,
    done: true,
    cached,
  });
  roomStore.applyRoomUpdate(result.room);
  gameStore.currentGame = result.game;
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
    if (!cached && urls.length > 0) {
      await preloadAssets(urls);
      localStorage.setItem(cacheKey, "1");
    } else {
      progress.value = 100;
    }
    await reportDone(urls.length || 1, cached);
    if (
      roomStore.currentRoom?.status === "playing" ||
      gameStore.currentGame?.status === "playing"
    ) {
      goToGame();
    } else {
      errorMessage.value = "仍在等待其他玩家完成加载…";
      window.setTimeout(() => {
        if (roomStore.currentRoom?.status === "playing") {
          goToGame();
        }
      }, 1500);
    }
  } catch (e) {
    errorMessage.value = e instanceof Error ? e.message : "加载失败，请确认后端已重启到最新版本。";
  }
});
</script>

<template>
  <AppShell>
    <PagePanel title="加载卡牌">
      <p class="muted">正在加载对局资源… {{ progress }}%</p>
      <div class="loading-bar">
        <div class="loading-fill" :style="{ width: `${progress}%` }" />
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
