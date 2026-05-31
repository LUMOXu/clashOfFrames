<script setup lang="ts">
import { computed, onMounted, onUnmounted, toRef } from "vue";
import { useRoute } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import GameTable from "@/components/game/GameTable.vue";
import GameAlert from "@/components/game/GameAlert.vue";
import GameAnimation from "@/components/game/GameAnimation.vue";
import GameResultModal from "@/components/game/GameResultModal.vue";
import RoomChat from "@/components/RoomChat.vue";
import { unlockGameAudio } from "@/composables/useGameAudio";
import { useTurnBanner } from "@/composables/useTurnBanner";
import { useAuthStore } from "@/stores/authStore";
import { useGameStore } from "@/stores/gameStore";
import { useRoomStore } from "@/stores/roomStore";

const route = useRoute();
const auth = useAuthStore();
const gameStore = useGameStore();
const roomStore = useRoomStore();

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
      !locked.value &&
      Date.now() >= (g.turnAvailableAt ?? 0),
  );
});
const canRing = computed(
  () => Boolean(self.value && game.value?.status === "playing" && !self.value.eliminated && !locked.value),
);

const { turnTitle, turnDetail, globalTurnHint } = useTurnBanner(game, toRef(() => auth.clientId), locked);

const chatMessages = computed(() => roomStore.currentRoom?.chatMessages ?? []);

function onFirstInteraction(): void {
  void unlockGameAudio();
}

onMounted(async () => {
  const roomId = String(route.params.roomId || roomStore.activeRoomId || "");
  if (auth.token) {
    gameStore.requestLoad(roomId || undefined, gameId.value || undefined);
    if (gameId.value) {
      try {
        await gameStore.loadGame(gameId.value);
      } catch {
        /* offline dev */
      }
    }
  }
  window.addEventListener("pointerdown", onFirstInteraction, { once: true });
  window.addEventListener("keydown", onFirstInteraction, { once: true });
});

onUnmounted(() => {
  window.removeEventListener("pointerdown", onFirstInteraction);
  window.removeEventListener("keydown", onFirstInteraction);
});

async function playCard(): Promise<void> {
  await unlockGameAudio();
  if (gameId.value) await gameStore.playCard(gameId.value);
}

async function ringBell(): Promise<void> {
  await unlockGameAudio();
  if (gameId.value) await gameStore.ringBell(gameId.value);
}

async function onContinue(): Promise<void> {
  if (gameId.value) await gameStore.continueGame(gameId.value);
}
</script>

<template>
  <AppShell immersive>
    <main class="page game-page">
      <p v-if="globalTurnHint" class="game-turn-hint muted">{{ globalTurnHint }}</p>
      <GameTable
        v-if="game"
        :players="game.players || []"
        :self-id="auth.clientId"
        :current-id="current?.clientId"
        :can-play="canPlay"
        :can-ring="canRing"
        :last-animation="game.lastAnimation"
        @play="playCard"
        @ring="ringBell"
      >
        <template #alert>
          <GameAlert :match="game.lastMatch" />
        </template>
        <template v-if="spectator" #spectator>
          <div class="spectator-badge">观战模式</div>
        </template>
        <template #turn-banner="{ player, isCurrent }">
          <div v-if="isCurrent && game?.status === 'playing'" class="turn-banner">
            <strong>{{ turnTitle(player, isCurrent) }}</strong>
            <span v-if="turnDetail(player, isCurrent)" class="turn-detail">{{ turnDetail(player, isCurrent) }}</span>
          </div>
        </template>
        <template v-if="locked" #animation>
          <GameAnimation
            :animation="game.lastAnimation"
            :players="game.players || []"
            :self-id="auth.clientId"
          />
        </template>
        <template v-if="game?.status === 'finished'" #result>
          <GameResultModal :game="game" :self-id="auth.clientId" @continue="onContinue" />
        </template>
      </GameTable>
      <section v-else class="center-message muted">加载对局中…</section>
      <section class="game-bottom">
        <div class="log-area">
          <div v-for="(log, i) in game?.logs || []" :key="log.id || i">{{ log.text }}</div>
        </div>
        <RoomChat
          v-if="roomStore.activeRoomId"
          variant="game"
          :room-id="roomStore.activeRoomId"
          :messages="chatMessages"
          @sent="roomStore.refreshRoom(roomStore.activeRoomId!)"
        />
      </section>
    </main>
  </AppShell>
</template>
