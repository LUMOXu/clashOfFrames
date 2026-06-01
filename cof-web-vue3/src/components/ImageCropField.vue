<script setup lang="ts">
import Cropper from "cropperjs";
import "cropperjs/dist/cropper.css";
import { onBeforeUnmount, ref, watch } from "vue";

const props = defineProps<{
  label?: string;
  aspectRatio?: number;
}>();

const fileInput = ref<HTMLInputElement | null>(null);
const imageEl = ref<HTMLImageElement | null>(null);
const containerEl = ref<HTMLDivElement | null>(null);
const previewUrl = ref<string | null>(null);
let cropper: Cropper | null = null;

function destroyCropper(): void {
  cropper?.destroy();
  cropper = null;
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = URL.createObjectURL(file);
}

watch(previewUrl, async (url) => {
  destroyCropper();
  if (!url || !imageEl.value) return;
  imageEl.value.src = url;
  await new Promise((r) => requestAnimationFrame(r));
  if (!imageEl.value) return;
  cropper = new Cropper(imageEl.value, {
    aspectRatio: props.aspectRatio ?? 720 / 1087,
    viewMode: 1,
    autoCropArea: 0.9,
    responsive: true,
    background: false,
  });
});

onBeforeUnmount(() => {
  destroyCropper();
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
});

function pickFile(): void {
  fileInput.value?.click();
}

function getCroppedBlob(): Promise<{ blob: Blob; crop: { x: number; y: number; width: number; height: number } }> {
  return new Promise((resolve, reject) => {
    if (!cropper) {
      reject(new Error("请先选择图片"));
      return;
    }
    const data = cropper.getData(true);
    const canvas = cropper.getCroppedCanvas({
      width: 720,
      height: 1087,
      imageSmoothingEnabled: true,
      imageSmoothingQuality: "high",
    });
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error("裁剪失败"));
          return;
        }
        resolve({
          blob,
          crop: {
            x: data.x,
            y: data.y,
            width: data.width,
            height: data.height,
          },
        });
      },
      "image/jpeg",
      0.92,
    );
  });
}

defineExpose({ pickFile, getCroppedBlob, hasImage: () => Boolean(cropper) });
</script>

<template>
  <div class="crop-field">
    <label v-if="label" class="crop-label">{{ label }}</label>
    <input ref="fileInput" type="file" accept="image/*" class="hidden-input" @change="onFileChange" />
    <button type="button" class="action-link" @click="pickFile">选择图片</button>
    <div v-show="previewUrl" ref="containerEl" class="crop-container">
      <img ref="imageEl" alt="crop" class="crop-source" />
    </div>
    <p class="muted crop-hint">拖动与缩放选区，提交后服务端会统一为 720×1087 JPEG（约 100KB）。</p>
  </div>
</template>

<style scoped>
.crop-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.hidden-input {
  display: none;
}
.crop-container {
  max-height: 420px;
  width: 100%;
  background: #111;
}
.crop-source {
  display: block;
  max-width: 100%;
}
.crop-hint {
  font-size: 0.85rem;
  margin: 0;
}
</style>
