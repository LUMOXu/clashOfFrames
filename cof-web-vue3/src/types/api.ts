export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T | null;
}

export interface PublicPlayer {
  clientId: string;
  username: string;
  statsId?: string;
  connected?: boolean;
  joinedAt?: number;
  lastSeenAt?: number;
}

export interface BootstrapData {
  libraries?: unknown[];
  computerPlayers?: unknown[];
  player?: PublicPlayer | null;
}

export interface AuthResult {
  token: string;
  player: PublicPlayer;
  passwordReset?: boolean;
}

export interface RoomSummary {
  id: string;
  status: string;
  hostId?: string;
  [key: string]: unknown;
}

export interface PublicGame {
  id: string;
  roomId?: string;
  status?: string;
  turnIndex?: number;
  playCount?: number;
  [key: string]: unknown;
}

export interface ProfileData {
  profile: Record<string, unknown>;
}

export type LeaderboardEntry = Record<string, unknown>;
