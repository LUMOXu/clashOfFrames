<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import PlayerName from "@/components/PlayerName.vue";

defineProps<{ username: string; confirming?: boolean }>();

const emit = defineEmits<{ confirm: [] }>();
const seconds = ref(10);
let timer: number | undefined;
const buttonText = computed(() => (seconds.value > 0 ? `收下（${seconds.value} 秒后可点击）` : "收下"));

onMounted(() => {
  timer = window.setInterval(() => {
    if (seconds.value > 0) seconds.value -= 1;
    if (seconds.value <= 0 && timer !== undefined) window.clearInterval(timer);
  }, 1000);
});

onUnmounted(() => {
  if (timer !== undefined) window.clearInterval(timer);
});
</script>

<template>
  <div class="god-reward-modal" role="dialog" aria-modal="true" aria-labelledby="god-slayer-title">
    <section class="god-reward-panel">
      <h2 id="god-slayer-title">恭喜！！！</h2>
      <p>你击败了帧封相对之神，胜天至少三子！以后，你的名字会以更醒目的样式出现在所有玩家列表和对局里！</p>
      <p>
        请截图这个页面发给页面下方的作者，领取<strong>弑神纪念奖励——一份CSBC'25的PMV静帧扑克牌</strong>！
      </p>
      <p class="god-reward-username">你的用户名：<PlayerName :username="username" god-slayer /></p>
      <button class="primary" :disabled="seconds > 0 || confirming" @click="emit('confirm')">
        {{ confirming ? "确认中……" : buttonText }}
      </button>
    </section>
  </div>
</template>
