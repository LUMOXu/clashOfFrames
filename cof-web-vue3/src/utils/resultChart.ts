import type { EChartsOption } from "echarts";
import type { PublicGame } from "@/types/api";

export const RESULT_REPLAY_RATE = 20;
export const RESULT_CHART_COLORS = [
  "#f3d775",
  "#54c4a8",
  "#ee6b72",
  "#8ab6ff",
  "#c58cff",
  "#ffad66",
  "#72d37d",
  "#f08ec2",
];

export interface ChartPoint {
  x: number;
  y: number;
}

export interface ChartSeries {
  clientId: string;
  username: string;
  index: number;
  winner: boolean;
  color: string;
  points: ChartPoint[];
  path: string;
}

export interface ResultChartModel {
  width: number;
  height: number;
  pad: { left: number; right: number; top: number; bottom: number };
  xMax: number;
  yMax: number;
  series: ChartSeries[];
  gridLines: { x1: number; y1: number; x2: number; y2: number }[];
  xLabels: { x: number; y: number; text: string }[];
  yLabels: { x: number; y: number; text: string }[];
  plotLeft: number;
  plotRight: number;
  plotTop: number;
  plotBottom: number;
}

export function resultChartXMax(game: PublicGame): number {
  const rowMax = Math.max(0, (game.resultInfo?.counts?.length ?? 0) - 1);
  return Math.max(0, Number(game.playCount) || rowMax);
}

export function resultReplayProgress(game: PublicGame, startedAt: number, now = Date.now()): number {
  const xMax = resultChartXMax(game);
  if (xMax <= 0) return 0;
  const elapsedSeconds = Math.max(0, now - startedAt) / 1000;
  return Math.min(xMax, elapsedSeconds * RESULT_REPLAY_RATE);
}

export function canContinueAfterResultReplay(
  game: PublicGame,
  startedAt: number,
  now = Date.now(),
): boolean {
  if (!game.resultInfo?.counts?.length) return true;
  return resultReplayProgress(game, startedAt, now) >= resultChartXMax(game);
}

export function clippedLinePoints(points: ChartPoint[], progressX: number): ChartPoint[] {
  if (!points.length) return [];
  if (progressX < points[0].x) return [];
  const out: ChartPoint[] = [];
  for (let index = 0; index < points.length; index += 1) {
    const point = points[index];
    if (point.x <= progressX) {
      out.push(point);
      continue;
    }
    const previous = points[index - 1];
    if (previous && previous.x < progressX) {
      const span = point.x - previous.x || 1;
      const ratio = (progressX - previous.x) / span;
      out.push({
        x: progressX,
        y: previous.y + (point.y - previous.y) * ratio,
      });
    }
    break;
  }
  return out;
}

function chartColor(index: number, winner: boolean): string {
  return winner ? "#f3d775" : RESULT_CHART_COLORS[index % RESULT_CHART_COLORS.length];
}

const AXIS_LABEL_COLOR = "rgba(255, 255, 255, 0.62)";
const GRID_LINE_COLOR = "rgba(255, 255, 255, 0.1)";
const AXIS_LINE_COLOR = "rgba(255, 255, 255, 0.42)";

export interface ResultChartData {
  xMax: number;
  yMax: number;
  series: ChartSeries[];
}

export function getResultChartData(game: PublicGame, progressX = resultChartXMax(game)): ResultChartData | null {
  const counts = game.resultInfo?.counts;
  const players = game.resultInfo?.players as { clientId: string; username: string }[] | undefined;
  if (!counts?.length || !players?.length) return null;

  const xMax = resultChartXMax(game);
  const maxCount = counts.reduce(
    (max, row) => Math.max(max, ...row.map((value) => Number(value) || 0)),
    0,
  );
  const yMax = Math.max(1, maxCount);

  const series: ChartSeries[] = players.map((player, playerIndex) => {
    const rawPoints = counts.map((row, rowIndex) => ({
      x: rowIndex,
      y: Number(row[playerIndex]) || 0,
    }));
    const winner = player.clientId === game.winnerId;
    const points = clippedLinePoints(rawPoints, progressX);
    return {
      clientId: player.clientId,
      username: player.username,
      index: playerIndex,
      winner,
      color: chartColor(playerIndex, winner),
      points,
      path: "",
    };
  });

  return { xMax, yMax, series };
}

