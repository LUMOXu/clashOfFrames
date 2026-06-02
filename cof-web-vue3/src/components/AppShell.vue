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
  if (!window.confirm("确定要退出登录吗？")) return;
  await auth.logout();
}
</script>

<template>
  <div class="app" :class="{ 'app-immersive': immersive }">
    <header class="topbar">
      <RouterLink class="brand" :to="{ name: 'home' }" aria-label="主菜单">
        <strong>帧封相对</strong>
        <span>Clash of Frames</span>
      </RouterLink>
      <div class="top-actions">
        <span v-if="auth.player" class="top-username">{{ auth.player.username }}</span>
        <RouterLink v-if="route.name !== 'home'" :to="{ name: 'home' }">
          <button type="button">主菜单</button>
        </RouterLink>
        <RouterLink v-if="route.name !== 'profile'" :to="{ name: 'profile' }">
          <button type="button">个人信息</button>
        </RouterLink>
        <button type="button" class="ghost" @click="onLogout">退出登录</button>
      </div>
    </header>
    <slot />
    <footer v-if="!immersive" class="app-footer">
      Version 1.1, Built by LUMO_Xu &amp; DrowningYu with good vibes.
    </footer>
  </div>
</template>
