<script setup lang="ts">
import { computed, onMounted } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import RoomChat from "@/components/RoomChat.vue";
import ComputerManager from "@/components/room/ComputerManager.vue";
import StartVotePanel from "@/components/room/StartVotePanel.vue";
import { useAuthStore } from "@/stores/authStore";
import { useLobbyStore } from "@/stores/lobbyStore";
import { useRoomStore } from "@/stores/roomStore";
import { useGameStore } from "@/stores/gameStore";
import * as roomsApi from "@/api/rooms";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const lobby = useLobbyStore();
const roomStore = useRoomStore();
const gameStore = useGameStore();

const roomId = computed(() => String(route.params.roomId || roomStore.activeRoomId || ""));
const room = computed(() => roomStore.currentRoom);
const isHost = computed(() => room.value?.hostId === auth.clientId);
const chatMessages = computed(() => room.value?.chatMessages ?? []);
const minPlayers = computed(() => room.value?.settings?.minPlayers ?? 2);
const canStart = computed(() => (room.value?.players?.length ?? 0) >= minPlayers.value);

onMounted(async () => {
  void lobby.loadMeta();
  if (route.params.roomId) {
    roomStore.activeRoomId = String(route.params.roomId);
    try {
      await roomStore.refreshRoom(String(route.params.roomId));
    } catch {
      /* ignore */
    }
  }
  if (auth.token) {
    gameStore.requestLoad(roomId.value);
  }
});

async function start(): Promise<void> {
  if (!roomId.value) return;
  const game = await gameStore.startGame(roomId.value);
  await router.push({ name: "loading", params: { roomId: roomId.value }, query: { gameId: game.id } });
}

async function voteStart(): Promise<void> {
  if (!roomId.value) return;
  const result = await roomsApi.startVote(roomId.value);
  roomStore.applyRoomUpdate(result.room);
}

async function cancelVote(): Promise<void> {
  if (!roomId.value) return;
  const result = await roomsApi.cancelStartVote(roomId.value);
  roomStore.applyRoomUpdate(result.room);
}

async function onChatSent(): Promise<void> {
  if (roomId.value) await roomStore.refreshRoom(roomId.value);
}
</script>

<template>
  <AppShell>
    <PagePanel title="等待室">
      <p class="status-line">
        房间 #{{ roomId }} · {{ room?.players?.length ?? 0 }}/{{ room?.settings?.maxPlayers ?? 8 }} 人，至少
        {{ minPlayers }} 人开始。
      </p>

      <div class="waiting-layout">
        <section class="waiting-main">
          <div class="player-grid">
            <div v-for="p in room?.playerDetails || []" :key="p.clientId" class="card player-row">
              <span class="player-label">
                <span :class="{ 'god-name': p.username?.toUpperCase() === 'GOD' }">{{ p.username }}</span>
                <span v-if="p.clientId === room?.hostId" class="pill ok">房主</span>
                <span v-if="p.isComputer" class="pill muted">人机</span>
                <span v-if="room?.startVotes?.includes(p.clientId)" class="pill ok">已投票</span>
              </span>
            </div>
          </div>

          <StartVotePanel
            v-if="room"
            :room="room"
            :self-id="auth.clientId"
            @vote="voteStart"
            @cancel="cancelVote"
          />

          <ComputerManager v-if="isHost && room" :room="room" />

          <div class="actions">
            <template v-if="isHost">
              <RouterLink :to="{ name: 'settings', params: { roomId } }">
                <button type="button">房间设置</button>
              </RouterLink>
              <RouterLink :to="{ name: 'settings', params: { roomId }, query: { transfer: '1' } }">
                <button type="button">转让房主</button>
              </RouterLink>
              <button class="primary" type="button" :disabled="!canStart" @click="start">开始游戏</button>
            </template>
            <RouterLink to="/">
              <button type="button">回主菜单</button>
            </RouterLink>
          </div>
        </section>

        <RoomChat v-if="roomId" class="waiting-chat" :room-id="roomId" :messages="chatMessages" @sent="onChatSent" />
      </div>
    </PagePanel>
  </AppShell>
</template>
