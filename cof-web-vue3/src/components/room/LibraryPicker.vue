<script setup lang="ts">
import { computed } from "vue";
import type { GameSettings } from "@/types/api";
import type { CardLibraryMeta } from "@/types/computer";
import { libraryCopyLimit } from "@/utils/format";

const props = defineProps<{
  libraries: CardLibraryMeta[];
  settings: GameSettings;
}>();

const selected = computed(() => new Set(props.settings.libraryIds ?? []));

const cardTotal = computed(() =>
  props.libraries
    .filter((lib) => selected.value.has(lib.id))
    .reduce((sum, lib) => {
      const copies = props.settings.libraryCopies?.[lib.id] ?? 1;
      return sum + (lib.cardCount ?? 0) * copies;
    }, 0),
);

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
  const max = libraryCopyLimit(lib);
  props.settings.libraryCopies[lib.id] = Math.max(1, Math.min(max, value || 1));
}
</script>

<template>
  <section class="panel">
    <h3>卡牌库 <span class="pill">当前 {{ cardTotal }} 张</span></h3>
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
            @change="toggleLibrary(lib.id, ($event.target as HTMLInputElement).checked)"
          />
          <input
            class="copy-input"
            type="number"
            min="1"
            :max="libraryCopyLimit(lib)"
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
