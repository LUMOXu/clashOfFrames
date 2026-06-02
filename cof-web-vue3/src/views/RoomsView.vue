<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useRoomStore } from "@/stores/roomStore";
import type { RoomSummary } from "@/types/api";

const roomStore = useRoomStore();
const router = useRouter();

onMounted(() => {
  void roomStore.fetchRooms(true);
});

const sortedRooms = computed(() =>
  [...roomStore.rooms].sort((a, b) => {
    const rank = (room: RoomSummary) => (room.status === "waiting" ? 0 : room.status === "loading" ? 1 : 2);
    return rank(a) - rank(b) || String(a.id).localeCompare(String(b.id));
  }),
);

async function joinRoom(id: string): Promise<void> {
  await roomStore.joinRoom(id);
  await router.push({ name: "waiting", params: { roomId: id } });
}

function statusLabel(status?: string): string {
  if (status === "waiting") return "等待中";
  if (status === "loading") return "加载中";
  if (status === "playing") return "对局中";
  if (status === "finished") return "已结束";
  return status || "未知";
}

function deckLabel(room: RoomSummary): string {
  const ids = room.settings?.libraryIds ?? [];
  if (!ids.length) return "默认卡组";
  return ids.join("、");
}

function voteLabel(room: RoomSummary): string {
  const votes = room.startVotes?.length ?? 0;
  if (!votes) return "暂无投票";
  return `${votes} 票`;
}
</script>

<template>
  <AppShell>
    <PagePanel title="房间列表">
      <p v-if="roomStore.loading" class="muted">加载中...</p>
      <div v-else class="library-list rooms-list">
        <div v-for="room in sortedRooms" :key="room.id" class="room-row room-card">
          <div class="room-card-main">
            <div class="room-card-head">
              <strong>房间 #{{ room.id }}</strong>
              <span class="pill" :class="{ ok: room.status === 'waiting', warn: room.status === 'playing' }">
                {{ statusLabel(room.status) }}
              </span>
            </div>
            <div class="room-meta">
              <span>人数：{{ room.players?.length ?? 0 }}/{{ room.settings?.maxPlayers ?? 8 }}</span>
              <span>最低开局：{{ room.settings?.minPlayers ?? 2 }}</span>
              <span>卡组：{{ deckLabel(room) }}</span>
              <span>{{ room.settings?.isPublic === false ? "私密" : "公开" }}</span>
              <span>投票：{{ voteLabel(room) }}</span>
              <span v-if="room.gameId">对局：{{ room.gameId }}</span>
            </div>
          </div>
          <button type="button" @click="joinRoom(room.id)">
            {{ room.status === "waiting" ? "加入" : "查看" }}
          </button>
        </div>
        <p v-if="!sortedRooms.length" class="muted">暂无公开房间。</p>
      </div>
    </PagePanel>
  </AppShell>
</template>
