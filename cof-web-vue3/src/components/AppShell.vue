<script setup lang="ts">
import { RouterLink, useRoute } from "vue-router";
import { useAuthStore } from "@/stores/authStore";

defineProps<{
  title?: string;
  immersive?: boolean;
}>();

const route = useRoute();
const auth = useAuthStore();

async function onLogout(): Promise<void> {
  await auth.logout();
}
</script>

<template>
  <div class="app" :class="{ 'app-immersive': immersive }">
    <header class="topbar">
      <div class="brand">
        <strong>帧封相对</strong>
        <span v-if="auth.player">{{ auth.player.username }}</span>
      </div>
      <div class="top-actions">
        <RouterLink v-if="route.name !== 'home'" :to="{ name: 'home' }">
          <button type="button">主菜单</button>
        </RouterLink>
        <button type="button" class="ghost" @click="onLogout">退出</button>
      </div>
    </header>
    <slot />
    <footer v-if="!immersive" class="app-footer">
      Version 1.1, Built by LUMO_Xu &amp; DrowningYu with good vibes.
    </footer>
  </div>
</template>
