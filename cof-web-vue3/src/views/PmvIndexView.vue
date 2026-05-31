<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useLobbyStore } from "@/stores/lobbyStore";
import { recordField } from "@/utils/record";

const lobby = useLobbyStore();
const search = ref("");

onMounted(() => {
  void lobby.loadPmvIndex();
});

const filteredRows = computed(() => {
  const q = search.value.trim().toLowerCase();
  if (!q) return lobby.pmvIndex;
  return lobby.pmvIndex.filter((row) =>
    JSON.stringify(row).toLowerCase().includes(q),
  );
});
</script>

<template>
  <AppShell>
    <PagePanel title="PMV 索引">
      <label class="pmv-search-row">搜索
        <input v-model="search" placeholder="PMV ID 或关键词" />
      </label>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>PMV ID</th>
              <th>信息</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in filteredRows" :key="index">
              <td>{{ recordField(row, "pmvId", index) }}</td>
              <td>{{ JSON.stringify(row) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="!filteredRows.length" class="muted">暂无索引条目。</p>
      </div>
    </PagePanel>
  </AppShell>
</template>
