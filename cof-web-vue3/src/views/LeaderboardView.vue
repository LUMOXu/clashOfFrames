<script setup lang="ts">
import { onMounted } from "vue";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useLobbyStore } from "@/stores/lobbyStore";
import { recordField } from "@/utils/record";

const lobby = useLobbyStore();

onMounted(() => {
  void lobby.loadLeaderboard();
});
</script>

<template>
  <AppShell>
    <PagePanel title="排行榜">
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>玩家</th>
              <th>胜场</th>
              <th>场次</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in lobby.leaderboard" :key="index">
              <td>{{ recordField(row, "username", "—") }}</td>
              <td>{{ recordField(row, "wins", "—") }}</td>
              <td>{{ recordField(row, "playCount", "—") }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="!lobby.leaderboard.length && !lobby.loadingLeaderboard" class="muted">暂无数据。</p>
      </div>
    </PagePanel>
  </AppShell>
</template>
