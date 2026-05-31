<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import RoomChat from "@/components/RoomChat.vue";
import ComputerInviteModal from "@/components/room/ComputerInviteModal.vue";
import StartVotePanel from "@/components/room/StartVotePanel.vue";
import { useAuthStore } from "@/stores/authStore";
import { useLobbyStore } from "@/stores/lobbyStore";
import { useRoomStore } from "@/stores/roomStore";
import { useGameStore } from "@/stores/gameStore";
import * as roomsApi from "@/api/rooms";
import type { ComputerPlayer } from "@/types/computer";
import { isGodComputer } from "@/utils/format";
import { resolveComputerId } from "@/utils/computerPlayer";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const lobby = useLobbyStore();
const roomStore = useRoomStore();
const gameStore = useGameStore();

const computerModalOpen = ref(false);

const roomId = computed(() => String(route.params.roomId || roomStore.activeRoomId || ""));
const room = computed(() => roomStore.currentRoom);
const isHost = computed(() => room.value?.hostId === auth.clientId);
const chatMessages = computed(() => room.value?.chatMessages ?? []);
const minPlayers = computed(() => room.value?.settings?.minPlayers ?? 2);
const canStart = computed(() => (room.value?.players?.length ?? 0) >= minPlayers.value);
const roomFull = computed(
  () => (room.value?.players?.length ?? 0) >= (room.value?.settings?.maxPlayers ?? 8),
);

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

function openComputerModal(): void {
  computerModalOpen.value = true;
}

async function inviteComputer(computer: ComputerPlayer): Promise<void> {
  if (!roomId.value || roomFull.value) return;
  const result = await roomsApi.addComputer(roomId.value, computer.id);
  roomStore.applyRoomUpdate(result.room);
}

async function removeComputer(computerId: string): Promise<void> {
  if (!roomId.value) return;
  const result = await roomsApi.removeComputer(roomId.value, computerId);
  roomStore.applyRoomUpdate(result.room);
}

async function leaveRoom(): Promise<void> {
  if (!roomId.value) return;
  try {
    await roomsApi.leaveRoom(roomId.value);
    roomStore.clearRoom();
    gameStore.clearGame();
    await router.push({ name: "home" });
  } catch (error) {
    roomStore.message = error instanceof Error ? error.message : "退出房间失败";
  }
}

async function disbandRoom(): Promise<void> {
  if (!roomId.value || !isHost.value) return;
  if (!window.confirm("确定要解散这个房间吗？")) return;
  try {
    await roomsApi.disbandRoom(roomId.value);
    roomStore.clearRoom();
    gameStore.clearGame();
    await router.push({ name: "home" });
  } catch (error) {
    roomStore.message = error instanceof Error ? error.message : "解散房间失败";
  }
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
          <div class="waiting-section">
            <div class="player-grid">
            <div v-for="p in room?.playerDetails || []" :key="p.clientId" class="card player-row waiting-player-row">
              <span class="player-label">
                <span :class="{ 'god-name': isGodComputer(p) || p.username?.toUpperCase() === 'GOD' }">
                  {{ p.username }}
                </span>
                <span v-if="p.clientId === room?.hostId" class="pill ok">房主</span>
                <span v-if="p.isComputer" class="pill muted">人机</span>
                <span v-if="room?.startVotes?.includes(p.clientId)" class="pill ok">已投票</span>
              </span>
              <button
                v-if="isHost && p.isComputer && resolveComputerId(p)"
                type="button"
                class="pill-btn"
                @click="removeComputer(resolveComputerId(p)!)"
              >
                移除
              </button>
            </div>
            </div>
            <button
              v-if="isHost"
              type="button"
              class="btn-invite-computer"
              :disabled="roomFull"
              @click="openComputerModal"
            >
              邀请人机
            </button>
          </div>

          <StartVotePanel
            v-if="room"
            :room="room"
            :self-id="auth.clientId"
            @vote="voteStart"
            @cancel="cancelVote"
          />

          <div class="actions waiting-actions">
            <template v-if="isHost">
              <RouterLink class="action-link" :to="{ name: 'settings', params: { roomId } }">
                <button type="button">房间设置</button>
              </RouterLink>
              <RouterLink
                class="action-link"
                :to="{ name: 'settings', params: { roomId }, query: { transfer: '1' } }"
              >
                <button type="button">转让房主</button>
              </RouterLink>
              <button class="primary" type="button" :disabled="!canStart" @click="start">开始游戏</button>
              <button type="button" class="danger" @click="disbandRoom">解散房间</button>
            </template>
            <button type="button" class="danger" @click="leaveRoom">退出房间</button>
            <RouterLink class="action-link" to="/">
              <button type="button">回主菜单</button>
            </RouterLink>
          </div>
        </section>

        <RoomChat
          v-if="roomId"
          variant="waiting"
          :room-id="roomId"
          :messages="chatMessages"
          @sent="onChatSent"
        />
      </div>

      <ComputerInviteModal
        v-if="room && isHost"
        v-model:open="computerModalOpen"
        :room="room"
        @invite="inviteComputer"
      />
    </PagePanel>
  </AppShell>
</template>
