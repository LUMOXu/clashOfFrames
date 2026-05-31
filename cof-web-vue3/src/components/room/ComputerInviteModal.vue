<script setup lang="ts">
import { computed } from "vue";
import ComputerPickerList from "@/components/room/ComputerPickerList.vue";
import { useLobbyStore } from "@/stores/lobbyStore";
import type { RoomSummary } from "@/types/api";
import type { ComputerPlayer } from "@/types/computer";
import { resolveComputerId } from "@/utils/computerPlayer";

const open = defineModel<boolean>("open", { default: false });

const props = defineProps<{
  room: RoomSummary;
}>();

const emit = defineEmits<{
  invite: [computer: ComputerPlayer];
}>();

const lobby = useLobbyStore();

const computers = computed(() => lobby.computerPlayers as ComputerPlayer[]);

const presentIds = computed(() => {
  const ids = new Set<string>();
  for (const p of props.room.playerDetails || []) {
    const id = resolveComputerId(p);
    if (id) ids.add(id);
  }
  return ids;
});

const roomFull = computed(
  () => (props.room.players?.length ?? 0) >= (props.room.settings?.maxPlayers ?? 8),
);

function close(): void {
  open.value = false;
}

function onInvite(computer: ComputerPlayer): void {
  emit("invite", computer);
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop" @click.self="close">
      <section class="panel computer-invite-modal" role="dialog" aria-labelledby="computer-invite-title">
        <header class="computer-invite-header">
          <div>
            <h3 id="computer-invite-title">邀请人机</h3>
            <p class="muted computer-manager-hint">邀请后该 AI 将使用下列参数参与对局。</p>
          </div>
          <button type="button" class="ghost modal-close" aria-label="关闭" @click="close">×</button>
        </header>
        <ComputerPickerList
          :computers="computers"
          :present-ids="presentIds"
          :room-full="roomFull"
          @invite="onInvite"
        />
      </section>
    </div>
  </Teleport>
</template>
