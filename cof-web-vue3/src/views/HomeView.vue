<script setup lang="ts">
import { RouterLink, useRouter } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import * as roomsApi from "@/api/rooms";
import { useGameStore } from "@/stores/gameStore";
import { useRoomStore } from "@/stores/roomStore";

const roomStore = useRoomStore();
const gameStore = useGameStore();
const router = useRouter();

function statusText(status?: string): string {
  const map: Record<string, string> = {
    waiting: "等待中",
    loading: "加载中",
    playing: "游戏中",
    finished: "已结束",
  };
  return (status && map[status]) || status || "";
}

function returnToRoom(): void {
  const room = roomStore.currentRoom;
  if (!room) return;
  const name =
    room.status === "playing" ? "game" : room.status === "loading" ? "loading" : "waiting";
  void router.push({
    name,
    params: { roomId: room.id },
    query: room.gameId ? { gameId: room.gameId } : undefined,
  });
}

async function leaveRoom(): Promise<void> {
  const room = roomStore.currentRoom;
  if (!room) return;
  try {
    await roomsApi.leaveRoom(room.id);
    roomStore.clearRoom();
    gameStore.clearGame();
  } catch (error) {
    roomStore.message = error instanceof Error ? error.message : "退出房间失败";
  }
}
</script>

<template>
  <AppShell>
    <main class="page home-page">
      <section class="panel">
        <h2>主菜单</h2>
        <p v-if="roomStore.currentRoom" class="status-line">
          当前房间：{{ roomStore.currentRoom.id }}，状态：{{ statusText(roomStore.currentRoom.status) }}
        </p>
        <div class="game-intro">
          PMV德国心脏病——点击牌堆出牌，如果观察到翻开的牌有两张来自同一个PMV，立刻按铃！
        </div>
        <div class="menu-grid">
          <RouterLink class="menu-link" :to="{ name: 'create-room' }">
            <button class="primary" type="button">创建房间</button>
          </RouterLink>
          <RouterLink class="menu-link" :to="{ name: 'join-room' }">
            <button type="button">加入房间</button>
          </RouterLink>
          <RouterLink class="menu-link" :to="{ name: 'rooms' }">
            <button type="button">查看房间</button>
          </RouterLink>
          <RouterLink class="menu-link" :to="{ name: 'profile' }">
            <button type="button">个人信息</button>
          </RouterLink>
          <RouterLink class="menu-link" :to="{ name: 'leaderboard' }">
            <button type="button">排行榜</button>
          </RouterLink>
          <template v-if="roomStore.currentRoom">
            <button class="primary" type="button" @click="returnToRoom">返回当前房间</button>
            <button class="danger" type="button" @click="leaveRoom">退出房间</button>
          </template>
        </div>
      </section>

      <section class="panel menu-info-panel">
        <div>
          <h3>详细说明</h3>
          <p class="status-line">翻牌、按铃、淘汰与房间选项的完整说明，也包含网站各页面的功能介绍。</p>
        </div>
        <RouterLink class="menu-link" :to="{ name: 'rules' }">
          <button type="button">详细说明</button>
        </RouterLink>
      </section>

      <section class="panel menu-info-panel">
        <div>
          <h3>卡组资料</h3>
          <p class="status-line">不知道每张牌属于什么 PMV？打开这个页面吧。</p>
        </div>
        <div class="actions">
          <RouterLink class="menu-link" :to="{ name: 'card-select' }">
            <button type="button">查看卡牌</button>
          </RouterLink>
        </div>
      </section>

      <section class="panel menu-info-panel">
        <div>
          <h3>卡组提交</h3>
          <p class="status-line">想提交自己的卡组？请查看指南。</p>
        </div>
        <div class="actions">
          <RouterLink class="menu-link" :to="{ name: 'pmv-index' }">
            <button type="button">卡组提交指南</button>
          </RouterLink>
        </div>
      </section>
    </main>
  </AppShell>
</template>
