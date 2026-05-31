import type { GameLog } from "@/types/api";

export function formatGameLog(log: GameLog): string {
  const text = log.text?.trim() || "";
  if (!log.at) return text;
  const time = new Date(log.at).toLocaleTimeString("zh-CN", { hour12: false });
  return `[${time}] ${text}`;
}
