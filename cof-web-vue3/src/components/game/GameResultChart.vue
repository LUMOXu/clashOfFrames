<script setup lang="ts">
import { LineChart } from "echarts/charts";
import { GridComponent } from "echarts/components";
import * as echarts from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { computed, onMounted, onUnmounted, shallowRef, useTemplateRef, watch } from "vue";
import type { PublicGame } from "@/types/api";
import { buildResultChartOption, getResultChartData } from "@/utils/resultChart";
import { isGodComputer } from "@/utils/format";

echarts.use([LineChart, GridComponent, CanvasRenderer]);

const props = defineProps<{
  game: PublicGame;
  progressX: number;
}>();

const chartHost = useTemplateRef<HTMLDivElement>("chartHost");
const chartInstance = shallowRef<echarts.ECharts | null>(null);

const seriesMeta = computed(() => getResultChartData(props.game, props.progressX)?.series ?? []);

function renderChart(): void {
  const host = chartHost.value;
  const instance = chartInstance.value;
  if (!host || !instance) return;

  const option = buildResultChartOption(props.game, props.progressX);
  if (!option) {
    instance.clear();
    return;
  }
  instance.setOption(option, { notMerge: true, lazyUpdate: true });
}

function resizeChart(): void {
  chartInstance.value?.resize();
}

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  const host = chartHost.value;
  if (!host) return;

  chartInstance.value = echarts.init(host, undefined, { renderer: "canvas" });
  renderChart();

  resizeObserver = new ResizeObserver(() => resizeChart());
  resizeObserver.observe(host);
});

watch(
  () => [props.game.id, props.progressX, props.game.resultInfo?.counts],
  () => renderChart(),
);

onUnmounted(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
  chartInstance.value?.dispose();
  chartInstance.value = null;
});
</script>

<template>
  <div v-if="seriesMeta.length" class="result-chart">
    <div ref="chartHost" class="result-chart-host" role="img" aria-label="剩余牌数折线图" />
    <div class="result-legend">
      <span
        v-for="series in seriesMeta"
        :key="`legend-${series.clientId}`"
        class="result-legend-item"
        :class="{ winner: series.winner }"
      >
        <span class="result-swatch" :style="{ background: series.color }" />
        <span
          :class="{
            'god-name': isGodComputer({ id: series.clientId, name: series.username }),
          }"
        >
          {{ series.username }}
        </span>
      </span>
    </div>
  </div>
</template>
