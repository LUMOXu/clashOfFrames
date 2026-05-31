let bellAudio: HTMLAudioElement | null = null;
let sendCardAudio: HTMLAudioElement | null = null;
let unlocked = false;

function bell(): HTMLAudioElement {
  if (!bellAudio) {
    bellAudio = new Audio("/audio/ding.wav");
    bellAudio.preload = "auto";
  }
  return bellAudio;
}

function sendCard(): HTMLAudioElement {
  if (!sendCardAudio) {
    sendCardAudio = new Audio("/audio/sendcard.mp3");
    sendCardAudio.preload = "auto";
  }
  return sendCardAudio;
}

function playClip(factory: () => HTMLAudioElement): void {
  const audio = factory();
  audio.currentTime = 0;
  void audio.play().catch(() => {
    /* autoplay policy or missing file */
  });
}

/** 浏览器要求用户手势后才能播放音效 */
export async function unlockGameAudio(): Promise<void> {
  if (unlocked) return;
  for (const factory of [bell, sendCard]) {
    const audio = factory();
    audio.muted = true;
    try {
      await audio.play();
      audio.pause();
      audio.currentTime = 0;
      audio.muted = false;
    } catch {
      /* ignore */
    }
  }
  unlocked = true;
}

export function playBellSound(): void {
  void playClip(bell);
}

export function playCardSound(): void {
  void playClip(sendCard);
}

export function handleAudioEvent(type: string | undefined): void {
  if (type === "ring-bell") playBellSound();
  if (type === "play-card") playCardSound();
}
