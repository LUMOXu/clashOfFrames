<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
import { useRoomStore } from "@/stores/roomStore";

const roomId = ref("");
const roomStore = useRoomStore();
const router = useRouter();

async function join(): Promise<void> {
  await roomStore.joinRoom(roomId.value.trim());
  await router.push({ name: "waiting", params: { roomId: roomId.value.trim() } });
}
</script>

<template>
  <AppShell>
    <PagePanel title="加入房间" narrow>
      <label>房间 ID
        <input v-model="roomId" required placeholder="输入房间号" />
      </label>
      <div class="actions">
        <button class="primary" type="button" :disabled="!roomId.trim() || roomStore.loading" @click="join">
          加入
        </button>
      </div>
    </PagePanel>
  </AppShell>
</template>
