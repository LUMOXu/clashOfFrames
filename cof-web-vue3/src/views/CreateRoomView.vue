<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import LibraryPicker from "@/components/room/LibraryPicker.vue";
import RoomOptionsForm from "@/components/room/RoomOptionsForm.vue";
import { useLobbyStore } from "@/stores/lobbyStore";
import { useRoomStore } from "@/stores/roomStore";
import type { CardLibraryMeta } from "@/types/computer";
import type { GameSettings } from "@/types/api";

const lobby = useLobbyStore();
const roomStore = useRoomStore();
const router = useRouter();

const settings = ref<GameSettings>({
  minPlayers: 2,
  maxPlayers: 8,
  isPublic: true,
  libraryIds: [],
  libraryCopies: {},
  startVoteThresholdMode: "auto",
  startVoteThreshold: 2,
  allowEmptyBell: false,
  randomBacks: false,
  conflictResolution: true,
  disconnectProtection: true,
});

const libraries = computed(() => lobby.cardLibraries as CardLibraryMeta[]);

onMounted(async () => {
  await lobby.loadMeta();
  if (!settings.value.libraryIds?.length && libraries.value.length) {
    const first = libraries.value[0];
    settings.value.libraryIds = [first.id];
    settings.value.libraryCopies = { [first.id]: 1 };
  }
});

async function create(): Promise<void> {
  const room = await roomStore.createRoom(settings.value, []);
  await router.push({ name: "waiting", params: { roomId: room.id } });
}
</script>

<template>
  <AppShell title="创建房间">
    <PagePanel title="创建房间">
      <form class="create-room-form grid" @submit.prevent="create">
        <RoomOptionsForm :settings="settings" show-vote show-advanced />
        <LibraryPicker v-if="!lobby.loadingMeta" :libraries="libraries" :settings="settings" />
        <p v-else class="muted">加载卡牌库…</p>
        <div class="actions">
          <button class="primary" type="submit" :disabled="roomStore.loading">创建房间</button>
          <RouterLink to="/">
            <button type="button">返回</button>
          </RouterLink>
        </div>
      </form>
    </PagePanel>
  </AppShell>
</template>
