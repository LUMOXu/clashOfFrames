import { computed, onUnmounted, ref, watch, type Ref } from "vue";
import type { PublicGame } from "@/types/api";
import type { GameReplayFrame, GameReplayTimeline } from "@/types/replay";

export const REPLAY_SPEED_OPTIONS = [0.5, 1, 1.5, 2, 3, 4] as const;

function findFrameIndex(frames: GameReplayFrame[], ms: number): number {
  if (!frames.length) return 0;
  let index = 0;
  for (let i = 0; i < frames.length; i += 1) {
    if (frames[i].t <= ms) {
      index = i;
    } else {
      break;
    }
  }
  return index;
}

function cloneState(state: PublicGame): PublicGame {
  return JSON.parse(JSON.stringify(state)) as PublicGame;
}

function formatReplayClock(ms: number): string {
  const total = Math.max(0, Math.floor(ms));
  const minutes = Math.floor(total / 60_000);
  const seconds = Math.floor((total % 60_000) / 1_000);
  const millis = total % 1_000;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}.${String(millis).padStart(3, "0")}`;
}

export function useMatchReplayPlayer(timeline: Ref<GameReplayTimeline | null>) {
  const speed = ref(1);
  const playing = ref(false);
  const replayMs = ref(0);
  const frameIndex = ref(0);
  const displayGame = ref<PublicGame | null>(null);
  const replayClock = ref(Date.now());
  const showAnimation = ref(false);

  const frames = computed(() => timeline.value?.frames ?? []);
  const maxMs = computed(() => {
    const list = frames.value;
    return list.length ? list[list.length - 1].t : 0;
  });
  const clockLabel = computed(() => formatReplayClock(replayMs.value));
  const durationLabel = computed(() => formatReplayClock(maxMs.value));

  let rafId = 0;
  let lastTickAt = 0;

  function applyFrameAt(ms: number, triggerAnimation: boolean): void {
    const list = frames.value;
    if (!list.length) {
      displayGame.value = null;
      return;
    }
    const clamped = Math.max(0, Math.min(Math.floor(ms), maxMs.value));
    replayMs.value = clamped;
    const index = findFrameIndex(list, clamped);
    frameIndex.value = index;
    const frame = list[index];
    const prev = index > 0 ? list[index - 1] : null;
    const state = cloneState(frame.state);
    const anim = state.lastAnimation;
    const prevAnimId = prev?.state.lastAnimation?.id;
    showAnimation.value = false;
    if (triggerAnimation && anim?.id && anim.id !== prevAnimId) {
      const now = Date.now();
      state.lastAnimation = { ...anim, startedAt: now };
      state.lockedUntil = now + (anim.durationMs ?? 0);
      showAnimation.value = true;
    } else {
      state.lastAnimation = undefined;
      state.lockedUntil = 0;
    }
    displayGame.value = state;
    replayClock.value = Date.now();
  }

  function seek(ms: number): void {
    playing.value = false;
    applyFrameAt(ms, true);
  }

  function step(delta: number): void {
    const list = frames.value;
    if (!list.length) return;
    const nextIndex = Math.max(0, Math.min(list.length - 1, frameIndex.value + delta));
    seek(list[nextIndex].t);
  }

  function tick(now: number): void {
    if (!playing.value) return;
    if (!lastTickAt) {
      lastTickAt = now;
    }
    const delta = (now - lastTickAt) * speed.value;
    lastTickAt = now;
    const nextMs = Math.min(maxMs.value, Math.floor(replayMs.value + delta));
    const crossed =
      findFrameIndex(frames.value, nextMs) !== findFrameIndex(frames.value, replayMs.value);
    applyFrameAt(nextMs, crossed);
    if (replayMs.value >= maxMs.value) {
      playing.value = false;
      return;
    }
    rafId = requestAnimationFrame(tick);
  }

  function play(): void {
    if (!frames.value.length) return;
    if (replayMs.value >= maxMs.value) {
      seek(0);
    }
    playing.value = true;
  }

  function pause(): void {
    playing.value = false;
  }

  function togglePlay(): void {
    if (playing.value) pause();
    else play();
  }

  watch(playing, (isPlaying) => {
    cancelAnimationFrame(rafId);
    if (isPlaying) {
      lastTickAt = 0;
      rafId = requestAnimationFrame(tick);
    }
  });

  watch(
    timeline,
    (value) => {
      playing.value = false;
      replayMs.value = 0;
      frameIndex.value = 0;
      if (value?.frames?.length) {
        applyFrameAt(0, false);
      } else {
        displayGame.value = null;
      }
    },
    { immediate: true },
  );

  let animRaf = 0;
  watch(
  () => showAnimation.value && (displayGame.value?.lastAnimation?.durationMs ?? 0) > 0,
    (active) => {
      cancelAnimationFrame(animRaf);
      if (!active) return;
      const loop = () => {
        replayClock.value = Date.now();
        const until = displayGame.value?.lockedUntil ?? 0;
        if (until > replayClock.value) {
          animRaf = requestAnimationFrame(loop);
        } else {
          showAnimation.value = false;
        }
      };
      animRaf = requestAnimationFrame(loop);
    },
  );

  onUnmounted(() => {
    cancelAnimationFrame(rafId);
    cancelAnimationFrame(animRaf);
  });

  const locked = computed(
    () => showAnimation.value && (displayGame.value?.lockedUntil ?? 0) > replayClock.value,
  );

  return {
    speed,
    playing,
    replayMs,
    maxMs,
    frameIndex,
    displayGame,
    replayClock,
    showAnimation,
    locked,
    clockLabel,
    durationLabel,
    seek,
    step,
    play,
    pause,
    togglePlay,
    formatReplayClock,
  };
}
