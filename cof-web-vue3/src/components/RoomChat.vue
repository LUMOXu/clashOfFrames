<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from "vue";
import * as roomsApi from "@/api/rooms";
import type { RoomChatMessage } from "@/types/api";
import type { PlayerIdentity } from "@/utils/playerName";
import PlayerName from "@/components/PlayerName.vue";

const props = defineProps<{
  roomId: string;
  messages: RoomChatMessage[];
  variant?: "default" | "game" | "waiting";
  players?: Array<PlayerIdentity & { clientId?: string }>;
}>();

const emit = defineEmits<{
  sent: [];
}>();

const draft = ref("");
const messagesEl = ref<HTMLElement | null>(null);

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

function sender(clientId: string, username: string): PlayerIdentity {
  return props.players?.find((player) => "clientId" in player && player.clientId === clientId) ?? { username };
}
</script>

<template>
  <section
    class="chat-area"
    :class="{ 'game-chat': variant === 'game', 'waiting-chat-panel': variant === 'waiting' }"
  >
    <header v-if="variant === 'waiting'" class="chat-panel-header">
      <h3>聊天</h3>
    </header>
    <h3 v-else-if="variant !== 'game'">聊天</h3>
    <div ref="messagesEl" class="chat-messages">
      <p v-if="!messages.length" class="chat-empty muted">暂无聊天。</p>
      <div v-for="(msg, i) in messages" :key="`${msg.at}-${msg.clientId}-${i}`" class="chat-line">
        <template v-if="variant === 'waiting'">
          <span class="chat-time">{{ new Date(msg.at).toLocaleTimeString("zh-CN", { hour12: false }) }}</span>
          <span class="chat-user"><PlayerName v-bind="sender(msg.clientId, msg.username)" /></span>
          <span class="chat-text">{{ msg.text }}</span>
        </template>
        <template v-else>
          [{{ new Date(msg.at).toLocaleTimeString("zh-CN", { hour12: false }) }}][<PlayerName
            v-bind="sender(msg.clientId, msg.username)"
          />]{{ msg.text }}
        </template>
      </div>
    </div>
    <form class="chat-form" @submit.prevent="submit">
      <input v-model="draft" maxlength="40" autocomplete="off" placeholder="最多 40 字" />
      <button class="primary" type="submit">发送</button>
    </form>
  </section>
</template>
