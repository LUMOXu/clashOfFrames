<script setup lang="ts">
import { computed } from "vue";
import * as roomsApi from "@/api/rooms";
import { useLobbyStore } from "@/stores/lobbyStore";
import { useRoomStore } from "@/stores/roomStore";
import type { RoomSummary } from "@/types/api";
import type { ComputerPlayer } from "@/types/computer";
import { fmtNum, fmtPct, isGodComputer } from "@/utils/format";

const props = defineProps<{
  room: RoomSummary;
}>();

const lobby = useLobbyStore();
const roomStore = useRoomStore();

const computers = computed(() => lobby.computerPlayers as ComputerPlayer[]);

const presentComputerIds = computed(() => {
  const ids = new Set<string>();
  for (const p of props.room.playerDetails || []) {
    if (p.isComputer && p.computerId) {
      ids.add(p.computerId);
    }
    if (p.isComputer && p.clientId.startsWith("computer:")) {
      ids.add(p.clientId.slice(p.clientId.lastIndexOf(":") + 1));
    }
  }
  return ids;
});

const roomFull = computed(
  () => (props.room.players?.length ?? 0) >= (props.room.settings?.maxPlayers ?? 8),
);

async function toggle(computer: ComputerPlayer, inRoom: boolean): Promise<void> {
  if (!props.room.id) return;
  const result = inRoom
    ? await roomsApi.removeComputer(props.room.id, computer.id)
    : await roomsApi.addComputer(props.room.id, computer.id);
  roomStore.applyRoomUpdate(result.room);
}
</script>

<template>
  <section v-if="computers.length" class="panel computer-manager">
    <h3>人机玩家</h3>
    <p class="muted computer-manager-hint">邀请后该 AI 将使用下列参数参与对局。</p>
    <div class="computer-list">
      <div
        v-for="computer in computers"
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
        <button
          type="button"
          :class="presentComputerIds.has(computer.id) ? '' : 'primary'"
          :disabled="!presentComputerIds.has(computer.id) && roomFull"
          @click="toggle(computer, presentComputerIds.has(computer.id))"
        >
          {{ presentComputerIds.has(computer.id) ? "移除" : "邀请" }}
        </button>
      </div>
    </div>
  </section>
</template>
