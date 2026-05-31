import { computed, onUnmounted, ref, watch, type Ref } from "vue";
import type { PublicAnimation } from "@/types/api";

/** 动画播放期间刷新时间戳，驱动飞牌与 visualDrawPile */
export function useAnimationClock(animation: Ref<PublicAnimation | null | undefined>): Ref<number> {
  const now = ref(Date.now());
  let frame = 0;

  const active = computed(() => {
    const a = animation.value;
    if (!a?.startedAt) return false;
    const elapsed = now.value - a.startedAt;
    return elapsed <= (a.durationMs ?? 0) + 700;
  });

  function loop(): void {
    now.value = Date.now();
    if (active.value) {
      frame = requestAnimationFrame(loop);
    }
  }

  watch(
    () => [animation.value?.id, animation.value?.startedAt] as const,
    () => {
      cancelAnimationFrame(frame);
      now.value = Date.now();
      if (active.value) {
        frame = requestAnimationFrame(loop);
      }
    },
    { immediate: true },
  );

  watch(active, (isActive) => {
    if (isActive && !frame) {
      frame = requestAnimationFrame(loop);
    }
  });

  onUnmounted(() => cancelAnimationFrame(frame));

  return now;
}