export function buildResultChartOption(game: PublicGame, progressX = resultChartXMax(game)): EChartsOption | null {
  const data = getResultChartData(game, progressX);
  if (!data) return null;

  const { xMax, yMax, series } = data;
  const xTicks = [...new Set([0, Math.floor(xMax / 2), xMax])];
  const yTicks = [...new Set([0, Math.ceil(yMax / 2), yMax])];

  return {
    animation: false,
    backgroundColor: "rgba(8, 11, 8, 0.42)",
    grid: { left: 46, right: 18, top: 18, bottom: 34, containLabel: false },
    xAxis: {
      type: "value",
      min: 0,
      max: xMax,
      axisLine: { lineStyle: { color: AXIS_LINE_COLOR, width: 1.4 } },
      axisLabel: {
        color: AXIS_LABEL_COLOR,
        fontSize: 12,
        formatter: (value: number) => (xTicks.includes(value) ? String(value) : ""),
      },
      splitLine: { lineStyle: { color: GRID_LINE_COLOR, width: 1 } },
    },
    yAxis: {
      type: "value",
      min: 0,
      max: yMax,
      axisLine: { show: true, lineStyle: { color: AXIS_LINE_COLOR, width: 1.4 } },
      axisLabel: {
        color: AXIS_LABEL_COLOR,
        fontSize: 12,
        formatter: (value: number) => (yTicks.includes(value) ? String(value) : ""),
      },
      splitLine: { lineStyle: { color: GRID_LINE_COLOR, width: 1 } },
    },
    series: series.map((item) => ({
      name: item.username,
      type: "line",
      showSymbol: false,
      animation: false,
      data: item.points.map((point) => [point.x, point.y]),
      lineStyle: {
        color: item.color,
        width: item.winner ? 4 : 2.4,
        shadowColor: item.winner ? "rgba(243, 215, 117, 0.55)" : undefined,
        shadowBlur: item.winner ? 8 : 0,
      },
      emphasis: { disabled: true },
    })),
  };
}

function chartPoint(
  point: ChartPoint,
  model: Pick<ResultChartModel, "width" | "height" | "pad" | "xMax" | "yMax">,
): { x: number; y: number } {
  const plotWidth = model.width - model.pad.left - model.pad.right;
  const plotHeight = model.height - model.pad.top - model.pad.bottom;
  const x = model.pad.left + (model.xMax > 0 ? point.x / model.xMax : 0) * plotWidth;
  const y = model.pad.top + (1 - (model.yMax > 0 ? point.y / model.yMax : 0)) * plotHeight;
  return { x, y };
}

function linePath(
  points: ChartPoint[],
  model: Pick<ResultChartModel, "width" | "height" | "pad" | "xMax" | "yMax">,
): string {
  return points
    .map((point, index) => {
      const plotted = chartPoint(point, model);
      return `${index === 0 ? "M" : "L"} ${plotted.x.toFixed(2)} ${plotted.y.toFixed(2)}`;
    })
    .join(" ");
}

export function buildResultChartModel(game: PublicGame, progressX = resultChartXMax(game)): ResultChartModel | null {
  const data = getResultChartData(game, progressX);
  if (!data) return null;

  const base = {
    width: 640,
    height: 260,
    pad: { left: 46, right: 18, top: 18, bottom: 34 },
    xMax: data.xMax,
    yMax: data.yMax,
  };

  const series: ChartSeries[] = data.series.map((item) => ({
    ...item,
    path: linePath(item.points, base),
  }));

  const plotLeft = base.pad.left;
  const plotRight = base.width - base.pad.right;
  const plotTop = base.pad.top;
  const plotBottom = base.height - base.pad.bottom;
  const xTicks = [...new Set([0, Math.floor(data.xMax / 2), data.xMax])];
  const yTicks = [...new Set([0, Math.ceil(base.yMax / 2), base.yMax])];

  const gridLines: ResultChartModel["gridLines"] = [];
  const xLabels: ResultChartModel["xLabels"] = [];
  const yLabels: ResultChartModel["yLabels"] = [];

  for (const tick of xTicks) {
    const point = chartPoint({ x: tick, y: 0 }, base);
    gridLines.push({ x1: point.x, y1: plotTop, x2: point.x, y2: plotBottom });
    xLabels.push({ x: point.x, y: base.height - 8, text: String(tick) });
  }
  for (const tick of yTicks) {
    const point = chartPoint({ x: 0, y: tick }, base);
    gridLines.push({ x1: plotLeft, y1: point.y, x2: plotRight, y2: point.y });
    yLabels.push({ x: 8, y: point.y + 4, text: String(tick) });
  }

  return {
    ...base,
    series,
    gridLines,
    xLabels,
    yLabels,
    plotLeft,
    plotRight,
    plotTop,
    plotBottom,
  };
}
