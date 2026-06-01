export function fmtNum(value: number | undefined | null | unknown): string {
  const n = Number(value);
  if (!Number.isFinite(n)) return "-";
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}

/** 0–1 的小数转为百分比文案；无数据返回 "-" */
export function fmtPct(value: number | undefined | null | unknown): string {
  const n = Number(value);
  if (!Number.isFinite(n)) return "-";
  const percent = n * 100;
  if (percent >= 99.995 && percent <= 100.005) return "100%";
  if (percent <= 0.005 && percent >= -0.005) return "0%";
  const rounded = Math.round(percent * 100) / 100;
  const text = Number.isInteger(rounded)
    ? String(rounded)
    : rounded.toFixed(2).replace(/\.?0+$/, "");
  return `${text}%`;
}

/** 排行榜：无对局/无按铃时显示 "-" */
export function fmtLeaderboardRate(
  value: number | undefined | null | unknown,
  gamesPlayed?: number | null,
  rings?: number | null,
): string {
  const games = Number(gamesPlayed);
  const ringCount = Number(rings);
  if (Number.isFinite(games) && games <= 0) return "-";
  if (Number.isFinite(ringCount) && ringCount <= 0 && value !== undefined && value !== null) {
    return "-";
  }
  return fmtPct(value);
}

export function formatDate(value: unknown): string {
  if (value === null || value === undefined) return "-";
  const date = new Date(Number(value));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString("zh-CN", { hour12: false });
}

export function isGodComputer(computer: {
  id?: string;
  name?: string;
  clientId?: string;
  username?: string;
  computerId?: string;
}): boolean {
  const id = computer.id ?? computer.clientId ?? computer.computerId;
  const name = computer.name ?? computer.username;
  return (
    id === "computer_god" ||
    String(name || "")
      .trim()
      .toUpperCase() === "GOD"
  );
}

export function libraryCopyLimit(lib: { cardCount?: number }): number {
  const cardCount = Math.max(1, Number(lib.cardCount) || 1);
  return Math.max(1, Math.floor(120 / cardCount));
}
