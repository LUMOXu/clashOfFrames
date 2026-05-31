<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useRoomStore } from "@/stores/roomStore";

const route = useRoute();
const roomStore = useRoomStore();
const roomId = computed(() => String(route.params.roomId || roomStore.activeRoomId || ""));
</script>

<template>
  <AppShell>
    <PagePanel title="房间设置">
      <p class="muted">房间 {{ roomId || "无" }} 的设置面板（壳）。</p>
      <div class="form-grid">
        <label>最小玩家数<input type="number" min="2" value="2" /></label>
        <label>最大玩家数<input type="number" min="2" value="8" /></label>
        <label class="toggle-row"><span>公开房间</span><input type="checkbox" /></label>
      </div>
      <div class="actions">
        <button class="primary" type="button">保存设置</button>
      </div>
    </PagePanel>
  </AppShell>
</template>
