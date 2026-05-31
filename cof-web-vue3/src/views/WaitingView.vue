<script setup lang="ts">
import { computed, onMounted } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useRoomStore } from "@/stores/roomStore";
import { useGameStore } from "@/stores/gameStore";

const route = useRoute();
const router = useRouter();
const roomStore = useRoomStore();
const gameStore = useGameStore();

const roomId = computed(() => String(route.params.roomId || roomStore.activeRoomId || ""));

onMounted(() => {
  if (route.params.roomId) {
    roomStore.activeRoomId = String(route.params.roomId);
  }
});

async function start(): Promise<void> {
  if (!roomId.value) return;
  const game = await gameStore.startGame(roomId.value);
  roomStore.setCurrentRoom({ id: roomId.value, status: "loading" });
  await router.push({ name: "loading", params: { roomId: roomId.value }, query: { gameId: game.id } });
}
</script>

<template>
  <AppShell>
    <PagePanel title="等待室">
      <p class="status-line">房间：{{ roomId || "未选择" }}</p>
      <p class="muted">等待其他玩家加入并准备开局。</p>
      <div class="actions">
        <RouterLink :to="{ name: 'settings' }"><button type="button">房间设置</button></RouterLink>
        <button class="primary" type="button" @click="start">开始游戏</button>
      </div>
    </PagePanel>
  </AppShell>
</template>
