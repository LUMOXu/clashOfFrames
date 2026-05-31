export interface LayoutPlayer {
  clientId: string;
  username: string;
  connected?: boolean;
  eliminated?: boolean;
  drawCount?: number;
  displayCount?: number;
}

export interface PlayerLayout {
  player: LayoutPlayer;
  x: number;
  y: number;
  drawX: number;
  drawY: number;
  displayX: number;
  displayY: number;
}

export function rotatePlayersForSelf<T extends { clientId: string }>(players: T[], selfId: string): T[] {
  const index = players.findIndex((p) => p.clientId === selfId);
  if (index <= 0) return players.slice();
  return [...players.slice(index), ...players.slice(0, index)];
}

export function playerLayouts(players: LayoutPlayer[], selfId: string): PlayerLayout[] {
  const rotated = rotatePlayersForSelf(players, selfId);
  const n = rotated.length || 1;
  return rotated.map((player, index) => {
    const angle = Math.PI / 2 + (Math.PI * 2 * index) / n;
    const x = 50 + Math.cos(angle) * 38;
    const y = 46 + Math.sin(angle) * 34;
    return {
      player,
      x,
      y,
      drawX: x - 8.2,
      drawY: y + 1.2,
      displayX: x + 8.4,
      displayY: y + 1.2,
    };
  });
}
