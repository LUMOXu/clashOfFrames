<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from "vue";
import * as roomsApi from "@/api/rooms";
import type { RoomChatMessage } from "@/types/api";

const props = defineProps<{
  roomId: string;
  messages: RoomChatMessage[];
  variant?: "default" | "game";
}>();

const emit = defineEmits<{
  sent: [];
}>();

const draft = ref("");
const messagesEl = ref<HTMLElement | null>(null);

function formatChatLine(msg: RoomChatMessage): string {
  const time = new Date(msg.at).toLocaleTimeString("zh-CN", { hour12: false });
  return `[${time}][${msg.username}]${msg.text}`;
}

async function scrollToLatest(): Promise<void> {
  await nextTick();
  const el = messagesEl.value;
  if (el) {
    el.scrollTop = el.scrollHeight;
  }
}

watch(
  () => props.messages,
  () => {
    void scrollToLatest();
  },
  { deep: true, immediate: true },
);

onMounted(() => {
  void scrollToLatest();
});

async function submit(): Promise<void> {
  const text = draft.value.trim();
  if (!text || !props.roomId) return;
  await roomsApi.postChat(props.roomId, text);
  draft.value = "";
  emit("sent");
}
</script>

<template>
  <section class="chat-area" :class="{ 'game-chat': variant === 'game' }">
    <h3 v-if="variant !== 'game'">聊天</h3>
    <div ref="messagesEl" class="chat-messages">
      <div v-if="!messages.length" class="muted">暂无聊天。</div>
      <div v-for="(msg, i) in messages" :key="`${msg.at}-${msg.clientId}-${i}`" class="chat-line">
        {{ formatChatLine(msg) }}
      </div>
    </div>
    <form class="chat-form" @submit.prevent="submit">
      <input v-model="draft" maxlength="40" autocomplete="off" placeholder="最多 40 字" />
      <button type="submit">发送</button>
    </form>
  </section>
</template>
