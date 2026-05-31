<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import GameResultChart from "@/components/game/GameResultChart.vue";
import type { PublicGame } from "@/types/api";
import { canContinueAfterResultReplay, resultReplayProgress } from "@/utils/resultChart";
import { isGodComputer } from "@/utils/format";

const props = defineProps<{
  game: PublicGame;
  selfId: string;
}>();

const emit = defineEmits<{
  continue: [];
}>();

const now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | undefined;

const replayStartedAt = ref(0);

watch(
  () => props.game.id,
  () => {
    replayStartedAt.value = Date.now();
  },
  { immediate: true },
);

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now();
  }, 250);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});

const winner = computed(() => props.game.players?.find((p) => p.clientId === props.game.winnerId));

const averageTurn = computed(() => {
  const success = props.game.successBellCount ?? 0;
  const plays = props.game.playCount ?? 0;
  return success > 0 ? (plays / success).toFixed(2) : String(plays);
});

const continuablePlayers = computed(() =>
  (props.game.players || []).filter((p) => !p.isComputer && p.connected !== false && !p.exited),
);

const validVotes = computed(() => {
  const votes = props.game.continueVotes || [];
  const ids = new Set(continuablePlayers.value.map((p) => p.clientId));
  return votes.filter((id) => ids.has(id)).length;
});

const countdownSec = computed(() => {
  if (!props.game.continueReturnAt) return null;
  return Math.max(0, Math.ceil((props.game.continueReturnAt - now.value) / 1000));
});

const chartProgressX = computed(() =>
  resultReplayProgress(props.game, replayStartedAt.value, now.value),
);

const replayDone = computed(() =>
  canContinueAfterResultReplay(props.game, replayStartedAt.value, now.value),
);

const hasChart = computed(() => (props.game.resultInfo?.counts?.length ?? 0) > 0);

const voted = computed(() => props.game.continueVotes?.includes(props.selfId));

const autoContinueSent = ref(false);

watch(countdownSec, (sec) => {
  if (autoContinueSent.value || !voted.value || !props.game.continueReturnAt) return;
  if (sec !== null && sec <= 0 && replayDone.value) {
    autoContinueSent.value = true;
    emit("continue");
  }
});
</script>

<template>
  <section class="result-panel modal result-modal">
    <h2>
      祝贺
      <span v-if="winner" :class="{ 'god-name': isGodComputer(winner) }">{{ winner.username }}</span>
      <template v-else>无人</template>
      胜利
    </h2>
    <p>总出牌数：{{ game.playCount ?? 0 }}</p>
    <p>
      抢铃：{{ game.bellCount ?? 0 }} 次，成功 {{ game.successBellCount ?? 0 }}，失败
      {{ game.failBellCount ?? 0 }}
    </p>
    <p>平均回合长度：{{ averageTurn }}</p>

    <GameResultChart v-if="hasChart" :game="game" :progress-x="chartProgressX" />

    <p>继续确认：{{ validVotes }}/{{ continuablePlayers.length }} 人</p>
    <p v-if="countdownSec !== null" class="muted">返回等待区倒计时：{{ countdownSec }} 秒</p>

    <button
      type="button"
      class="primary"
      :disabled="!replayDone || voted"
      @click="emit('continue')"
    >
      {{ !replayDone ? "回放中…" : voted ? "已投票继续" : "继续" }}
    </button>
  </section>
</template>
