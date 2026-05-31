<script setup lang="ts">
import { computed, onMounted, onUnmounted } from "vue";
import { useRoute } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import GameTable from "@/components/game/GameTable.vue";
import { useAuthStore } from "@/stores/authStore";
import { useGameStore } from "@/stores/gameStore";

const route = useRoute();
const auth = useAuthStore();
const gameStore = useGameStore();

const gameId = computed(() => String(route.query.gameId || gameStore.currentGame?.id || ""));
const game = computed(() => gameStore.currentGame);
const self = computed(() => game.value?.players?.find((p) => p.clientId === auth.clientId));
const spectator = computed(() => !self.value);
const current = computed(() => {
  const g = game.value;
  if (!g?.players?.length) return undefined;
  return g.players[g.turnIndex ?? 0];
});
const locked = computed(() => (game.value?.lockedUntil ?? 0) > Date.now());
const canPlay = computed(() => {
  const s = self.value;
  const g = game.value;
  const c = current.value;
  return Boolean(
    s &&
      g?.status === "playing" &&
      c?.clientId === s.clientId &&
      !s.eliminated &&
      (s.drawCount ?? 0) > 0 &&
      !locked.value,
  );
});
const canRing = computed(() => Boolean(self.value && game.value?.status === "playing" && !self.value.eliminated && !locked.value));

const layoutPlayers = computed(() =>
  (game.value?.players || []).map((p) => ({
    clientId: p.clientId,
    username: p.username,
    connected: p.connected,
    eliminated: p.eliminated,
    drawCount: p.drawCount,
    displayCount: p.displayCount,
  })),
);

onMounted(async () => {
  if (auth.token) {
    gameStore.connectSocket(auth.token);
    if (gameId.value) {
      gameStore.requestLoad(undefined, gameId.value);
      try {
        await gameStore.loadGame(gameId.value);
      } catch {
        /* tolerate offline dev */
      }
    }
  }
});

onUnmounted(() => {
  gameStore.disconnectSocket();
});

async function playCard(): Promise<void> {
  if (gameId.value) await gameStore.playCard(gameId.value);
}

async function ringBell(): Promise<void> {
  if (gameId.value) await gameStore.ringBell(gameId.value);
}
</script>

<template>
  <AppShell>
    <main class="page game-shell">
      <GameTable
        v-if="game"
        :players="layoutPlayers"
        :self-id="auth.clientId"
        :current-id="current?.clientId"
        :can-play="canPlay"
        :can-ring="canRing"
        @play="playCard"
        @ring="ringBell"
      >
        <template v-if="spectator" #spectator>
          <div class="spectator-badge">观战模式</div>
        </template>
        <template #turn-banner="{ player, isCurrent }">
          <div v-if="isCurrent && game?.status === 'playing'" class="turn-banner">
            <strong>
              {{ player.clientId === auth.clientId ? "轮到你出牌" : `轮到 ${player.username} 出牌` }}
            </strong>
          </div>
        </template>
        <template #display="{ player }">
          <img
            v-if="(player.displayCount ?? 0) > 0"
            class="face-card"
            :src="game?.players?.find((x) => x.clientId === player.clientId)?.displayPile?.slice(-1)[0]?.imageUrl || ''"
            alt=""
          />
        </template>
        <template v-if="game?.status === 'finished'" #result>
          <section class="result-panel">
            <h2>对局结束</h2>
            <p v-if="game.winnerId">胜者：{{ game.players?.find((p) => p.clientId === game.winnerId)?.username }}</p>
          </section>
        </template>
      </GameTable>
      <section v-else class="center-message muted">加载对局中…</section>
      <section class="game-bottom">
        <div class="log-area">
          <div v-for="log in game?.logs || []" :key="log.id">{{ log.text }}</div>
        </div>
        <div class="chat-area game-chat">
          <h3>聊天</h3>
          <p class="muted">房间聊天由 WebSocket ROOM 事件驱动。</p>
        </div>
      </section>
    </main>
  </AppShell>
</template>
