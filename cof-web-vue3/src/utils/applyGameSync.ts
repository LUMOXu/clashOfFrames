import type { GameLog, GameSettings, PublicGame, PublicPlayer, PublicTopEntry } from "@/types/api";
import { expandCompactCard, expandCompactCards } from "./cardSync";

interface PlayerPatch {
  id?: string;
  clientId?: string;
  dc?: number;
  xc?: number;
  el?: boolean;
  ex?: boolean;
  co?: boolean;
  rd?: boolean;
  rk?: number;
  ea?: number;
  drm?: number;
  dr?: unknown;
  dp?: unknown;
  dpa?: unknown;
  ps?: {
    pl?: number;
    rg?: number;
    cr?: number;
    wr?: number;
    wc?: number;
  };
}

interface CompactTopEntry {
  pid: string;
  s?: number;
  c?: unknown;
}

function mergeStats(
  previous: PublicPlayer["stats"] | undefined,
  patch: PlayerPatch["ps"],
): PublicPlayer["stats"] | undefined {
  if (!patch) {
    return previous;
  }
  return {
    plays: patch.pl ?? previous?.plays ?? 0,
    rings: patch.rg ?? previous?.rings ?? 0,
    correctRings: patch.cr ?? previous?.correctRings ?? 0,
    wrongRings: patch.wr ?? previous?.wrongRings ?? 0,
    wonCards: patch.wc ?? previous?.wonCards ?? 0,
  };
}

function applyPlayerPatch(
  previous: PublicPlayer | undefined,
  patch: PlayerPatch,
  settings?: GameSettings,
): PublicPlayer {
  if (patch.clientId && !patch.id) {
    return { ...previous, ...patch } as PublicPlayer;
  }
  const clientId = patch.id ?? patch.clientId ?? previous?.clientId ?? "";
  const next: PublicPlayer = {
    ...previous,
    clientId,
    username: previous?.username ?? "",
  };
  if (typeof patch.dc === "number") next.drawCount = patch.dc;
  if (typeof patch.xc === "number") {
    next.displayCount = patch.xc;
    if (patch.xc === 0 && patch.dp === undefined && !patch.dpa) {
      next.displayPile = [];
    }
  }
  if (typeof patch.el === "boolean") next.eliminated = patch.el;
  if (typeof patch.ex === "boolean") next.exited = patch.ex;
  if (typeof patch.co === "boolean") next.connected = patch.co;
  if (typeof patch.rd === "boolean") next.ready = patch.rd;
  if (typeof patch.rk === "number") next.rank = patch.rk;
  if (typeof patch.ea === "number") next.eliminatedAt = patch.ea;
  if (typeof patch.drm === "number" && patch.drm > 0) {
    next.drawPile = (previous?.drawPile ?? []).slice(patch.drm);
  } else {
    const dr = expandCompactCards(patch.dr, settings);
    if (dr) next.drawPile = dr;
  }
  if (patch.dpa) {
    const appended = expandCompactCard(
      patch.dpa as Parameters<typeof expandCompactCard>[0],
      settings,
    );
    next.displayPile = [...(previous?.displayPile ?? []), appended];
  } else {
    const dp = expandCompactCards(patch.dp, settings);
    if (dp) next.displayPile = dp;
  }
  if (patch.ps) {
    next.stats = mergeStats(previous?.stats, patch.ps);
  }
  return next;
}

function mergePlayers(
  previous: PublicPlayer[],
  patches: PlayerPatch[],
  settings?: GameSettings,
): PublicPlayer[] {
  const patchById = new Map<string, PlayerPatch>();
  for (const patch of patches) {
    const key = patch.id ?? patch.clientId;
    if (key) patchById.set(key, patch);
  }
  const merged = previous.map((player) => {
    const patch = patchById.get(player.clientId);
    if (!patch) {
      return { ...player };
    }
    patchById.delete(player.clientId);
    return applyPlayerPatch(player, patch, settings);
  });
  for (const patch of patchById.values()) {
    const key = patch.id ?? patch.clientId;
    if (!key) continue;
    merged.push(applyPlayerPatch(undefined, patch, settings));
  }
  return merged;
}

function mergeTopCards(
  previous: PublicGame["preLastTopCards"],
  entries: unknown,
  settings?: GameSettings,
): PublicGame["preLastTopCards"] {
  if (!Array.isArray(entries)) {
    return previous;
  }
  const map = new Map(
    (previous ?? []).map((entry) => [entry.playerId ?? "", { ...entry }]),
  );
  for (const raw of entries) {
    if (!raw || typeof raw !== "object") continue;
    const entry = raw as CompactTopEntry & PublicTopEntry;
    const playerId = entry.pid ?? entry.playerId;
    if (!playerId) continue;
    map.set(playerId, {
      playerId,
      username: entry.username,
      playedSeq: entry.s ?? entry.playedSeq,
      card: entry.c
        ? expandCompactCard(entry.c as Parameters<typeof expandCompactCard>[0], settings)
        : entry.card,
    });
  }
  return Array.from(map.values());
}

function clonePlayers(players: PublicPlayer[]): PublicPlayer[] {
  return players.map((player) => ({
    ...player,
    drawPile: player.drawPile ? [...player.drawPile] : [],
    displayPile: player.displayPile ? [...player.displayPile] : [],
    stats: player.stats ? { ...player.stats } : player.stats,
  }));
}

