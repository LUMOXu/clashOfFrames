<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import type { PublicGame } from "@/types/api";

const props = defineProps<{
  game: PublicGame;
  selfId: string;
}>();

const emit = defineEmits<{
  continue: [];
}>();

const now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | undefined;

const voted = computed(() => props.game.continueVotes?.includes(props.selfId));

const countdownSec = computed(() => {
  if (!props.game.continueReturnAt) return null;
  return Math.max(0, Math.ceil((props.game.continueReturnAt - now.value) / 1000));
});

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now();
  }, 250);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});

const autoContinueSent = ref(false);

watch(
  () => props.game.id,
  () => {
    autoContinueSent.value = false;
  },
);

/** 倒计时结束后若仍停留在结算页，再请求一次 continue（与后端 tick 互补） */
watch(countdownSec, (sec) => {
  if (autoContinueSent.value || !voted.value || !props.game.continueReturnAt) return;
  if (sec !== null && sec <= 0) {
    autoContinueSent.value = true;
    emit("continue");
  }
});
</script>

<template>
  <section class="result-panel modal result-modal">
    <h2>对局结束</h2>
    <p v-if="game.winnerId">
      胜者：{{ game.players?.find((p) => p.clientId === game.winnerId)?.username }}
    </p>
    <p v-if="countdownSec !== null" class="muted">返回等待室倒计时 {{ countdownSec }}s</p>
    <button type="button" class="primary" :disabled="voted" @click="emit('continue')">
      {{ voted ? "已投票继续" : "继续" }}
    </button>
  </section>
</template>
