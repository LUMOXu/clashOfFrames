<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import LibraryPicker from "@/components/room/LibraryPicker.vue";
import RoomOptionsForm from "@/components/room/RoomOptionsForm.vue";
import { useAuthStore } from "@/stores/authStore";
import { useRoomStore } from "@/stores/roomStore";
import { useLobbyStore } from "@/stores/lobbyStore";
import * as roomsApi from "@/api/rooms";
import type { CardLibraryMeta } from "@/types/computer";
import type { GameSettings } from "@/types/api";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const roomStore = useRoomStore();
const lobby = useLobbyStore();

const roomId = computed(() => String(route.params.roomId || roomStore.activeRoomId || ""));
const isHost = computed(() => roomStore.currentRoom?.hostId === auth.clientId);
const transferMode = computed(() => route.query.transfer === "1");

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
const newHostId = ref("");
const saveHint = ref("");

const libraries = computed(() => lobby.cardLibraries as CardLibraryMeta[]);

let saveTimer: ReturnType<typeof setTimeout> | null = null;

onMounted(async () => {
  await lobby.loadMeta();
  if (roomStore.currentRoom?.settings) {
    settings.value = { ...roomStore.currentRoom.settings };
  }
});

watch(
  settings,
  () => {
    if (!isHost.value || transferMode.value) return;
    if (saveTimer) clearTimeout(saveTimer);
    saveTimer = setTimeout(async () => {
      if (!roomId.value) return;
      const result = await roomsApi.updateRoomSettings(roomId.value, settings.value);
      roomStore.applyRoomUpdate(result.room);
      saveHint.value = "已自动保存";
      window.setTimeout(() => {
        saveHint.value = "";
      }, 1500);
    }, 180);
  },
  { deep: true },
);

async function transfer(): Promise<void> {
  if (!roomId.value || !newHostId.value) return;
  await roomsApi.transferHost(roomId.value, newHostId.value);
  await router.push({ name: "waiting", params: { roomId: roomId.value } });
}
</script>

<template>
  <AppShell>
    <PagePanel :title="transferMode ? '转让房主' : '房间设置'">
      <template v-if="transferMode">
        <label>
          新房主
          <select v-model="newHostId">
            <option value="">选择玩家</option>
            <option
              v-for="p in roomStore.currentRoom?.playerDetails || []"
              :key="p.clientId"
              :value="p.clientId"
            >
              {{ p.username }}
            </option>
          </select>
        </label>
        <button class="primary" type="button" @click="transfer">确认转让</button>
      </template>
      <template v-else>
        <p v-if="saveHint" class="muted">{{ saveHint }}</p>
        <form class="grid">
          <RoomOptionsForm :settings="settings" show-vote show-advanced />
          <LibraryPicker :libraries="libraries" :settings="settings" />
        </form>
      </template>
    </PagePanel>
  </AppShell>
</template>
