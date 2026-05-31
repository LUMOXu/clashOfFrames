<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { fetchCardViewer } from "@/api/meta";
import { useLobbyStore } from "@/stores/lobbyStore";

const route = useRoute();
const router = useRouter();
const lobby = useLobbyStore();
const progress = ref(0);

onMounted(async () => {
  const ids = String(route.query.libraries || "")
    .split(",")
    .filter(Boolean);
  lobby.setCardSelection(ids);
  try {
    const payload = await fetchCardViewer(ids);
    lobby.setCardViewerPayload(payload);
    const cacheKey = `cof.cardViewer.${payload.key}`;
    if (localStorage.getItem(cacheKey) === "1") {
      progress.value = 100;
    } else {
      const assets = payload.assets || [];
      let loaded = 0;
      for (const url of assets) {
        await new Promise<void>((resolve) => {
          const img = new Image();
          img.onload = () => resolve();
          img.onerror = () => resolve();
          img.src = url;
        });
        loaded += 1;
        progress.value = Math.round((loaded / Math.max(assets.length, 1)) * 100);
      }
      localStorage.setItem(cacheKey, "1");
    }
  } finally {
    await router.push({ name: "card-info" });
  }
});
</script>

<template>
  <AppShell>
    <PagePanel title="加载卡牌">
      <p class="muted">正在加载图鉴资源… {{ progress }}%</p>
      <div class="loading-bar">
        <div class="loading-fill" :style="{ width: `${progress}%` }" />
      </div>
    </PagePanel>
  </AppShell>
</template>
