<script setup lang="ts">
import { computed, toRef } from "vue";
import { playerLayouts } from "@/composables/playerLayouts";
import { useAnimationClock } from "@/composables/useAnimationClock";
import type { PublicAnimation, PublicPlayer } from "@/types/api";
import { flyStyle } from "@/utils/flyStyle";

const props = defineProps<{
  animation?: PublicAnimation | null;
  players: PublicPlayer[];
  selfId: string;
}>();

const animationRef = toRef(props, "animation");
const now = useAnimationClock(animationRef);

const active = computed(() => {
  const animation = props.animation;
  if (!animation?.startedAt) return false;
  return now.value - animation.startedAt <= (animation.durationMs ?? 0) + 700;
});

const layoutMap = computed(
  () =>
    new Map(
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
      ).map((layout) => [layout.player.clientId, layout]),
    ),
);

interface FlyGhost {
  key: string;
  src: string;
  className: string;
  style: Record<string, string>;
}

const ghosts = computed((): FlyGhost[] => {
  const animation = props.animation;
  if (!animation?.startedAt || !active.value) return [];

  const elapsed = Math.max(0, now.value - animation.startedAt);
  const layouts = layoutMap.value;

  if (animation.type === "success") {
    const target = layouts.get(animation.targetPlayerId || "");
    if (!target) return [];
    const matchIds = new Set(animation.matchCardIds || []);
    const out: FlyGhost[] = [];
    let order = 0;
    for (const pile of animation.piles || []) {
      const from = layouts.get(pile.playerId || "");
      if (!from) continue;
      for (const card of pile.cards || []) {
        order += 1;
        const matchClass = matchIds.has(card.id) ? " match-ghost" : "";
        out.push({
          key: `ok-${order}-${card.id}`,
          src: card.imageUrl || card.backUrl || "",
          className: `collect-card${matchClass}`,
          style: flyStyle(
            from.displayX,
            from.displayY,
            target.drawX,
            target.drawY,
            animation.durationMs ?? 0,
            0,
            elapsed,
          ),
        });
      }
    }
    return out;
  }

  if (animation.type === "fail") {
    const moveMs = animation.moveMs ?? 0;
    return (animation.transfers || [])
      .map((transfer, index) => {
        const from = layouts.get(transfer.fromPlayerId || "");
        const to = layouts.get(transfer.toPlayerId || "");
        if (!from || !to || !transfer.card) return null;
        return {
          key: `fail-${index}-${transfer.card.id}`,
          src: transfer.card.backUrl || transfer.card.imageUrl || "",
          className: "fail-card",
          style: flyStyle(
            from.drawX,
            from.drawY,
            to.drawX,
            to.drawY,
            moveMs,
            transfer.delayMs ?? 0,
            elapsed,
          ),
        };
      })
      .filter((item): item is FlyGhost => item !== null);
  }

  return [];
});
</script>

<template>
  <div v-if="active && ghosts.length" class="animation-layer">
    <img
      v-for="ghost in ghosts"
      :key="ghost.key"
      class="fly-card"
      :class="ghost.className"
      :src="ghost.src"
      alt=""
      :style="ghost.style"
    />
  </div>
</template>
