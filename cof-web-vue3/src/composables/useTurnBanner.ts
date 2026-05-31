import { computed, onMounted, onUnmounted, ref, type Ref } from "vue";
import type { PublicGame, PublicPlayer } from "@/types/api";

const MAX_VISIBLE_TURN_COUNTDOWN_SECONDS = 8;

export function useTurnBanner(
  game: Ref<PublicGame | null | undefined>,
  selfId: Ref<string>,
  locked: Ref<boolean>,
) {
  const now = ref(Date.now());
  let timer: ReturnType<typeof setInterval> | undefined;

  onMounted(() => {
    timer = setInterval(() => {
      now.value = Date.now();
    }, 250);
  });

  onUnmounted(() => {
    if (timer) clearInterval(timer);
  });

  function turnSecondsLeft(): number {
    const deadline = game.value?.turnDeadlineAt;
    if (!deadline) return 0;
    return Math.max(0, Math.ceil((deadline - now.value) / 1000));
  }

  function turnDetail(player: PublicPlayer, isCurrent: boolean): string {
    const g = game.value;
    if (!g || g.status !== "playing" || !isCurrent) return "";
    if (locked.value) {
      const sec = Math.ceil(((g.lockedUntil ?? 0) - now.value) / 1000);
      return sec > 0 ? `结算倒计时 ${sec} 秒` : "";
    }
    if (player.connected === false) return "玩家掉线，等待自动出牌";
    const remaining = turnSecondsLeft();
    const countdown =
      remaining > 0 && remaining <= MAX_VISIBLE_TURN_COUNTDOWN_SECONDS
        ? `出牌倒计时 ${remaining} 秒`
        : "";
    if (player.clientId === selfId.value) {
      return countdown ? `点击高亮牌堆出牌 · ${countdown}` : "点击高亮牌堆出牌";
    }
    return countdown;
  }

  function turnTitle(player: PublicPlayer, isCurrent: boolean): string {
    if (!isCurrent || game.value?.status !== "playing") return "";
    return player.clientId === selfId.value ? "轮到你出牌" : `轮到 ${player.username} 出牌`;
  }

  const globalTurnHint = computed(() => {
    const g = game.value;
    if (!g || g.status !== "playing" || locked.value) return "";
    const current = g.players?.[g.turnIndex ?? 0];
    if (!current || current.clientId === selfId.value) return "";
    const remaining = turnSecondsLeft();
    if (remaining > 0 && remaining <= MAX_VISIBLE_TURN_COUNTDOWN_SECONDS) {
      return `${current.username} 出牌倒计时 ${remaining} 秒`;
    }
    return "";
  });

  return { turnTitle, turnDetail, globalTurnHint, turnSecondsLeft };
}
