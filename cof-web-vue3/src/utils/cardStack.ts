export function hashText(text: string): number {
  let hash = 2166136261;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

/** 与 old/public/app.js stackStyle 一致 */
export function stackStyle(
  cardId: string,
  index: number,
  type: "draw" | "display",
  topFirst: boolean,
): Record<string, string | number> {
  const hash = hashText(`${type}:${cardId}`);
  const xRange = type === "draw" ? 13 : 16;
  const yRange = type === "draw" ? 16 : 13;
  const rotRange = type === "draw" ? 13 : 11;
  const x = (((hash % 1000) / 999) * 2 - 1) * xRange;
  const y = ((((hash >>> 10) % 1000) / 999) * 2 - 1) * yRange;
  const rot = ((((hash >>> 20) % 1000) / 999) * 2 - 1) * rotRange;
  const zIndex = topFirst ? 100 - index : index + 1;
  return {
    transform: `translate(${x.toFixed(1)}px, ${y.toFixed(1)}px) rotate(${rot.toFixed(1)}deg)`,
    zIndex,
  };
}
