<script setup lang="ts">
import { computed, ref } from "vue";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useLobbyStore } from "@/stores/lobbyStore";

const lobby = useLobbyStore();
const activeTab = ref(0);

const libraries = computed(() => lobby.cardViewerPayload?.libraries ?? []);
const activeLibrary = computed(() => libraries.value[activeTab.value]);
</script>

<template>
  <AppShell>
    <PagePanel title="卡牌浏览">
      <div v-if="!libraries.length" class="muted">未加载卡牌数据。</div>
      <template v-else>
        <div class="tab-row">
          <button
            v-for="(lib, i) in libraries"
            :key="lib.id"
            type="button"
            :class="{ active: activeTab === i }"
            @click="activeTab = i"
          >
            {{ lib.name }}
          </button>
        </div>
        <p v-if="activeLibrary?.backUrl" class="muted">牌背</p>
        <img v-if="activeLibrary?.backUrl" class="card-back-preview" :src="activeLibrary.backUrl" alt="" />
        <div v-for="pmv in activeLibrary?.pmvs || []" :key="pmv.pmvId" class="pmv-block">
          <h3>{{ pmv.pmvName || pmv.pmvId }}</h3>
          <div class="pmv-card-list">
            <img v-for="card in pmv.cards || []" :key="card.id" :src="card.imageUrl" :alt="card.shot" />
          </div>
        </div>
      </template>
    </PagePanel>
  </AppShell>
</template>
