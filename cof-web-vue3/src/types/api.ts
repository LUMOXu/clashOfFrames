export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T | null;
}

export interface PlayerInGameStats {
  plays?: number;
  rings?: number;
  correctRings?: number;
  wrongRings?: number;
  wonCards?: number;
}

export interface PublicPlayer {
  clientId: string;
  username: string;
  statsId?: string;
  stats?: PlayerInGameStats;
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
  rank?: number;
  eliminatedAt?: number;
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
  playCount?: number;
  text?: string;
  at?: number;
}

export interface ResultInfoPlayer {
  clientId: string;
  username: string;
}

export interface ResultInfo {
  players?: ResultInfoPlayer[];
  counts?: number[][];
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

export interface PublicTopEntry {
  playerId?: string;
  username?: string;
  playedSeq?: number;
  card?: PublicCard;
}

export interface PublicGame {
  id: string;
  roomId?: string;
  settings?: GameSettings;
  status?: string;
  turnIndex?: number;
  turnDeadlineAt?: number;
  turnAvailableAt?: number;
  playCount?: number;
  bellCount?: number;
  successBellCount?: number;
  failBellCount?: number;
  lockedUntil?: number;
  lockMessage?: string;
  winnerId?: string;
  players?: PublicPlayer[];
  logs?: GameLog[];
  lastMatch?: PublicMatch;
  lastAnimation?: PublicAnimation;
  preLastTopCards?: PublicTopEntry[];
  continueVotes?: string[];
  continueReturnAt?: number;
  resultInfo?: ResultInfo;
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

export interface LeaderboardData {
  players: LeaderboardEntry[];
  matches: LeaderboardEntry[];
}

export interface CardViewerPmv {
  pmvId?: number;
  name?: string;
  pmvName?: string;
  author?: string;
  description?: string;
  link?: string;
  shots?: { id: string; imageUrl?: string; shot?: string }[];
  cards?: { id: string; imageUrl?: string; shot?: string }[];
}

export interface CardViewerLibrary {
  id: string;
  name: string;
  folderName?: string;
  curator?: string;
  description?: string;
  version?: string;
  link?: string;
  backUrl?: string;
  cardCount?: number;
  pmvCount?: number;
  pmvs?: CardViewerPmv[];
}

export interface CardViewerPayload {
  key: string;
  assets: string[];
  libraries: CardViewerLibrary[];
}
