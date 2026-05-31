export interface WsMessage {
  t: string;
  g?: string;
  r?: string;
  cid?: string;
  ti?: number;
  pc?: string;
  ld?: number;
  lt?: number;
  dn?: boolean;
  au?: string;
  sync?: unknown;
  room?: unknown;
  err?: string;
}

export interface ParsedSyncMessage {
  type: "SYNC";
  gameId: string;
  turnIndex: number;
  playCount: number;
  syncPayload: unknown;
}
