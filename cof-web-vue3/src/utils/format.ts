export function fmtNum(value: number | undefined | null): string {
  const n = Number(value);
  if (!Number.isFinite(n)) return "-";
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}

export function fmtPct(value: number | undefined | null): string {
  const n = Number(value);
  if (!Number.isFinite(n)) return "-";
  return `${Math.round(n * 100)}%`;
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
