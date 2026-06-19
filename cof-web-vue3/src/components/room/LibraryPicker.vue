<script setup lang="ts">
import { computed } from "vue";
import type { GameSettings } from "@/types/api";
import type { CardLibraryMeta } from "@/types/computer";
import {
  MAX_ROOM_CARDS,
  canAddLibrary,
  maxCopiesWithinRoomLimit,
  selectedCardTotal,
} from "@/utils/libraryCardLimit";

const props = defineProps<{
  libraries: CardLibraryMeta[];
  settings: GameSettings;
}>();

const selected = computed(() => new Set(props.settings.libraryIds ?? []));

const cardTotal = computed(() => selectedCardTotal(props.libraries, props.settings));

function toggleLibrary(id: string, checked: boolean): void {
  const ids = props.settings.libraryIds ?? [];
  if (checked) {
    props.settings.libraryIds = [...ids, id];
    if (!props.settings.libraryCopies) props.settings.libraryCopies = {};
    if (!props.settings.libraryCopies[id]) {
      props.settings.libraryCopies[id] = 1;
    }
  } else {
    props.settings.libraryIds = ids.filter((x) => x !== id);
  }
}

function copyValue(lib: CardLibraryMeta): number {
  return props.settings.libraryCopies?.[lib.id] ?? 1;
}

function setCopy(lib: CardLibraryMeta, value: number): void {
  if (!props.settings.libraryCopies) props.settings.libraryCopies = {};
  const max = maxCopiesWithinRoomLimit(props.libraries, props.settings, lib);
  props.settings.libraryCopies[lib.id] = Math.max(1, Math.min(max, value || 1));
}
</script>

<template>
  <section class="panel">
    <h3>卡牌库 <span class="pill">当前 {{ cardTotal }} / {{ MAX_ROOM_CARDS }} 张</span></h3>
    <p v-if="cardTotal > MAX_ROOM_CARDS" class="error-text">总卡牌数不能超过 {{ MAX_ROOM_CARDS }} 张，请减少份数。</p>
    <div class="library-list">
      <label v-for="lib in libraries" :key="lib.id" class="library-row">
        <span>
          {{ lib.name }}
          <span class="pill muted-inline">{{ lib.cardCount ?? 0 }} 张 / {{ lib.pmvCount ?? 0 }} PMV</span>
        </span>
        <span class="library-controls">
          <input
            type="checkbox"
            :checked="selected.has(lib.id)"
            :disabled="!selected.has(lib.id) && !canAddLibrary(libraries, settings, lib)"
            @change="toggleLibrary(lib.id, ($event.target as HTMLInputElement).checked)"
          />
          <input
            class="copy-input"
            type="number"
            min="1"
            :max="maxCopiesWithinRoomLimit(libraries, settings, lib)"
            :value="copyValue(lib)"
            :disabled="!selected.has(lib.id)"
            aria-label="复制份数"
            @input="setCopy(lib, Number(($event.target as HTMLInputElement).value))"
          />
        </span>
      </label>
    </div>
  </section>
</template>
