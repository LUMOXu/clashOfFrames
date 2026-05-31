import type { ParsedSyncMessage, WsMessage } from "@/types/ws";

export const WS_GAME_PATH = "/ws/v1/game";

export function buildGameSocketUrl(token: string, origin = window.location.origin): string {
  const protocol = origin.startsWith("https") ? "wss" : "ws";
  const host = origin.replace(/^https?:\/\//, "");
  return `${protocol}://${host}${WS_GAME_PATH}?token=${encodeURIComponent(token)}`;
}

export function parseWsMessage(raw: string): WsMessage | null {
  try {
    const parsed = JSON.parse(raw) as WsMessage;
    if (!parsed || typeof parsed.t !== "string") {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function parseSyncMessage(raw: string): ParsedSyncMessage | null {
  const message = parseWsMessage(raw);
  if (!message || message.t.toUpperCase() !== "SYNC") {
    return null;
  }
  if (!message.g) {
    return null;
  }
  return {
    type: "SYNC",
    gameId: message.g,
    turnIndex: message.ti ?? 0,
    playCount: Number.parseInt(message.pc ?? "0", 10) || 0,
    syncPayload: message.sync ?? null,
  };
}

export type GameSocketListener = (message: WsMessage) => void;

export class GameSocket {
  private socket: WebSocket | null = null;
  private listeners = new Set<GameSocketListener>();

  connect(token: string, urlFactory: (token: string) => string = buildGameSocketUrl): void {
    this.disconnect();
    this.socket = new WebSocket(urlFactory(token));
    this.socket.addEventListener("message", (event) => {
      const message = parseWsMessage(String(event.data));
      if (message) {
        this.listeners.forEach((listener) => listener(message));
      }
    });
  }

  disconnect(): void {
    this.socket?.close();
    this.socket = null;
  }

  send(payload: WsMessage): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(payload));
    }
  }

  onMessage(listener: GameSocketListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  get readyState(): number {
    return this.socket?.readyState ?? WebSocket.CLOSED;
  }
}
