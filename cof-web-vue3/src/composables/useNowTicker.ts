import { onMounted, onUnmounted, ref } from "vue";

/** 周期性刷新 Date.now()，用于依赖时间的 computed（出牌冷却等）。 */
export function useNowTicker(intervalMs = 200): { now: ReturnType<typeof ref<number>> } {
  const now = ref(Date.now());
  let timer: ReturnType<typeof setInterval> | undefined;
  onMounted(() => {
    timer = setInterval(() => {
      now.value = Date.now();
    }, intervalMs);
  });
  onUnmounted(() => {
    if (timer) clearInterval(timer);
  });
  return { now };
}
