<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useLobbyStore } from "@/stores/lobbyStore";
import { useRoomStore } from "@/stores/roomStore";
import { recordId, recordName } from "@/utils/record";

const lobby = useLobbyStore();
const roomStore = useRoomStore();
const router = useRouter();
const selectedComputers = ref<string[]>([]);

onMounted(() => {
  void lobby.loadMeta();
});

async function create(): Promise<void> {
  const room = await roomStore.createRoom(undefined, selectedComputers.value);
  await router.push({ name: "waiting", params: { roomId: room.id } });
}
</script>

<template>
  <AppShell title="创建房间">
    <PagePanel title="创建房间">
      <p class="muted">配置房间并邀请玩家（功能壳，后续对接完整设置）。</p>
      <div v-if="lobby.loadingMeta">加载元数据...</div>
      <div v-else class="computer-list">
        <label
          v-for="(player, index) in lobby.computerPlayers"
          :key="recordId(player, index)"
          class="computer-row"
        >
          <span>{{ recordName(player, `电脑 ${index + 1}`) }}</span>
          <input v-model="selectedComputers" type="checkbox" :value="recordId(player, index)" />
        </label>
      </div>
      <div class="actions">
        <button class="primary" type="button" :disabled="roomStore.loading" @click="create">创建并进入等待室</button>
      </div>
    </PagePanel>
  </AppShell>
</template>
