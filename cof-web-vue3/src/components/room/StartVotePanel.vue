<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import type { RoomSummary } from "@/types/api";
import { isComputerClientId } from "@/utils/computerPlayer";

const props = defineProps<{
  room: RoomSummary;
  selfId: string;
}>();

const emit = defineEmits<{
  vote: [];
  cancel: [];
}>();

const now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | undefined;

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now();
  }, 500);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});

const humanPlayerIds = computed(() =>
  (props.room.players ?? []).filter((id) => !isComputerClientId(id)),
);

const humanVoteCount = computed(() => {
  const votes = new Set(props.room.startVotes ?? []);
  return humanPlayerIds.value.filter((id) => votes.has(id)).length;
});

const voted = computed(() => props.room.startVotes?.includes(props.selfId) ?? false);

const voteRequired = computed(() => {
  const count = humanPlayerIds.value.length;
  const settings = props.room.settings;
  if (settings?.startVoteThresholdMode === "manual" && settings.startVoteThreshold) {
    return Math.max(1, Math.min(count || 1, settings.startVoteThreshold));
  }
  return Math.max(1, count - 2);
});

const countdownSec = computed(() => {
  if (!props.room.startAt) return null;
  return Math.max(0, Math.ceil((props.room.startAt - now.value) / 1000));
});
</script>

<template>
  <section class="panel vote-panel">
    <h3>投票开始</h3>
    <p class="status-line">
      {{ humanVoteCount }}/{{ voteRequired }} 票（仅统计真人），房间 {{ humanPlayerIds.length }} 真人 /
      {{ (room.players?.length ?? 0) - humanPlayerIds.length }} 人机。
    </p>
    <p v-if="countdownSec !== null" class="status-line">倒计时 {{ countdownSec }} 秒后自动开始。</p>
    <div class="actions">
      <button type="button" class="primary" :disabled="voted" @click="emit('vote')">投票开始</button>
      <button type="button" :disabled="!voted" @click="emit('cancel')">取消投票</button>
    </div>
  </section>
</template>
