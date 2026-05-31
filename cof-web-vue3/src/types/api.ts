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
  eliminated?: boolean;
  exited?: boolean;
  ready?: boolean;
  loadingLoaded?: number;
  loadingTotal?: number;
  loadingProgress?: number;
  drawCount?: number;
  displayCount?: number;
  isComputer?: boolean;
  computerId?: string;
  displayPile?: PublicCard[];
  drawPile?: PublicCard[];
}

export interface PublicCard {
  id: string;
  imageUrl?: string;
  backUrl?: string;
  pmvName?: string;
  pmvId?: number;
}

export interface GameLog {
  id?: string;
  text?: string;
}

export interface PublicMatch {
  type?: string;
  by?: string;
  username?: string;
  pmvName?: string;
  given?: number;
  at?: number;
  cards?: { card?: PublicCard; imageUrl?: string }[];
}

export interface PublicAnimation {
  id?: string;
  type?: string;
  targetPlayerId?: string;
  startedAt?: number;
  durationMs?: number;
  moveMs?: number;
  matchCardIds?: string[];
  piles?: { playerId?: string; cards?: PublicCard[] }[];
  transfers?: { fromPlayerId?: string; toPlayerId?: string; card?: PublicCard; delayMs?: number }[];
}

export interface PublicGame {
  id: string;
  roomId?: string;
  status?: string;
  turnIndex?: number;
  turnDeadlineAt?: number;
  turnAvailableAt?: number;
  playCount?: number;
  bellCount?: number;
  lockedUntil?: number;
  lockMessage?: string;
  winnerId?: string;
  players?: PublicPlayer[];
  logs?: GameLog[];
  lastMatch?: PublicMatch;
  lastAnimation?: PublicAnimation;
  continueVotes?: string[];
  continueReturnAt?: number;
  resultInfo?: { players?: unknown[]; counts?: number[][] };
}

export interface RoomChatMessage {
  clientId: string;
  username: string;
  text: string;
  at: number;
}

export interface RoomPlayerDetail {
  clientId: string;
  username: string;
  isComputer?: boolean;
  computerId?: string;
}

export interface GameSettings {
  minPlayers?: number;
  maxPlayers?: number;
  isPublic?: boolean;
  libraryIds?: string[];
  libraryCopies?: Record<string, number>;
  startVoteThresholdMode?: string;
  startVoteThreshold?: number;
  allowEmptyBell?: boolean;
  randomBacks?: boolean;
  conflictResolution?: boolean;
  disconnectProtection?: boolean;
}

export interface RoomSummary {
  id: string;
  status: string;
  hostId?: string;
  players?: string[];
  spectators?: string[];
  settings?: GameSettings;
  gameId?: string;
  startVotes?: string[];
  startAt?: number;
  chatMessages?: RoomChatMessage[];
  playerDetails?: RoomPlayerDetail[];
}

export interface BootstrapData {
  libraries?: unknown[];
  computerPlayers?: unknown[];
  player?: PublicPlayer | null;
  rooms?: RoomSummary[];
  currentRoom?: RoomSummary | null;
  currentGame?: PublicGame | null;
}

export interface AuthResult {
  token: string;
  player: PublicPlayer;
  passwordReset?: boolean;
}

export interface ProfileData {
  profile: Record<string, unknown>;
}

export type LeaderboardEntry = Record<string, unknown>;

export interface CardViewerPayload {
  key: string;
  assets: string[];
  libraries: {
    id: string;
    name: string;
    backUrl?: string;
    pmvs?: { pmvId?: number; pmvName?: string; cards?: { id: string; imageUrl?: string; shot?: string }[] }[];
  }[];
}
