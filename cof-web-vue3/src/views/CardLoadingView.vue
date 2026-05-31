<script setup lang="ts">
import { onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useLobbyStore } from "@/stores/lobbyStore";

const route = useRoute();
const router = useRouter();
const lobby = useLobbyStore();

onMounted(() => {
  const ids = String(route.query.libraries || "").split(",").filter(Boolean);
  lobby.setCardSelection(ids);
  lobby.setCardViewerPayload({ libraries: ids, cards: [] });
  window.setTimeout(() => {
    void router.push({ name: "card-info" });
  }, 500);
});
</script>

<template>
  <AppShell>
    <PagePanel title="加载卡牌">
      <p class="muted">正在加载图鉴资源...</p>
      <div class="loading-bar"><div class="loading-fill" style="width: 60%" /></div>
    </PagePanel>
  </AppShell>
</template>
