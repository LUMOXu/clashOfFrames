<script setup lang="ts">
import { computed, onMounted } from "vue";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useAuthStore } from "@/stores/authStore";
import { useLobbyStore } from "@/stores/lobbyStore";

const auth = useAuthStore();
const lobby = useLobbyStore();

const profile = computed(() => lobby.profile as Record<string, unknown> | null);
const history = computed(() => {
  const raw = profile.value?.history;
  return Array.isArray(raw) ? raw : [];
});

onMounted(() => {
  if (auth.clientId) {
    void lobby.loadProfile(auth.clientId);
  }
});
</script>

<template>
  <AppShell>
    <PagePanel title="个人战绩">
      <p v-if="lobby.loadingProfile" class="muted">加载中...</p>
      <template v-else-if="profile">
        <div class="stats-grid">
          <div class="stat-card">对局 {{ profile.gamesPlayed ?? 0 }}</div>
          <div class="stat-card">胜场 {{ profile.wins ?? 0 }}</div>
          <div class="stat-card">按铃 {{ profile.rings ?? 0 }}</div>
          <div class="stat-card">正确按铃 {{ profile.correctRings ?? 0 }}</div>
        </div>
        <h3>历史对局</h3>
        <table v-if="history.length" class="data-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>房间</th>
              <th>名次</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in history" :key="i">
              <td>{{ (row as Record<string, unknown>).at }}</td>
              <td>{{ (row as Record<string, unknown>).roomId }}</td>
              <td>{{ (row as Record<string, unknown>).rank }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="muted">暂无历史记录。</p>
      </template>
      <p v-else class="muted">暂无战绩数据。</p>
    </PagePanel>
  </AppShell>
</template>