/** 与 GameCore.tableTopCards 一致：出牌前各玩家展示堆顶牌 */
function snapshotTableTops(players: PublicPlayer[]): PublicTopEntry[] {
  const tops: PublicTopEntry[] = [];
  for (const player of players) {
    const pile = player.displayPile;
    if (!pile?.length) continue;
    const card = pile[pile.length - 1];
    tops.push({
      playerId: player.clientId,
      username: player.username,
      playedSeq: card.playedSeq && card.playedSeq > 0 ? card.playedSeq : 0,
      card: { ...card },
    });
  }
  return tops;
}

function applyPlayCardEvent(
  previous: PublicGame,
  node: Record<string, unknown>,
): PublicGame {
  const settings = previous.settings;
  const players = clonePlayers(previous.players ?? []);
  const next: PublicGame = { ...previous, players };
  next.preLastTopCards = snapshotTableTops(players);

  const by = typeof node.by === "string" ? node.by : "";
  const card = expandCompactCard(
    node.c as Parameters<typeof expandCompactCard>[0],
    settings,
  );
  const prevActor = previous.players?.find((player) => player.clientId === by);
  const actor = players.find((player) => player.clientId === by);
  if (actor) {
    if (actor.drawPile?.length) {
      actor.drawPile = actor.drawPile.slice(1);
      actor.drawCount = actor.drawPile.length;
    } else {
      actor.drawCount = Math.max(0, (prevActor?.drawCount ?? actor.drawCount ?? 0) - 1);
    }
    actor.displayPile = [...(actor.displayPile ?? []), card];
    actor.displayCount = actor.displayPile.length;
    if (node.el === 1 || node.el === true) {
      actor.eliminated = true;
    }
    if (typeof node.rk === "number") {
      actor.rank = node.rk;
    }
    if (actor.stats) {
      actor.stats = { ...actor.stats, plays: (actor.stats.plays ?? 0) + 1 };
    }
  }

  if (typeof node.st === "string") next.status = node.st;
  if (typeof node.ti === "number") next.turnIndex = node.ti;
  if (typeof node.td === "number") next.turnDeadlineAt = node.td;
  if (typeof node.ta === "number") next.turnAvailableAt = node.ta;
  if (typeof node.lu === "number") next.lockedUntil = node.lu;
  if (typeof node.lm === "string") next.lockMessage = node.lm;
  if (typeof node.pc === "number") next.playCount = node.pc;
  if (typeof node.w === "string") next.winnerId = node.w;
  if (Array.isArray(node.eo)) {
    next.eliminatedOrder = node.eo as string[];
  }
  if (node.ri && typeof node.ri === "object") {
    next.resultInfo = node.ri as PublicGame["resultInfo"];
  }
  return next;
}

function appendLogs(previous: GameLog[], incoming: unknown[]): GameLog[] {
  const mapped = incoming.map((entry) => {
    if (!entry || typeof entry !== "object") {
      return entry as GameLog;
    }
    const raw = entry as Record<string, unknown>;
    if (typeof raw.text === "string") {
      return raw as GameLog;
    }
    return {
      text: typeof raw.t === "string" ? raw.t : undefined,
      at: typeof raw.at === "number" ? raw.at : undefined,
      playCount: typeof raw.pc === "number" ? raw.pc : undefined,
    } as GameLog;
  });
  return [...previous, ...mapped];
}

export function applyGameSync(previous: PublicGame | null, sync: unknown): PublicGame | null {
  if (!sync || typeof sync !== "object") {
    return previous;
  }
  const node = sync as Record<string, unknown>;
  if (node.full && typeof node.full === "object") {
    return node.full as PublicGame;
  }
  if (!previous) {
    return null;
  }
  if (node.hb === true) {
    return previous;
  }
  if (node.ev === "play") {
    return applyPlayCardEvent(previous, node);
  }

  const settings = previous.settings;
  const next: PublicGame = { ...previous };

  if (typeof node.st === "string") next.status = node.st;
  if (typeof node.ti === "number") next.turnIndex = node.ti;
  if (typeof node.td === "number") next.turnDeadlineAt = node.td;
  if (typeof node.ta === "number") next.turnAvailableAt = node.ta;
  if (typeof node.lu === "number") next.lockedUntil = node.lu;
  if (typeof node.lm === "string") next.lockMessage = node.lm;
  if (typeof node.pc === "number") next.playCount = node.pc;
  if (typeof node.bc === "number") next.bellCount = node.bc;
  if (typeof node.sbc === "number") next.successBellCount = node.sbc;
  if (typeof node.fbc === "number") next.failBellCount = node.fbc;
  if (typeof node.dc === "number") next.discardedCards = node.dc;
  if (typeof node.w === "string") next.winnerId = node.w;
  if (typeof node.ccs === "number") next.continueCountdownStartedAt = node.ccs;
  if (typeof node.cra === "number") next.continueReturnAt = node.cra;

  if (Array.isArray(node.pl)) {
    next.players = mergePlayers(previous.players ?? [], node.pl as PlayerPatch[], settings);
  }
  if (Array.isArray(node.lg)) {
    next.logs = appendLogs(previous.logs ?? [], node.lg);
  }
  if (node.la && typeof node.la === "object") {
    next.lastAnimation = node.la as PublicGame["lastAnimation"];
  }
  if (node.lmch && typeof node.lmch === "object") {
    next.lastMatch = node.lmch as PublicGame["lastMatch"];
  }
  if (Array.isArray(node.pt)) {
    next.preLastTopCards = mergeTopCards(previous.preLastTopCards, node.pt, settings);
  }
  if (Array.isArray(node.eo)) {
    next.eliminatedOrder = node.eo as string[];
  }
  if (Array.isArray(node.cv)) {
    next.continueVotes = node.cv as string[];
  }
  if (node.ri && typeof node.ri === "object") {
    next.resultInfo = node.ri as PublicGame["resultInfo"];
  }

  return next;
}
