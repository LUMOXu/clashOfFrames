<script setup lang="ts">
import { onUnmounted, ref, watch } from "vue";
import type { PublicMatch } from "@/types/api";

const props = defineProps<{
  match?: PublicMatch | null;
}>();

const visible = ref(false);
let hideTimer: ReturnType<typeof setTimeout> | undefined;

watch(
  () => props.match,
  (match) => {
    if (hideTimer) clearTimeout(hideTimer);
    if (!match?.at) {
      visible.value = false;
      return;
    }
    visible.value = Date.now() - match.at < 5200;
    if (visible.value) {
      hideTimer = setTimeout(() => {
        visible.value = false;
      }, Math.max(0, 5200 - (Date.now() - match.at)));
    }
  },
  { immediate: true, deep: true },
);

onUnmounted(() => {
  if (hideTimer) clearTimeout(hideTimer);
});

function cardImage(entry: { card?: { imageUrl?: string }; imageUrl?: string }): string {
  return entry.card?.imageUrl || entry.imageUrl || "";
}
</script>

<template>
  <div v-if="visible && match" class="game-alert" :class="{ ok: match.type === 'success', fail: match.type === 'fail' }">
    <template v-if="match.type === 'fail'">
      <strong>错误按铃</strong>
      <div>{{ match.username }} 交出 {{ match.given ?? 0 }} 张牌</div>
    </template>
    <template v-else>
      <strong>玩家 {{ match.username }} 匹配成功！</strong>
      <div>{{ match.pmvName }}</div>
      <div v-if="match.cards?.length" class="match-preview">
        <img v-for="(entry, i) in match.cards" :key="i" :src="cardImage(entry)" alt="" />
      </div>
    </template>
  </div>
</template>
