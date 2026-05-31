<script setup lang="ts">
import { computed } from "vue";
import type { ComputerPlayer } from "@/types/computer";
import { fmtNum, fmtPct, isGodComputer } from "@/utils/format";

const props = defineProps<{
  computers: ComputerPlayer[];
  presentIds: Set<string>;
  roomFull: boolean;
}>();

const emit = defineEmits<{
  invite: [computer: ComputerPlayer];
}>();

const available = computed(() =>
  props.computers.filter((c) => !props.presentIds.has(c.id)),
);
</script>

<template>
  <div v-if="!computers.length" class="muted">暂无可用人机配置。</div>
  <div v-else-if="!available.length" class="muted">所有人机已在房间中。</div>
  <div v-else class="computer-list computer-picker-list">
    <div
      v-for="computer in available"
      :key="computer.id"
      class="computer-row"
      :class="{ 'god-computer': isGodComputer(computer) }"
    >
      <div class="computer-info">
        <strong :class="{ 'god-name': isGodComputer(computer) }">{{ computer.name }}</strong>
        <div v-if="computer.description" class="muted">{{ computer.description }}</div>
        <div class="computer-stats">
          <span>
            出牌
            <strong>{{ fmtNum(computer.playDelayMeanSeconds) }}±{{ fmtNum(computer.playDelayStdSeconds) }}s</strong>
          </span>
          <span>
            反应
            <strong>{{ fmtNum(computer.reactionMeanSeconds) }}±{{ fmtNum(computer.reactionStdSeconds) }}s</strong>
          </span>
          <span>发现 <strong>{{ fmtPct(computer.matchDetectionProbability) }}</strong></span>
          <span>错按 <strong>{{ fmtPct(computer.falseRingProbability) }}</strong></span>
        </div>
      </div>
      <button class="primary" type="button" :disabled="roomFull" @click="emit('invite', computer)">
        邀请
      </button>
    </div>
  </div>
</template>
