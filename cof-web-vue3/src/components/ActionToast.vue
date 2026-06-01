<script setup lang="ts">
import { onMounted, ref } from "vue";

defineProps<{
  message: string;
  x: number;
  y: number;
  variant?: "error" | "success";
}>();

const emit = defineEmits<{ close: [] }>();
const visible = ref(true);

onMounted(() => {
  window.setTimeout(() => {
    visible.value = false;
    emit("close");
  }, 4500);
});
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="action-toast"
      :class="variant === 'success' ? 'action-toast--success' : 'action-toast--error'"
      :style="{ left: `${x}px`, top: `${y}px` }"
      role="alert"
    >
      {{ message }}
    </div>
  </Teleport>
</template>

<style scoped>
.action-toast {
  position: fixed;
  z-index: 9999;
  max-width: min(320px, 90vw);
  padding: 0.65rem 0.85rem;
  border-radius: 8px;
  font-size: 0.9rem;
  line-height: 1.35;
  transform: translate(-50%, -100%) translateY(-8px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
  pointer-events: none;
}
.action-toast--error {
  background: #3d1515;
  color: #ffc9c9;
  border: 1px solid #a33;
}
.action-toast--success {
  background: #153d22;
  color: #c9ffd4;
  border: 1px solid #3a6;
}
</style>
