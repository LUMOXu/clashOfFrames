<script setup lang="ts">
import type { GameSettings } from "@/types/api";

defineProps<{
  settings: GameSettings;
  showVote?: boolean;
  showAdvanced?: boolean;
}>();
</script>

<template>
  <div class="room-options-form grid">
    <div class="form-grid panel">
      <label>
        房间最小人数
        <input v-model.number="settings.minPlayers" type="number" min="2" max="8" />
      </label>
      <label>
        房间最大人数
        <input v-model.number="settings.maxPlayers" type="number" min="2" max="8" />
      </label>
    </div>

    <label class="toggle-row panel">
      <span>公开房间</span>
      <input v-model="settings.isPublic" type="checkbox" />
    </label>

    <section v-if="showVote" class="panel form-grid">
      <h3>投票开始</h3>
      <label>
        投票开始阈值
        <select v-model="settings.startVoteThresholdMode">
          <option value="auto">自动（当前人数 - 2）</option>
          <option value="manual">手动</option>
        </select>
      </label>
      <label>
        手动阈值
        <input
          v-model.number="settings.startVoteThreshold"
          type="number"
          min="1"
          max="8"
          :disabled="settings.startVoteThresholdMode !== 'manual'"
        />
      </label>
    </section>

    <section v-if="showAdvanced" class="panel form-grid advanced-settings">
      <h3>高级选项</h3>
      <label class="toggle-row">
        <span>卡牌耗尽依然可拍铃</span>
        <input v-model="settings.allowEmptyBell" type="checkbox" />
      </label>
      <label class="toggle-row">
        <span>随机卡背颜色</span>
        <input v-model="settings.randomBacks" type="checkbox" />
      </label>
      <label class="toggle-row">
        <span>解决拍铃与出牌冲突</span>
        <input v-model="settings.conflictResolution" type="checkbox" />
      </label>
      <label class="toggle-row">
        <span>断线保护</span>
        <input v-model="settings.disconnectProtection" type="checkbox" />
      </label>
    </section>
  </div>
</template>
