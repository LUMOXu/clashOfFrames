<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { fetchCardViewer } from "@/api/meta";
import { useLobbyStore } from "@/stores/lobbyStore";

const route = useRoute();
const router = useRouter();
const lobby = useLobbyStore();
const progress = ref(0);
const loaded = ref(0);
const total = ref(0);
const errorMessage = ref("");

async function preloadAssets(urls: string[]): Promise<void> {
  total.value = urls.length;
  loaded.value = 0;
  progress.value = 0;
  for (const url of urls) {
    await new Promise<void>((resolve) => {
      const img = new Image();
      img.onload = () => resolve();
      img.onerror = () => resolve();
      img.src = url;
    });
    loaded.value += 1;
    progress.value = Math.round((loaded.value / Math.max(urls.length, 1)) * 100);
  }
}

onMounted(async () => {
  const ids = String(route.query.libraries || "")
    .split(",")
    .filter(Boolean);
  if (!ids.length) {
    errorMessage.value = "请先选择至少一个卡组。";
    return;
  }
  lobby.setCardSelection(ids);
  try {
    const payload = await fetchCardViewer(ids);
    lobby.setCardViewerPayload(payload);
    const cacheKey = `cof.cardViewer.${payload.key}`;
    const assets = payload.assets || [];
    if (localStorage.getItem(cacheKey) === "1") {
      loaded.value = assets.length;
      total.value = assets.length;
      progress.value = 100;
    } else {
      await preloadAssets(assets);
      localStorage.setItem(cacheKey, "1");
    }
    await router.replace({ name: "card-info" });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载卡牌资源失败";
  }
});
</script>

<template>
  <AppShell>
    <PagePanel title="查看卡牌加载">
      <template v-if="errorMessage">
        <p class="error-text">{{ errorMessage }}</p>
        <div class="actions">
          <RouterLink class="action-link" :to="{ name: 'card-select' }">
            <button type="button">返回选择</button>
          </RouterLink>
        </div>
      </template>
      <template v-else>
        <p class="status-line">{{ loaded }}/{{ total || "…" }} 个资源 · {{ progress }}%</p>
        <div class="loading-bar">
          <div class="loading-fill" :style="{ width: `${progress}%` }" />
        </div>
      </template>
    </PagePanel>
  </AppShell>
</template>
