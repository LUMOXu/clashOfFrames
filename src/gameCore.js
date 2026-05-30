"use strict";

const DEFAULT_SETTINGS = {
  minPlayers: 3,
  maxPlayers: 8,
  isPublic: true,
  libraryIds: [],
  allowEmptyBell: false,
  randomBacks: false,
  conflictResolution: true,
  disconnectProtection: true,
};

const TURN_TIMEOUT_MS = 8000;
const DISCONNECTED_TURN_TIMEOUT_MS = 2000;
const MANUAL_TURN_TIMEOUT_MS = 24 * 60 * 60 * 1000;
const SUCCESS_HIGHLIGHT_MS = 3000;
const SUCCESS_MOVE_MS = 1200;
const FAIL_MOVE_MS = 900;
const FAIL_STAGGER_MS = 300;

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function randomId(prefix = "") {
  return `${prefix}${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
}

function shuffle(items, rng = Math.random) {
  const out = items.slice();
  for (let i = out.length - 1; i > 0; i -= 1) {
    const j = Math.floor(rng() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

function normalizeSettings(input = {}, availableLibraryIds = []) {
  const merged = { ...DEFAULT_SETTINGS, ...input };
  const minPlayers = Math.max(3, Math.min(8, Number.parseInt(merged.minPlayers, 10) || 3));
  const maxPlayers = Math.max(minPlayers, Math.min(8, Number.parseInt(merged.maxPlayers, 10) || 8));
  const selectedLibraries = Array.isArray(merged.libraryIds) ? merged.libraryIds : [];
  const validSelected = selectedLibraries.filter((id) => availableLibraryIds.includes(id));
  const libraryIds = validSelected.length > 0 ? [...new Set(validSelected)] : availableLibraryIds.slice(0, 1);

  return {
    minPlayers,
    maxPlayers,
    isPublic: Boolean(merged.isPublic),
    libraryIds,
    allowEmptyBell: Boolean(merged.allowEmptyBell),
    randomBacks: Boolean(merged.randomBacks),
    conflictResolution: merged.conflictResolution !== false,
    disconnectProtection: merged.disconnectProtection !== false,
  };
}

function dealCards(cards, playerCount) {
  const perPlayer = Math.floor(cards.length / playerCount);
  const used = cards.slice(0, perPlayer * playerCount);
  const hands = Array.from({ length: playerCount }, () => []);
  used.forEach((card, index) => {
    hands[index % playerCount].push(clone(card));
  });
  return { hands, discarded: cards.length - used.length, perPlayer };
}

function createGame({ room, players, cards, now = Date.now(), rng = Math.random }) {
  const deck = shuffle(cards, rng);
  const { hands, discarded, perPlayer } = dealCards(deck, players.length);
  if (perPlayer < 1) {
    throw new Error("Not enough cards to deal at least one card to each player.");
  }

  const startIndex = typeof room.lastWinnerId === "string"
    ? Math.max(0, players.findIndex((player) => player.clientId === room.lastWinnerId))
    : Math.floor(rng() * players.length);

  const gamePlayers = players.map((player, index) => ({
    clientId: player.clientId,
    username: player.username,
    connected: player.connected !== false,
    eliminated: false,
    exited: false,
    ready: false,
    loadingLoaded: 0,
    loadingTotal: 0,
    loadingProgress: 0,
    loadingCached: false,
    loadingStartedAt: null,
    loadingFinishedAt: null,
    drawPile: hands[index],
    displayPile: [],
    eliminatedAt: null,
    rank: null,
    stats: {
      plays: 0,
      rings: 0,
      correctRings: 0,
      wrongRings: 0,
      wonCards: 0,
    },
  }));

  const game = {
    id: randomId("game_"),
    roomId: room.id,
    status: "loading",
    settings: clone(room.settings),
    players: gamePlayers,
    spectators: [],
    turnIndex: startIndex < 0 ? 0 : startIndex,
    turnStartedAt: now,
    turnAvailableAt: now,
    turnDeadlineAt: now + TURN_TIMEOUT_MS,
    lastPlayAt: 0,
    lockedUntil: 0,
    lockMessage: "",
    lastAnimation: null,
    playCount: 0,
    bellCount: 0,
    successBellCount: 0,
    failBellCount: 0,
    discardedCards: discarded,
    preLastTopCards: [],
    lastMatch: null,
    logs: [],
    eliminatedOrder: [],
    winnerId: null,
    continueVotes: [],
    continueCountdownStartedAt: null,
    continueReturnAt: null,
    createdAt: now,
    startedAt: null,
    finishedAt: null,
  };

  addLog(game, "游戏加载中。");
  return game;
}

function publicBackCard(card) {
  return {
    id: card.id,
    libraryId: card.libraryId,
    backUrl: card.backUrl,
  };
}

function publicFaceCard(card) {
  return {
    id: card.id,
    libraryId: card.libraryId,
    pmvId: card.pmvId,
    pmvName: card.pmvName,
    shot: card.shot,
    imageUrl: card.imageUrl,
    backUrl: card.backUrl,
    playedSeq: card.playedSeq || 0,
    playedBy: card.playedBy || null,
  };
}

function publicAnimation(animation) {
  if (!animation) return null;
  if (animation.type === "success") {
    return {
      ...clone(animation),
      piles: animation.piles.map((pile) => ({
        ...pile,
        cards: pile.cards.map(publicFaceCard),
      })),
    };
  }
  if (animation.type === "fail") {
    return {
      ...clone(animation),
      transfers: animation.transfers.map((transfer) => ({
        ...transfer,
        card: publicBackCard(transfer.card),
      })),
    };
  }
  return clone(animation);
}

function publicGame(game) {
  return {
    ...clone(game),
    logs: game.logs.slice(0, 80).map(clone),
    lastAnimation: publicAnimation(game.lastAnimation),
    players: game.players.map((player) => ({
      ...clone(player),
      drawCount: player.drawPile.length,
      displayCount: player.displayPile.length,
      drawPile: player.drawPile.slice(0, 8).map(publicBackCard),
      displayPile: player.displayPile.map(publicFaceCard),
    })),
  };
}

function addLog(game, text) {
  game.logs.unshift({
    id: randomId("log_"),
    playCount: game.playCount,
    text: `[出牌数${game.playCount}] ${text}`,
    at: Date.now(),
  });
  if (game.logs.length > 250) {
    game.logs.length = 250;
  }
}

function getPlayer(game, clientId) {
  return game.players.find((player) => player.clientId === clientId);
}

function activePlayers(game, { includeEmptyBell = true } = {}) {
  return game.players.filter((player) => {
    if (player.eliminated || player.exited) return false;
    if (includeEmptyBell && game.settings.allowEmptyBell) return true;
    return player.drawPile.length > 0;
  });
}

function tableTopCards(game) {
  return game.players
    .map((player) => {
      const card = player.displayPile[player.displayPile.length - 1];
      if (!card) return null;
      return {
        playerId: player.clientId,
        username: player.username,
        card: clone(card),
        playedSeq: card.playedSeq || 0,
      };
    })
    .filter(Boolean);
}

function findMatchFromTopCards(topCards) {
  const groups = new Map();
  topCards.forEach((entry) => {
    if (!groups.has(entry.card.pmvId)) groups.set(entry.card.pmvId, []);
    groups.get(entry.card.pmvId).push(entry);
  });

  const candidates = [...groups.values()]
    .filter((group) => group.length >= 2)
    .sort((a, b) => {
      const aLatest = Math.max(...a.map((entry) => entry.playedSeq || 0));
      const bLatest = Math.max(...b.map((entry) => entry.playedSeq || 0));
      return bLatest - aLatest;
    });

  if (candidates.length === 0) return null;
  const group = candidates[0].slice().sort((a, b) => (b.playedSeq || 0) - (a.playedSeq || 0));
  const shown = group.slice(0, 2);
  return {
    pmvId: shown[0].card.pmvId,
    pmvName: shown[0].card.pmvName,
    cards: shown,
  };
}

function findCurrentMatch(game) {
  return findMatchFromTopCards(tableTopCards(game));
}

function findBellMatch(game) {
  const current = findCurrentMatch(game);
  if (!game.settings.conflictResolution) return current;
  const previous = findMatchFromTopCards(game.preLastTopCards || []);
  return previous || current;
}

function canAct(game, clientId, now = Date.now()) {
  const player = getPlayer(game, clientId);
  if (!player) return { ok: false, error: "你不在这局游戏中。" };
  if (game.status !== "playing") return { ok: false, error: "游戏还没有开始。" };
  if (game.lockedUntil > now) return { ok: false, error: "动作结算中，请稍等。" };
  if (player.eliminated || player.exited) return { ok: false, error: "你已经不能操作了。" };
  return { ok: true, player };
}

function nextTurnIndex(game, fromIndex = game.turnIndex) {
  if (game.players.length === 0) return 0;
  for (let step = 1; step <= game.players.length; step += 1) {
    const index = (fromIndex + step) % game.players.length;
    const player = game.players[index];
    if (!player.eliminated && !player.exited && player.drawPile.length > 0) {
      return index;
    }
  }
  return fromIndex;
}

function turnTimeoutMs(game, index = game.turnIndex) {
  const player = game.players[index];
  if (player && player.connected === false && game.settings.disconnectProtection) {
    return DISCONNECTED_TURN_TIMEOUT_MS;
  }
  return TURN_TIMEOUT_MS;
}

function setTurnTiming(game, now = Date.now(), availableDelay = 1000, options = {}) {
  game.turnStartedAt = now;
  game.turnAvailableAt = now + availableDelay;
  const timeout = options.manualOnly && game.players[game.turnIndex]?.connected !== false
    ? MANUAL_TURN_TIMEOUT_MS
    : turnTimeoutMs(game);
  game.turnDeadlineAt = now + timeout;
}

function ensureTurnPlayerCanPlay(game, now = Date.now()) {
  const current = game.players[game.turnIndex];
  if (!current || current.eliminated || current.exited || current.drawPile.length === 0) {
    game.turnIndex = nextTurnIndex(game, game.turnIndex);
    setTurnTiming(game, now, 0);
  }
}

function startPlaying(game, now = Date.now()) {
  game.status = "playing";
  game.startedAt = game.startedAt || now;
  setTurnTiming(game, now, 0);
  ensureTurnPlayerCanPlay(game, now);
  addLog(game, "所有可用玩家加载完成，对局开始。");
}

function performPlayCard(game, clientId, { now = Date.now(), auto = false } = {}) {
  const acting = canAct(game, clientId, now);
  if (!acting.ok) return acting;
  const player = acting.player;
  const playerIndex = game.players.findIndex((item) => item.clientId === clientId);
  if (playerIndex !== game.turnIndex) return { ok: false, error: "还没轮到你出牌。" };
  if (!auto && now < game.turnAvailableAt) return { ok: false, error: "出牌间隔还没到。" };
  if (player.drawPile.length === 0) return { ok: false, error: "你已经没有未出牌了。" };

  game.preLastTopCards = tableTopCards(game);
  const card = player.drawPile.shift();
  game.playCount += 1;
  card.playedSeq = game.playCount;
  card.playedBy = player.clientId;
  player.displayPile.push(card);
  player.stats.plays += 1;
  game.lastPlayAt = now;
  addLog(game, `${player.username}${auto ? "自动" : ""}出牌。`);

  if (!game.settings.allowEmptyBell && player.drawPile.length === 0) {
    eliminatePlayer(game, player.clientId, "牌堆耗尽");
  }

  game.turnIndex = nextTurnIndex(game, playerIndex);
  if (game.settings.allowEmptyBell) eliminateEmptyDuelPlayers(game);
  setTurnTiming(game, now, 1000);
  checkGameOver(game, now);
  return { ok: true, game };
}

function clockwisePlayersAfter(game, clientId) {
  const start = game.players.findIndex((player) => player.clientId === clientId);
  if (start < 0) return [];
  const out = [];
  for (let step = 1; step < game.players.length; step += 1) {
    const player = game.players[(start + step) % game.players.length];
    if (!player.eliminated && !player.exited) out.push(player);
  }
  return out;
}

function performRingBell(game, clientId, { now = Date.now(), rng = Math.random } = {}) {
  const acting = canAct(game, clientId, now);
  if (!acting.ok) return acting;
  const player = acting.player;
  const match = findBellMatch(game);
  game.bellCount += 1;
  player.stats.rings += 1;

  if (match) {
    const wonCards = [];
    const piles = [];
    game.players.forEach((item) => {
      if (item.displayPile.length > 0) {
        piles.push({
          playerId: item.clientId,
          username: item.username,
          cards: item.displayPile.map((card) => clone(card)),
        });
      }
      wonCards.push(...item.displayPile.splice(0));
    });
    const shuffledWon = shuffle(wonCards, rng);
    const durationMs = SUCCESS_HIGHLIGHT_MS + SUCCESS_MOVE_MS;
    player.drawPile.push(...shuffledWon);
    player.stats.correctRings += 1;
    player.stats.wonCards += wonCards.length;
    game.successBellCount += 1;
    game.lastMatch = {
      type: "success",
      by: clientId,
      username: player.username,
      pmvId: match.pmvId,
      pmvName: match.pmvName,
      cards: match.cards,
      wonCards: wonCards.length,
      at: now,
    };
    game.lastAnimation = {
      id: randomId("anim_"),
      type: "success",
      by: clientId,
      username: player.username,
      targetPlayerId: clientId,
      startedAt: now,
      highlightMs: SUCCESS_HIGHLIGHT_MS,
      moveMs: SUCCESS_MOVE_MS,
      durationMs,
      pmvId: match.pmvId,
      pmvName: match.pmvName,
      matchCardIds: match.cards.map((entry) => entry.card.id),
      piles,
    };
    game.lockedUntil = now + durationMs;
    game.lockMessage = `${player.username}匹配成功：${match.pmvName}`;
    game.turnIndex = game.players.findIndex((item) => item.clientId === clientId);
    if (game.settings.allowEmptyBell) eliminateEmptyDuelPlayers(game);
    setTurnTiming(game, game.lockedUntil, 1000, { manualOnly: true });
    addLog(game, `${player.username}正确按铃，匹配 ${match.pmvName}，赢得 ${wonCards.length} 张牌。`);
  } else {
    const recipients = clockwisePlayersAfter(game, clientId);
    let given = 0;
    const transfers = [];
    for (const recipient of recipients) {
      const card = player.drawPile.shift();
      if (!card) break;
      transfers.push({
        fromPlayerId: clientId,
        toPlayerId: recipient.clientId,
        card: clone(card),
        delayMs: given * FAIL_STAGGER_MS,
      });
      recipient.drawPile.unshift(card);
      given += 1;
    }
    const durationMs = Math.max(FAIL_MOVE_MS, (Math.max(0, transfers.length - 1) * FAIL_STAGGER_MS) + FAIL_MOVE_MS);
    player.stats.wrongRings += 1;
    game.failBellCount += 1;
    game.lastMatch = {
      type: "fail",
      by: clientId,
      username: player.username,
      given,
      at: now,
    };
    game.lastAnimation = {
      id: randomId("anim_"),
      type: "fail",
      by: clientId,
      username: player.username,
      startedAt: now,
      moveMs: FAIL_MOVE_MS,
      staggerMs: FAIL_STAGGER_MS,
      durationMs,
      transfers,
    };
    game.lockedUntil = now + durationMs;
    game.lockMessage = `${player.username}错误按铃`;
    addLog(game, `${player.username}错误按铃，交出 ${given} 张牌。`);
    if (game.settings.allowEmptyBell && player.drawPile.length === 0) {
      eliminatePlayer(game, player.clientId, "空牌错误按铃");
    } else if (!game.settings.allowEmptyBell && player.drawPile.length === 0) {
      eliminatePlayer(game, player.clientId, "牌堆耗尽");
    }
    if (game.settings.allowEmptyBell) eliminateEmptyDuelPlayers(game);
    setTurnTiming(game, game.lockedUntil, 1000, { manualOnly: true });
  }

  checkGameOver(game, now);
  return { ok: true, game };
}

function eliminatePlayer(game, clientId, reason = "淘汰") {
  const player = getPlayer(game, clientId);
  if (!player || player.eliminated) return;
  player.eliminated = true;
  player.eliminatedAt = Date.now();
  player.rank = null;
  game.eliminatedOrder.push(clientId);
  addLog(game, `${player.username}已被淘汰（${reason}）。`);
  if (game.players[game.turnIndex]?.clientId === clientId) {
    game.turnIndex = nextTurnIndex(game, game.turnIndex);
  }
}

function eliminateEmptyDuelPlayers(game) {
  const remaining = game.players.filter((player) => !player.eliminated && !player.exited);
  if (remaining.length !== 2) return;
  remaining
    .filter((player) => player.drawPile.length === 0)
    .forEach((player) => eliminatePlayer(game, player.clientId, "双人局牌堆耗尽"));
}

function checkGameOver(game, now = Date.now()) {
  if (game.status !== "playing") return false;
  const remaining = game.players.filter((player) => !player.eliminated && !player.exited);
  if (remaining.length <= 1) {
    const winner = remaining[0] || null;
    game.status = "finished";
    game.finishedAt = now;
    game.winnerId = winner ? winner.clientId : null;
    assignRanks(game);
    if (winner) addLog(game, `祝贺 ${winner.username} 胜利。`);
    return true;
  }
  ensureTurnPlayerCanPlay(game, now);
  return false;
}

function assignRanks(game) {
  const total = game.players.length;
  const eliminated = game.eliminatedOrder.slice();
  const alive = game.players.filter((player) => !player.eliminated && !player.exited);
  alive.forEach((player) => {
    player.rank = 1;
  });
  eliminated.forEach((clientId, index) => {
    const player = getPlayer(game, clientId);
    if (player) player.rank = total - index;
  });
  game.players.forEach((player) => {
    if (!player.rank) player.rank = total;
  });
}

function markConnection(game, clientId, connected, { disconnectProtection = true } = {}) {
  const player = getPlayer(game, clientId);
  if (!player) return false;
  player.connected = connected;
  if (!connected) {
    if (!disconnectProtection && !player.eliminated) {
      player.exited = true;
      eliminatePlayer(game, clientId, "掉线");
    }
  } else {
    player.exited = false;
  }
  return true;
}

function summarizeGameForStats(game) {
  return {
    gameId: game.id,
    roomId: game.roomId,
    at: game.finishedAt || Date.now(),
    playerCount: game.players.length,
    playCount: game.playCount,
    bellCount: game.bellCount,
    successBellCount: game.successBellCount,
    failBellCount: game.failBellCount,
    winnerId: game.winnerId,
    averageRoundLength: game.successBellCount > 0 ? game.playCount / game.successBellCount : game.playCount,
    players: game.players.map((player) => ({
      clientId: player.clientId,
      username: player.username,
      rank: player.rank || null,
      stats: clone(player.stats),
    })),
  };
}

module.exports = {
  DEFAULT_SETTINGS,
  normalizeSettings,
  shuffle,
  dealCards,
  createGame,
  publicGame,
  addLog,
  getPlayer,
  activePlayers,
  tableTopCards,
  findMatchFromTopCards,
  findCurrentMatch,
  findBellMatch,
  turnTimeoutMs,
  setTurnTiming,
  startPlaying,
  performPlayCard,
  performRingBell,
  eliminatePlayer,
  checkGameOver,
  markConnection,
  summarizeGameForStats,
};
