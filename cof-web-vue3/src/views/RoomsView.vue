<script setup lang="ts">
import { onMounted } from "vue";
import { useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useRoomStore } from "@/stores/roomStore";

const roomStore = useRoomStore();
const router = useRouter();

onMounted(() => {
  void roomStore.fetchRooms(true);
});

async function joinRoom(id: string): Promise<void> {
  await roomStore.joinRoom(id);
  await router.push({ name: "waiting", params: { roomId: id } });
}
</script>

<template>
  <AppShell>
    <PagePanel title="房间列表">
      <p v-if="roomStore.loading" class="muted">加载中...</p>
      <div v-else class="library-list">
        <div v-for="room in roomStore.rooms" :key="room.id" class="room-row">
          <div>
            <strong>{{ room.id }}</strong>
            <div class="room-meta">
              <span>状态：{{ room.status }}</span>
            </div>
          </div>
          <button type="button" @click="joinRoom(room.id)">加入</button>
        </div>
        <p v-if="!roomStore.rooms.length" class="muted">暂无公开房间。</p>
      </div>
    </PagePanel>
  </AppShell>
</template>
