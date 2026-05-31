export function fmtNum(value: number | undefined | null | unknown): string {
  const n = Number(value);
  if (!Number.isFinite(n)) return "-";
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}

export function fmtPct(value: number | undefined | null | unknown): string {
  const n = Number(value);
  if (!Number.isFinite(n)) return "-";
  const percent = n * 100;
  const rounded = Math.round(percent * 100) / 100;
  return `${String(rounded).replace(/\.?0+$/, "")}%`;
}

export function formatDate(value: unknown): string {
  if (value === null || value === undefined) return "-";
  const date = new Date(Number(value));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString("zh-CN", { hour12: false });
}

export function isGodComputer(computer: { id?: string; name?: string }): boolean {
  return (
    computer.id === "computer_god" ||
    String(computer.name || "")
      .trim()
      .toUpperCase() === "GOD"
  );
}

export function libraryCopyLimit(lib: { cardCount?: number }): number {
  const cardCount = Math.max(1, Number(lib.cardCount) || 1);
  return Math.max(1, Math.floor(120 / cardCount));
}
