export function flyStyle(
  fromX: number,
  fromY: number,
  toX: number,
  toY: number,
  durationMs: number,
  delayMs = 0,
  elapsedMs = 0,
): Record<string, string> {
  const effectiveDelay = delayMs - elapsedMs;
  return {
    "--from-left": `${fromX}%`,
    "--from-top": `${fromY}%`,
    "--to-left": `${toX}%`,
    "--to-top": `${toY}%`,
    animationDuration: `${durationMs}ms`,
    animationDelay: `${effectiveDelay}ms`,
  };
}
