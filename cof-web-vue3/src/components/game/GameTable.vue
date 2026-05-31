<script setup lang="ts">
import { computed } from "vue";
import { playerLayouts, type LayoutPlayer } from "@/composables/playerLayouts";

const props = defineProps<{
  players: LayoutPlayer[];
  selfId: string;
  currentId?: string;
  canPlay: boolean;
  canRing: boolean;
}>();

const emit = defineEmits<{
  play: [];
  ring: [];
}>();

const layouts = computed(() => playerLayouts(props.players, props.selfId));
const currentId = computed(() => props.currentId || "");

/** 由后端 / Vite proxy 提供，勿写静态 src 以免被 Vite 当模块解析 */
const tableLogoUrl = "/assets/logo.png";
const bellUrl = "/assets/bell.png";
const cardBackPlaceholderUrl = "/cards/placeholder-back.png";
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
          v-if="(item.player.drawCount ?? 0) > 0"
          class="mini-card"
          :src="cardBackPlaceholderUrl"
          alt=""
          @error="($event.target as HTMLImageElement).style.display = 'none'"
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
        <slot name="display" :player="item.player" />
      </div>
    </div>
    <slot name="animation" />
    <slot name="result" />
  </section>
</template>
