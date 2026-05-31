import type { PublicAnimation, PublicCard, PublicPlayer } from "@/types/api";

/** 错拍动画期间隐藏尚未飞到的牌，与 old visualDrawPile 一致 */
export function visualDrawPile(
  player: PublicPlayer,
  animation: PublicAnimation | null | undefined,
  now: number,
): PublicCard[] {
  const cards = [...(player.drawPile || [])];
  if (!animation || animation.type !== "fail" || !animation.startedAt) {
    return cards.slice(0, 8);
  }
  const elapsed = Math.max(0, now - animation.startedAt);
  const moveMs = animation.moveMs ?? 0;
  const pendingIds = new Set(
    (animation.transfers || [])
      .filter(
        (transfer) =>
          transfer.toPlayerId === player.clientId &&
          elapsed < (transfer.delayMs ?? 0) + moveMs,
      )
      .map((transfer) => transfer.card?.id)
      .filter((id): id is string => Boolean(id)),
  );
  if (pendingIds.size === 0) {
    return cards.slice(0, 8);
  }
  return cards.filter((card) => !pendingIds.has(card.id)).slice(0, 8);
}
