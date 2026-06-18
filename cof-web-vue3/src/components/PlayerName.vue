<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { playerFontUrl, playerNameKind, type PlayerIdentity } from "@/utils/playerName";

const props = defineProps<PlayerIdentity>();
const kind = computed(() => playerNameKind(props));
const family = ref("");
const fontLoads = new Map<string, Promise<string>>();

function safeFamily(url: string): string {
  let hash = 0;
  for (const char of url) hash = ((hash << 5) - hash + char.charCodeAt(0)) | 0;
  return `CofSpecialName${Math.abs(hash)}`;
}

async function loadSubset(url: string): Promise<string> {
  if (typeof FontFace === "undefined" || typeof document === "undefined") return "";
  const existing = fontLoads.get(url);
  if (existing) return existing;
  const promise = (async () => {
    const name = safeFamily(url);
    const face = new FontFace(name, `url("${url}")`, { weight: "700 900", style: "normal" });
    const loaded = await face.load();
    document.fonts.add(loaded);
    return name;
  })();
  fontLoads.set(url, promise);
  try {
    return await promise;
  } catch {
    fontLoads.delete(url);
    return "";
  }
}

watch(
  () => playerFontUrl(props),
  async (url) => {
    family.value = url ? await loadSubset(url) : "";
  },
  { immediate: true },
);
</script>

<template>
  <span
    class="player-name"
    :class="`player-name--${kind}`"
    :style="family ? { fontFamily: `'${family}', 'Songti SC', serif` } : undefined"
  >{{ username }}</span>
</template>
