<script setup lang="ts">
import { watch } from "vue";
import { RouterView } from "vue-router";
import { useAuthStore } from "@/stores/authStore";
import { useGameStore } from "@/stores/gameStore";
import { useRoomStore } from "@/stores/roomStore";
import { useAutoRoute } from "@/composables/useAutoRoute";

const auth = useAuthStore();
const gameStore = useGameStore();
const roomStore = useRoomStore();

useAutoRoute();

gameStore.onRoomUpdate((room) => {
  roomStore.applyRoomUpdate(room);
});

watch(
  () => auth.token,
  (token) => {
    if (token) {
      gameStore.connectSocket(token);
      void auth.refreshBootstrap();
    } else {
      gameStore.disconnectSocket();
      gameStore.clearGame();
      roomStore.clearRoom();
    }
  },
  { immediate: true },
);
</script>

<template>
  <RouterView />
</template>
