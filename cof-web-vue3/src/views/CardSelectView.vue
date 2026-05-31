<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useLobbyStore } from "@/stores/lobbyStore";
import { recordId, recordName } from "@/utils/record";

const lobby = useLobbyStore();
const router = useRouter();
const selected = ref<string[]>([]);

onMounted(() => {
  void lobby.loadMeta();
});

function openViewer(): void {
  lobby.setCardSelection(selected.value);
  void router.push({ name: "card-loading", query: { libraries: selected.value.join(",") } });
}
</script>

<template>
  <AppShell>
    <PagePanel title="卡牌图鉴">
      <div class="library-list">
        <label
          v-for="(lib, index) in lobby.cardLibraries"
          :key="recordId(lib, index)"
          class="library-row"
        >
          <span>{{ recordName(lib, `牌库 ${index + 1}`) }}</span>
          <input v-model="selected" type="checkbox" :value="recordId(lib, index)" />
        </label>
      </div>
      <div class="actions">
        <button class="primary" type="button" :disabled="!selected.length" @click="openViewer">查看选中牌库</button>
      </div>
    </PagePanel>
  </AppShell>
</template>
