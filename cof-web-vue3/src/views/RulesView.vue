<script setup lang="ts">
import AppShell from "@/components/AppShell.vue";
import PagePanel from "@/components/PagePanel.vue";
</script>

<template>
  <AppShell>
    <PagePanel title="规则说明">
      <div class="rules-grid">
        <section>
          <h3>基本流程</h3>
          <p>创建或加入房间，等待玩家到齐后开局；加载卡牌资源后进入对局，轮流点击自己的牌堆出牌。</p>
        </section>
        <section>
          <h3>按铃</h3>
          <p>当展示区顶牌出现同一 PMV 的匹配时，按中央铃抢牌；正确按铃收走展示区牌，错误按铃受罚。</p>
        </section>
        <section>
          <h3>淘汰与胜利</h3>
          <p>手牌出完且无法继续出牌时淘汰；最后存活或率先达成条件的玩家获胜。</p>
        </section>
        <section>
          <h3>房间选项</h3>
          <p>房主可设置人数、公开性、牌库、投票开局、空铃、随机牌背等；可邀请人机填充座位。</p>
        </section>
        <section>
          <h3>对局界面</h3>
          <p>环形座位显示各玩家未出/已出牌数；中央为铃与匹配提示；底部为日志与聊天。</p>
        </section>
        <section>
          <h3>卡牌与图鉴</h3>
          <p>主菜单可浏览卡牌图鉴与 PMV 索引；对局使用房间所选牌库资源。</p>
        </section>
      </div>
    </PagePanel>
  </AppShell>
</template>
