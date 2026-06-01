<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { RouterLink, useRoute } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import GameAlert from "@/components/game/GameAlert.vue";
import GameAnimation from "@/components/game/GameAnimation.vue";
import GameTable from "@/components/game/GameTable.vue";
import PagePanel from "@/components/PagePanel.vue";
import { fetchMatchReplay } from "@/api/profile";
import { REPLAY_SPEED_OPTIONS, useMatchReplayPlayer } from "@/composables/useMatchReplayPlayer";
import { useTurnBanner } from "@/composables/useTurnBanner";
import { useAuthStore } from "@/stores/authStore";
import type { GameReplayTimeline } from "@/types/replay";
import { formatDate } from "@/utils/format";
const route = useRoute();
const auth = useAuthStore();
const loading = ref(true);
const loadError = ref("");
const meta = ref<{ gameId?: string; roomId?: string; playedAt?: number }>({});
const logText = ref("");
const timelineRef = ref<GameReplayTimeline | null>(null);

const gameId = computed(() => String(route.params.gameId || ""));

const player = useMatchReplayPlayer(timelineRef);
const {
  speed,
  playing,
  replayMs,
  maxMs,
  displayGame,
  replayClock,
  locked,
  clockLabel,
  durationLabel,
  seek,
  step,
  togglePlay,
} = player;

const viewerId = computed(
  () => timelineRef.value?.defaultViewerId || auth.clientId || displayGame.value?.players?.[0]?.clientId || "",
);

const current = computed(() => {
  const g = displayGame.value;
  if (!g?.players?.length) return undefined;
  return g.players[g.turnIndex ?? 0];
});

const { turnTitle, turnDetail } = useTurnBanner(displayGame, viewerId, locked);

const hasVisual = computed(() => (timelineRef.value?.frames?.length ?? 0) > 0);

const sliderMax = computed(() => Math.max(maxMs.value, 1));

onMounted(async () => {
  loadError.value = "";
  timelineRef.value = null;
  logText.value = "";
  loading.value = true;
  try {
    if (!auth.clientId) throw new Error("未登录");
    const id = gameId.value;
    if (!id) throw new Error("缺少对局 ID");
    const data = await fetchMatchReplay(auth.clientId, id);
    meta.value = data.replay;
    logText.value = data.replay.logText || "";
    if (data.replay.replayJson) {
      timelineRef.value = JSON.parse(data.replay.replayJson) as GameReplayTimeline;
    }
    if (!timelineRef.value?.frames?.length && !logText.value) {
      throw new Error("该对局没有可播放的回放数据");
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : "加载回放失败";
  } finally {
    loading.value = false;
  }
});

watch(speed, () => {
  if (playing.value) {
    player.pause();
    player.play();
  }
});
</script>

<template>
  <AppShell immersive>
    <main class="page game-page replay-page">
      <PagePanel v-if="loading" title="对局回放">
        <p class="muted">加载中…</p>
      </PagePanel>
      <PagePanel v-else-if="loadError" title="对局回放">
        <p class="error-text">{{ loadError }}</p>
        <div class="actions">
          <RouterLink class="action-link" to="/profile">
            <button type="button">返回个人主页</button>
          </RouterLink>
        </div>
      </PagePanel>
      <template v-else>
        <header class="replay-header">
          <h1 class="replay-title">对局回放</h1>
          <p class="status-line muted">
            {{ meta.gameId ?? gameId }}
            <span v-if="meta.roomId"> · 房间 {{ meta.roomId }}</span>
            <span v-if="meta.playedAt"> · {{ formatDate(meta.playedAt) }}</span>
          </p>
        </header>

        <section v-if="hasVisual" class="replay-stage">
          <GameTable
            v-if="displayGame?.players?.length"
            :players="displayGame.players"
            :self-id="viewerId"
            :current-id="current?.clientId"
            :can-play="false"
            :can-ring="false"
            :last-animation="locked ? displayGame.lastAnimation : undefined"
            :clock-now="replayClock"
          >
            <template #alert>
              <GameAlert :match="displayGame.lastMatch" />
            </template>
            <template v-if="locked" #turn-banner="{ player, isCurrent }">
              <div v-if="isCurrent && displayGame?.status === 'playing'" class="turn-banner">
                <strong>{{ turnTitle(player, isCurrent) }}</strong>
                <span v-if="turnDetail(player, isCurrent)" class="turn-detail">
                  {{ turnDetail(player, isCurrent) }}
                </span>
              </div>
            </template>
            <template v-if="locked" #animation>
              <GameAnimation
                :animation="displayGame.lastAnimation"
                :players="displayGame.players"
                :self-id="viewerId"
                :clock-now="replayClock"
              />
            </template>
          </GameTable>
          <p v-else class="center-message muted">准备回放…</p>

          <div class="replay-controls">
            <div class="replay-transport">
              <button type="button" class="replay-btn" title="上一帧" @click="step(-1)">⏮</button>
              <button type="button" class="replay-btn replay-btn-primary" @click="togglePlay">
                {{ playing ? "暂停" : "播放" }}
              </button>
              <button type="button" class="replay-btn" title="下一帧" @click="step(1)">⏭</button>
            </div>
            <div class="replay-time">{{ clockLabel }} / {{ durationLabel }}</div>
            <input
              class="replay-slider"
              type="range"
              min="0"
              :max="sliderMax"
              step="50"
              :value="replayMs"
              @input="seek(Number(($event.target as HTMLInputElement).value))"
            />
            <label class="replay-speed">
              倍速
              <select v-model.number="speed">
                <option v-for="opt in REPLAY_SPEED_OPTIONS" :key="opt" :value="opt">{{ opt }}×</option>
              </select>
            </label>
          </div>
        </section>

        <section v-if="logText" class="replay-log-section">
          <h3 class="section-title">文字日志</h3>
          <pre class="replay-log">{{ logText }}</pre>
        </section>
        <p v-else-if="!hasVisual" class="muted">暂无回放内容。</p>

        <div class="actions">
          <RouterLink class="action-link" to="/profile">
            <button type="button">返回个人主页</button>
          </RouterLink>
        </div>
      </template>
    </main>
  </AppShell>
</template>

<style scoped>
.replay-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 1200px;
  margin: 0 auto;
  padding: 1rem;
}
.replay-header {
  text-align: center;
}
.replay-title {
  margin: 0 0 0.25rem;
  font-size: 1.35rem;
}
.replay-stage {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.replay-controls {
  display: grid;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.25);
}
.replay-transport {
  display: flex;
  justify-content: center;
  gap: 0.5rem;
}
.replay-btn {
  min-width: 2.5rem;
  padding: 0.35rem 0.75rem;
}
.replay-btn-primary {
  min-width: 5rem;
}
.replay-time {
  text-align: center;
  font-variant-numeric: tabular-nums;
  font-size: 0.95rem;
}
.replay-slider {
  width: 100%;
}
.replay-speed {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  font-size: 0.9rem;
}
.replay-log-section {
  margin-top: 0.5rem;
}
.replay-log {
  max-height: 240px;
  overflow: auto;
  margin: 0;
  padding: 0.75rem 1rem;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.3);
  font-size: 0.8rem;
  white-space: pre-wrap;
}
</style>
