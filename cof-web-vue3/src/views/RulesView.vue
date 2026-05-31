<script setup lang="ts">
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
</script>

<template>
  <AppShell>
    <PagePanel title="规则说明">
      <div class="rules-grid">
        <section>
          <h3>基本玩法</h3>
          <p>轮流从自己的牌堆翻牌到中央展示区；当展示区出现同一 PMV 的两张牌时，任意玩家可按铃抢牌。</p>
        </section>
        <section>
          <h3>胜利条件</h3>
          <p>率先出完手牌并正确按铃的玩家获胜；误铃会受到惩罚。</p>
        </section>
        <section>
          <h3>在线房间</h3>
          <p>房主可配置牌库、电脑玩家与开局投票；全员加载完成后进入对局。</p>
        </section>
      </div>
    </PagePanel>
  </AppShell>
</template>
