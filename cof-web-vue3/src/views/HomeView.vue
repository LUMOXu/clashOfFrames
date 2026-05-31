<script setup lang="ts">
import { RouterLink } from "vue-router";
import AppShell from "@/components/AppShell.vue";
import { useRoomStore } from "@/stores/roomStore";

const roomStore = useRoomStore();
</script>

<template>
  <AppShell>
    <main class="page">
      <section class="panel">
        <h2>主菜单</h2>
        <p v-if="roomStore.currentRoom" class="status-line">
          当前房间：{{ roomStore.currentRoom.id }}，状态：{{ roomStore.currentRoom.status }}
        </p>
        <div class="game-intro">
          PMV德国心脏病——点击牌堆出牌，如果观察到翻开的牌有两张来自一个PMV，立刻按铃！
        </div>
        <div class="menu-grid">
          <RouterLink :to="{ name: 'create-room' }"><button class="primary" type="button">创建房间</button></RouterLink>
          <RouterLink :to="{ name: 'join-room' }"><button type="button">加入房间</button></RouterLink>
          <RouterLink :to="{ name: 'rooms' }"><button type="button">查看房间</button></RouterLink>
          <RouterLink :to="{ name: 'rules' }"><button type="button">规则说明</button></RouterLink>
          <RouterLink :to="{ name: 'profile' }"><button type="button">个人战绩</button></RouterLink>
          <RouterLink :to="{ name: 'leaderboard' }"><button type="button">排行榜</button></RouterLink>
          <RouterLink :to="{ name: 'card-select' }"><button type="button">卡牌图鉴</button></RouterLink>
          <RouterLink :to="{ name: 'pmv-index' }"><button type="button">PMV 索引</button></RouterLink>
        </div>
      </section>
    </main>
  </AppShell>
</template>
