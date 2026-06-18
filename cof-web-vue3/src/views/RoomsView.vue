<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useRoomStore } from "@/stores/roomStore";
import { useLobbyStore } from "@/stores/lobbyStore";
import type { RoomSummary } from "@/types/api";
import PlayerName from "@/components/PlayerName.vue";
import { recordId, recordName } from "@/utils/record";

const roomStore = useRoomStore();
const router = useRouter();
const lobby = useLobbyStore();

onMounted(() => {
  void Promise.all([roomStore.fetchRooms(false), lobby.loadMeta()]);
});

const sortedRooms = computed(() =>
  roomStore.rooms.filter((room) => room.settings?.isPublic !== false).sort((a, b) => {
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
  return ids
    .map((id) => {
      const library = lobby.cardLibraries.find((item, index) => recordId(item, index) === String(id));
      const copies = room.settings?.libraryCopies?.[id] ?? 1;
      return `${recordName(library, `卡组 #${id}`)} × ${copies}`;
    })
    .join("、");
}

function rulesLabel(room: RoomSummary): string {
  const settings = room.settings;
  const rules = [`${settings?.minPlayers ?? 2}–${settings?.maxPlayers ?? 8} 人`];
  if (settings?.randomBacks) rules.push("随机牌背");
  if (settings?.conflictResolution) rules.push("抢铃冲突保护");
  if (settings?.disconnectProtection) rules.push("断线保护");
  return rules.join(" · ");
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
              <span class="room-meta-wide">玩家：
                <template v-for="(player, index) in room.playerDetails || []" :key="player.clientId">
                  <span v-if="index">、</span><PlayerName v-bind="player" />
                </template>
                <span v-if="!room.playerDetails?.length">暂无玩家</span>
              </span>
              <span class="room-meta-wide">卡组：{{ deckLabel(room) }}</span>
              <span class="room-meta-wide">规则：{{ rulesLabel(room) }}</span>
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
