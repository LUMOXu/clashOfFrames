<script setup lang="ts">
import { computed, toRef } from "vue";
import { playerLayouts } from "@/composables/playerLayouts";
import { useAnimationClock } from "@/composables/useAnimationClock";
import type { PublicAnimation, PublicPlayer } from "@/types/api";
import { stackStyle } from "@/utils/cardStack";
import { visualDrawPile } from "@/utils/visualDrawPile";

const props = defineProps<{
  players: PublicPlayer[];
  selfId: string;
  currentId?: string;
  canPlay: boolean;
  canRing: boolean;
  lastAnimation?: PublicAnimation | null;
}>();

const emit = defineEmits<{
  play: [];
  ring: [];
}>();

const animationRef = toRef(props, "lastAnimation");
const now = useAnimationClock(animationRef);

const layouts = computed(() =>
  playerLayouts(
    props.players.map((p) => ({
      clientId: p.clientId,
      username: p.username,
      connected: p.connected,
      eliminated: p.eliminated,
      drawCount: p.drawCount,
      displayCount: p.displayCount,
    })),
    props.selfId,
  ),
);

const playerMap = computed(() => new Map(props.players.map((p) => [p.clientId, p])));

const currentId = computed(() => props.currentId || "");

const tableLogoUrl = "/assets/logo.png";
const bellUrl = "/assets/bell.png";
const cardBackPlaceholderUrl = "/cards/placeholder-back.png";

function drawCardsFor(playerId: string) {
  const player = playerMap.value.get(playerId);
  if (!player) return [];
  return visualDrawPile(player, props.lastAnimation, now.value);
}

function displayCardsFor(playerId: string) {
  return playerMap.value.get(playerId)?.displayPile || [];
}
</script>

<template>
  <section class="table-area">
    <img class="table-logo" :src="tableLogoUrl" alt="" />
    <div class="game-hud">
      <slot name="alert" />
      <slot name="spectator" />
    </div>
    <button
      class="bell"
      type="button"
      title="按铃"
      :disabled="!canRing"
      @click="emit('ring')"
    >
      <img :src="bellUrl" alt="bell" />
    </button>
    <div class="station-layer">
      <div
        v-for="item in layouts"
        :key="item.player.clientId"
        class="station"
        :class="{
          current: currentId === item.player.clientId,
          eliminated: item.player.eliminated,
        }"
        :style="{ left: `${item.x}%`, top: `${item.y}%` }"
      >
        <div class="station-name">
          {{ item.player.username }}
          <template v-if="item.player.connected === false">（退出）</template>
          <template v-if="item.player.eliminated">（淘汰）</template>
        </div>
        <div class="count">
          未出 {{ item.player.drawCount ?? 0 }} | 已出 {{ item.player.displayCount ?? 0 }}
        </div>
        <slot name="turn-banner" :player="item.player" :is-current="currentId === item.player.clientId" />
      </div>
    </div>
    <div class="pile-layer draw-layer">
      <button
        v-for="item in layouts"
        :key="`draw-${item.player.clientId}`"
        class="draw-stack table-pile"
        :class="{ current: currentId === item.player.clientId }"
        :style="{ left: `${item.drawX}%`, top: `${item.drawY}%` }"
        type="button"
        title="出牌"
        :disabled="!(item.player.clientId === selfId && canPlay)"
        @click="emit('play')"
      >
        <img
          v-for="(card, index) in drawCardsFor(item.player.clientId)"
          :key="card.id"
          class="mini-card"
          :src="card.backUrl || cardBackPlaceholderUrl"
          alt=""
          :style="stackStyle(card.id, index, 'draw', true)"
          @error="($event.target as HTMLImageElement).src = cardBackPlaceholderUrl"
        />
      </button>
    </div>
    <div class="pile-layer display-layer">
      <div
        v-for="item in layouts"
        :key="`display-${item.player.clientId}`"
        class="display-stack table-pile"
        :class="{ eliminated: item.player.eliminated }"
        :style="{ left: `${item.displayX}%`, top: `${item.displayY}%` }"
      >
        <img
          v-for="(card, index) in displayCardsFor(item.player.clientId)"
          :key="card.id"
          class="mini-card"
          :src="card.imageUrl || card.backUrl || ''"
          :alt="card.pmvName || ''"
          :style="stackStyle(card.id, index, 'display', false)"
        />
      </div>
    </div>
    <slot name="animation" />
    <slot name="result" />
  </section>
</template>
