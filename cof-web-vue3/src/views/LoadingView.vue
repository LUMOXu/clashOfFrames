<script setup lang="ts">
import { onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useGameStore } from "@/stores/gameStore";

const route = useRoute();
const router = useRouter();
const gameStore = useGameStore();

onMounted(() => {
  gameStore.setLoadingProgress(0, 1, false);
  const timer = window.setTimeout(() => {
    gameStore.setLoadingProgress(1, 1, true);
    void router.push({
      name: "game",
      params: { roomId: String(route.params.roomId || "") },
      query: { gameId: route.query.gameId },
    });
  }, 800);
  return () => window.clearTimeout(timer);
});
</script>

<template>
  <AppShell>
    <PagePanel title="资源加载">
      <p class="muted">正在加载对局资源...</p>
      <div class="loading-bar">
        <div
          class="loading-fill"
          :style="{
            width: `${gameStore.loadingProgress.total ? (100 * gameStore.loadingProgress.loaded) / gameStore.loadingProgress.total : 0}%`,
          }"
        />
      </div>
      <p class="loading-detail">
        {{ gameStore.loadingProgress.loaded }} / {{ gameStore.loadingProgress.total }}
      </p>
    </PagePanel>
  </AppShell>
</template>
