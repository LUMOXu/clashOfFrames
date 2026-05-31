<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useLobbyStore } from "@/stores/lobbyStore";
import type { CardLibraryMeta } from "@/types/computer";
import { recordField, recordId, recordName } from "@/utils/record";

const lobby = useLobbyStore();
const router = useRouter();
const selected = ref<string[]>([]);

onMounted(async () => {
  await lobby.loadMeta();
  const libraries = lobby.cardLibraries as CardLibraryMeta[];
  if (!selected.value.length && libraries.length) {
    selected.value = [libraries[0].id];
  }
});

function openViewer(): void {
  if (!selected.value.length) return;
  lobby.setCardSelection(selected.value);
  void router.push({ name: "card-loading", query: { libraries: selected.value.join(",") } });
}
</script>

<template>
  <AppShell>
    <PagePanel title="卡组选择">
      <p v-if="lobby.loadingMeta" class="muted">加载卡组列表…</p>
      <form v-else class="grid" @submit.prevent="openViewer">
        <p v-if="!(lobby.cardLibraries as CardLibraryMeta[]).length" class="muted">暂无可用卡组。</p>
        <div v-else class="library-list">
          <label
            v-for="(lib, index) in lobby.cardLibraries as CardLibraryMeta[]"
            :key="recordId(lib, index)"
            class="library-row"
          >
            <span>
              <strong>{{ recordName(lib, `牌库 ${index + 1}`) }}</strong>
              <span class="pill">{{ recordField(lib, "cardCount", 0) }} 张 / {{ recordField(lib, "pmvCount", 0) }} PMV</span>
              <span class="muted">整理者：{{ recordField(lib, "curator", "未填写") }}</span>
            </span>
            <input v-model="selected" type="checkbox" :value="recordId(lib, index)" />
          </label>
        </div>
        <div class="actions">
          <button class="primary" type="submit" :disabled="!selected.length">查看</button>
          <RouterLink class="action-link" to="/">
            <button type="button">返回</button>
          </RouterLink>
        </div>
      </form>
    </PagePanel>
  </AppShell>
</template>
