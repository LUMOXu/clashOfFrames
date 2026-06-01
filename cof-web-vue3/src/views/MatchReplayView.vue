<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { fetchMatchReplay } from "@/api/profile";
import { useAuthStore } from "@/stores/authStore";
import { formatDate } from "@/utils/format";

const route = useRoute();
const auth = useAuthStore();
const loading = ref(true);
const loadError = ref("");
const logText = ref("");
const meta = ref<{ gameId?: string; roomId?: string; playedAt?: number }>({});

const gameId = computed(() => String(route.params.gameId || ""));

onMounted(async () => {
  loadError.value = "";
  logText.value = "";
  loading.value = true;
  try {
    if (!auth.clientId) {
      throw new Error("未登录");
    }
    const id = gameId.value;
    if (!id) {
      throw new Error("缺少对局 ID");
    }
    const data = await fetchMatchReplay(auth.clientId, id);
    meta.value = data.replay;
    logText.value = data.replay.logText || "";
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : "加载回放失败";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <AppShell>
    <PagePanel title="对局回放">
      <p v-if="loading" class="muted">加载中…</p>
      <p v-else-if="loadError" class="error-text">{{ loadError }}</p>
      <template v-else>
        <p class="status-line">
          对局 {{ meta.gameId ?? gameId }}
          <span v-if="meta.roomId"> · 房间 {{ meta.roomId }}</span>
          <span v-if="meta.playedAt"> · {{ formatDate(meta.playedAt) }}</span>
        </p>
        <p class="muted replay-hint">时间轴为开局起增量计时（mm:ss:SSS）。</p>
        <pre class="replay-log">{{ logText || "（无日志内容）" }}</pre>
      </template>
      <div class="actions">
        <RouterLink class="action-link" to="/profile">
          <button type="button">返回个人主页</button>
        </RouterLink>
      </div>
    </PagePanel>
  </AppShell>
</template>

<style scoped>
.replay-hint {
  margin: 0.5rem 0 1rem;
  font-size: 0.9rem;
}
.replay-log {
  max-height: min(70vh, 640px);
  overflow: auto;
  margin: 0;
  padding: 1rem;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.35);
  font-size: 0.85rem;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
