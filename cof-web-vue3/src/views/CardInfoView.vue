<script setup lang="ts">
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useLobbyStore } from "@/stores/lobbyStore";

const lobby = useLobbyStore();
</script>

<template>
  <AppShell>
    <PagePanel title="卡牌详情">
      <p class="muted">已选牌库：{{ lobby.selectedLibraryIds.join(", ") || "无" }}</p>
      <div class="pmv-card-list">
        <article class="pmv-card">
          <p>图鉴内容壳 — 接入 card-viewer API 后展示 PMV 卡牌与截图。</p>
        </article>
      </div>
    </PagePanel>
  </AppShell>
</template>
