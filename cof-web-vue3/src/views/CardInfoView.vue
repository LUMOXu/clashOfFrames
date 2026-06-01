<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { RouterLink, useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useLobbyStore } from "@/stores/lobbyStore";
import type { CardViewerLibrary, CardViewerPmv } from "@/types/api";
import { recordField } from "@/utils/record";

const lobby = useLobbyStore();
const router = useRouter();
const activeLibraryId = ref("");

onMounted(() => {
  if (!lobby.cardViewerPayload?.libraries?.length) {
    const ids = lobby.selectedLibraryIds;
    void router.replace({
      name: "card-loading",
      query: ids.length ? { libraries: ids.join(",") } : undefined,
    });
  }
});

const libraries = computed(() => lobby.cardViewerPayload?.libraries ?? []);

const activeLibrary = computed<CardViewerLibrary | null>(() => {
  if (!libraries.value.length) return null;
  const id = activeLibraryId.value || libraries.value[0]?.id;
  return libraries.value.find((lib) => lib.id === id) ?? libraries.value[0] ?? null;
});

watch(
  libraries,
  (items) => {
    if (!items.length) {
      activeLibraryId.value = "";
      return;
    }
    if (!items.some((lib) => lib.id === activeLibraryId.value)) {
      activeLibraryId.value = items[0].id;
    }
  },
  { immediate: true },
);

function pmvShots(pmv: CardViewerPmv) {
  return pmv.shots?.length ? pmv.shots : pmv.cards ?? [];
}

function pmvTitle(pmv: CardViewerPmv): string {
  return pmv.name || pmv.pmvName || `PMV ${pmv.pmvId ?? ""}`;
}
</script>

<template>
  <AppShell>
    <PagePanel title="牌组图鉴">
      <p class="actions-inline">
        <RouterLink class="action-link" to="/cards/submit">提交新牌组</RouterLink>
        <RouterLink class="action-link" to="/pmv-index">PMV 索引</RouterLink>
      </p>
      <div v-if="!libraries.length" class="muted">没有可展示的卡组。</div>
      <template v-else-if="activeLibrary">
        <div class="tabs">
          <button
            v-for="lib in libraries"
            :key="lib.id"
            type="button"
            :class="{ primary: lib.id === activeLibrary.id }"
            @click="activeLibraryId = lib.id"
          >
            {{ lib.name }}
          </button>
        </div>

        <div class="deck-info">
          <img v-if="activeLibrary.backUrl" :src="activeLibrary.backUrl" alt="" />
          <div>
            <h3>{{ activeLibrary.name }}</h3>
            <p class="status-line">
              整理者：{{ recordField(activeLibrary, "curator", "未填写") }} ·
              {{ recordField(activeLibrary, "cardCount", 0) }} 张 ·
              {{ recordField(activeLibrary, "pmvCount", 0) }} PMV
              <template v-if="activeLibrary.version"> · 版本 {{ activeLibrary.version }}</template>
            </p>
            <p class="muted">{{ activeLibrary.description || "暂无说明" }}</p>
            <p v-if="activeLibrary.link">
              <a :href="activeLibrary.link" target="_blank" rel="noopener noreferrer">卡组链接</a>
            </p>
            <p class="muted">文件夹：{{ activeLibrary.folderName || activeLibrary.id }}</p>
          </div>
        </div>

        <div class="pmv-card-list">
          <article v-for="pmv in activeLibrary.pmvs || []" :key="String(pmv.pmvId)" class="pmv-card">
            <h3>pmv {{ pmv.pmvId }} · {{ pmvTitle(pmv) }}</h3>
            <div class="pmv-shots">
              <img
                v-for="shot in pmvShots(pmv)"
                :key="shot.id"
                :src="shot.imageUrl"
                :alt="`${pmvTitle(pmv)} ${shot.shot || ''}`"
              />
            </div>
            <p v-if="pmv.author">作者：{{ pmv.author }}</p>
            <p v-if="pmv.description">{{ pmv.description }}</p>
            <p v-if="pmv.link">
              <a :href="pmv.link" target="_blank" rel="noopener noreferrer">链接</a>
            </p>
          </article>
        </div>

        <div class="actions">
          <RouterLink class="action-link" :to="{ name: 'card-select' }">
            <button type="button">重新选择</button>
          </RouterLink>
          <RouterLink class="action-link" to="/">
            <button type="button">返回</button>
          </RouterLink>
        </div>
      </template>
    </PagePanel>
  </AppShell>
</template>
