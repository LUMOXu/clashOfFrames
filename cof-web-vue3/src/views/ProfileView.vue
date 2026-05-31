<script setup lang="ts">
import { onMounted } from "vue";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useAuthStore } from "@/stores/authStore";
import { useLobbyStore } from "@/stores/lobbyStore";

const auth = useAuthStore();
const lobby = useLobbyStore();

onMounted(() => {
  if (auth.player?.clientId) {
    void lobby.loadProfile(auth.player.clientId);
  }
});
</script>

<template>
  <AppShell>
    <PagePanel title="个人战绩">
      <p v-if="lobby.loadingProfile" class="muted">加载中...</p>
      <pre v-else-if="lobby.profile">{{ JSON.stringify(lobby.profile, null, 2) }}</pre>
      <p v-else class="muted">暂无战绩数据。</p>
    </PagePanel>
  </AppShell>
</template>
