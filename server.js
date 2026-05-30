"use strict";

const http = require("http");
const fs = require("fs");
const path = require("path");
const os = require("os");
const crypto = require("crypto");
const {
  normalizeSettings,
  createGame,
  publicGame,
  startPlaying,
  performPlayCard,
  performRingBell,
  findCurrentMatch,
  markConnection,
  checkGameOver,
  setTurnTiming,
  summarizeGameForStats,
} = require("./src/gameCore");
const { discoverCardLibraries, cardsGroupedByPmv } = require("./src/cardLibraries");

const ROOT_DIR = __dirname;
const DEFAULT_PORT = Number.parseInt(process.env.PORT || "3000", 10);
const DEFAULT_HOST = process.env.HOST || "0.0.0.0";
const PASSWORD_ITERATIONS = 210000;
const PASSWORD_KEY_LENGTH = 32;
const PASSWORD_DIGEST = "sha256";
const SESSION_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const WAITING_DISCONNECT_KICK_MS = 2 * 60 * 1000;
const LOADING_DISCONNECT_KICK_MS = 10 * 1000;
const GOD_COMPUTER_ID = "computer_god";

function createApp(options = {}) {
  const rootDir = options.rootDir || ROOT_DIR;
  const dataFile = options.dataFile || path.join(rootDir, "data", "state.json");
  const publicDir = path.join(rootDir, "public");
  const cardLibraries = discoverCardLibraries(rootDir);
  const computerPlayers = loadComputerPlayers(rootDir);
  const data = loadData(dataFile);
  const state = {
    players: new Map(),
    sessions: new Map(),
    rooms: new Map(),
    games: new Map(),
    streams: new Map(),
    disconnectTimers: new Map(),
    data,
  };
  computerPlayers.forEach((computer) => {
    const stats = ensureStats(data, `computer:${computer.id}`, computer.name);
    stats.isComputer = true;
    stats.computerId = computer.id;
  });

  const server = http.createServer((req, res) => {
    handleRequest(req, res, { rootDir, publicDir, dataFile, cardLibraries, computerPlayers, state })
      .catch((error) => {
        const statusCode = error.statusCode || 500;
        if (statusCode >= 500) console.error(error);
        sendJson(res, statusCode, { error: error.message || "Internal server error" });
      });
  });

  const tick = setInterval(() => {
    let changed = false;
    const now = Date.now();
    for (const room of state.rooms.values()) {
      if (room.status === "waiting" && pruneWaitingDisconnectedPlayers(room, state, now)) {
        changed = true;
      }
      if (room.status === "loading" && pruneLoadingDisconnectedPlayers(room, state, now)) {
        changed = true;
      }
      if (room.status === "waiting" && room.startAt && now >= room.startAt) {
        try {
          startRoomGame(room, state, cardLibraries);
          changed = true;
        } catch (error) {
          room.startCountdownStartedAt = null;
          room.startAt = null;
          changed = true;
        }
      }
    }
    for (const game of state.games.values()) {
      if (game.status === "finished") {
        const room = state.rooms.get(game.roomId);
        if (room && room.gameId === game.id && maybeReturnToWaiting(room, game, state)) changed = true;
        continue;
      }
      if (game.status !== "playing") continue;
      const computerChanged = advanceComputerPlayers(game, state, now);
      if (computerChanged.played) emitAudioEvent(state, "play-card", { roomId: game.roomId, gameId: game.id });
      if (computerChanged.rang) emitAudioEvent(state, "ring-bell", { roomId: game.roomId, gameId: game.id });
      if (computerChanged.changed) {
        finalizeGameIfNeeded(game, state, dataFile);
        changed = true;
        continue;
      }
      if (game.lockedUntil > now) continue;
      const player = game.players[game.turnIndex];
      if (player && player.connected === false && game.settings.disconnectProtection) {
        const disconnectedDeadline = (game.turnStartedAt || game.turnAvailableAt || now) + 2000;
        if (game.turnDeadlineAt > disconnectedDeadline) game.turnDeadlineAt = disconnectedDeadline;
      }
      if (player && !player.eliminated && !player.exited && player.drawPile.length > 0 && now >= game.turnDeadlineAt) {
        const result = performPlayCard(game, player.clientId, { now, auto: true });
        changed = true;
        finalizeGameIfNeeded(game, state, dataFile);
        if (result.ok) emitAudioEvent(state, "play-card", { roomId: game.roomId, gameId: game.id });
      }
    }
    if (changed) broadcast(state);
  }, 100);

  server.on("close", () => clearInterval(tick));
  return { server, state, cardLibraries };
}

async function handleRequest(req, res, context) {
  const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);
  const pathname = decodeURIComponent(url.pathname);

  if (req.method === "GET" && pathname === "/api/events") {
    return handleEvents(req, res, url, context);
  }

  if (pathname.startsWith("/api/")) {
    return handleApi(req, res, url, pathname, context);
  }

  return serveStatic(req, res, pathname, context);
}

async function handleApi(req, res, url, pathname, context) {
  const { state, cardLibraries, computerPlayers, dataFile } = context;
  const body = req.method === "POST" ? await readBody(req) : {};

  if (req.method === "GET" && pathname === "/api/bootstrap") {
    const auth = optionalAuth(req, url, body, context);
    return sendJson(res, 200, snapshotFor(auth?.clientId || "", state, cardLibraries, computerPlayers));
  }

  if (req.method === "GET" && pathname === "/api/card-libraries") {
    return sendJson(res, 200, publicLibraries(cardLibraries));
  }

  if (req.method === "GET" && pathname === "/api/computer-players") {
    return sendJson(res, 200, { players: publicComputerPlayers(computerPlayers) });
  }

  if (req.method === "GET" && pathname === "/api/card-viewer") {
    const requested = url.searchParams.getAll("libraryIds")
      .flatMap((value) => value.split(","))
      .map((value) => value.trim())
      .filter(Boolean);
    return sendJson(res, 200, cardViewerPayload(requested, cardLibraries));
  }

  if (req.method === "GET" && pathname === "/api/pmv-index") {
    return sendJson(res, 200, pmvIndexPayload(cardLibraries));
  }

  if (req.method === "POST" && pathname === "/api/register") {
    const username = cleanName(requiredString(body.username, "username"));
    const password = requiredString(body.password, "password");
    if (password.length < 6) throw httpError(400, "密码至少需要 6 个字符。");
    ensureUsernameAvailable(state, username);
    const user = createUser(state, username, password);
    const player = upsertPlayerFromUser(state, user, getClientIp(req));
    const token = createSession(state, user.clientId);
    saveData(state.data, dataFile);
    broadcast(state);
    return sendJson(res, 201, { token, player: publicPlayer(player) });
  }

  if (req.method === "POST" && pathname === "/api/login") {
    const username = cleanName(requiredString(body.username, "username"));
    const password = requiredString(body.password, "password");
    const user = findUserByUsername(state.data, username);
    if (!user) throw httpError(401, "用户名或密码不正确。");
    const verification = verifyPassword(user, password);
    if (!verification.ok) throw httpError(401, "用户名或密码不正确。");
    if (verification.resetPassword) setUserPassword(user, password);
    user.lastLoginAt = Date.now();
    const player = upsertPlayerFromUser(state, user, getClientIp(req));
    const token = createSession(state, user.clientId);
    saveData(state.data, dataFile);
    broadcast(state);
    return sendJson(res, 200, { token, player: publicPlayer(player), passwordReset: verification.resetPassword });
  }

  if (req.method === "POST" && pathname === "/api/logout") {
    const token = tokenFromRequest(req, url, body);
    const session = token ? state.sessions.get(token) : null;
    if (token) state.sessions.delete(token);
    if (session) {
      setConnected(state, session.clientId, false);
      broadcast(state);
    }
    return sendJson(res, 200, { ok: true });
  }

  if (req.method === "POST" && pathname === "/api/player") {
    return sendJson(res, 410, { error: "现在需要注册账号并登录；用户名注册后不能直接修改，请联系管理员处理。" });
  }

  if (req.method === "GET" && pathname === "/api/rooms") {
    const { clientId } = requireAuth(req, url, body, context);
    const includePrivate = url.searchParams.get("all") === "1";
    const rooms = [...state.rooms.values()]
      .filter((room) => includePrivate || room.settings.isPublic || room.players.includes(clientId) || room.spectators.includes(clientId))
      .map((room) => roomSummary(room, state));
    return sendJson(res, 200, { rooms });
  }

  if (req.method === "POST" && pathname === "/api/rooms") {
    const { player } = requireAuth(req, url, body, context);
    const room = createRoom(state, player, body.settings || {}, cardLibraries);
    (Array.isArray(body.computerIds) ? body.computerIds : []).forEach((computerId) => {
      addComputerToRoom(room, String(computerId), state, computerPlayers);
    });
    evaluateStartVotes(room, state);
    broadcast(state);
    return sendJson(res, 201, { room: roomSummary(room, state) });
  }

  const roomAssetMatch = /^\/api\/rooms\/([^/]+)\/assets$/.exec(pathname);
  if (roomAssetMatch && req.method === "GET") {
    const { clientId } = requireAuth(req, url, body, context);
    const room = state.rooms.get(roomAssetMatch[1]);
    if (!room) return sendJson(res, 404, { error: "房间不存在。" });
    if (!room.players.includes(clientId) && !room.spectators.includes(clientId)) {
      throw httpError(403, "只有房间内玩家可以加载该房间资源。");
    }
    return sendJson(res, 200, assetManifestForRoom(room, cardLibraries));
  }

  const roomMatch = /^\/api\/rooms\/([^/]+)\/([^/]+)$/.exec(pathname);
  if (roomMatch && req.method === "POST") {
    const roomId = roomMatch[1];
    const action = roomMatch[2];
    const room = state.rooms.get(roomId);
    if (!room) return sendJson(res, 404, { error: "房间不存在。" });
    const { clientId } = requireAuth(req, url, body, context);

    if (action === "join") {
      const player = ensureKnownPlayer(state, clientId);
      const result = joinRoom(room, player, state);
      evaluateStartVotes(room, state);
      broadcast(state);
      return sendJson(res, 200, result);
    }

    if (action === "leave") {
      const affectedGame = leaveRoom(room, clientId, state);
      if (affectedGame?.status === "loading" && state.rooms.has(room.id)) {
        advanceLoading(room, affectedGame, state);
      }
      if (affectedGame) finalizeGameIfNeeded(affectedGame, state, dataFile);
      if (state.rooms.has(room.id)) evaluateStartVotes(room, state);
      broadcast(state);
      return sendJson(res, 200, { ok: true });
    }

    if (action === "settings") {
      ensureHost(room, clientId);
      resetFinishedRoomToWaiting(room, state);
      if (room.status !== "waiting") throw new Error("只有等待中房间可以修改设置。");
      room.settings = {
        ...room.settings,
        ...normalizeSettings({ ...room.settings, ...(body.settings || {}) }, cardLibraries.map((library) => library.id), libraryCardCountMap(cardLibraries)),
        minPlayers: room.settings.minPlayers,
        maxPlayers: room.settings.maxPlayers,
        isPublic: room.settings.isPublic,
      };
      evaluateStartVotes(room, state);
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state) });
    }

    if (action === "add-computer") {
      ensureHost(room, clientId);
      if (room.status !== "waiting") throw new Error("只有等待中房间可以邀请人机。");
      const computerId = requiredString(body.computerId, "computerId");
      addComputerToRoom(room, computerId, state, computerPlayers);
      evaluateStartVotes(room, state);
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state) });
    }

    if (action === "remove-computer") {
      ensureHost(room, clientId);
      if (room.status !== "waiting") throw new Error("只有等待中房间可以移除人机。");
      removeComputerFromRoom(room, requiredString(body.computerId, "computerId"), state);
      evaluateStartVotes(room, state);
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state) });
    }

    if (action === "start-vote") {
      if (!room.players.includes(clientId)) throw httpError(403, "只有房间内玩家可以投票开始。");
      if (!room.startVotes.includes(clientId)) room.startVotes.push(clientId);
      evaluateStartVotes(room, state);
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state) });
    }

    if (action === "cancel-start-vote") {
      room.startVotes = (room.startVotes || []).filter((id) => id !== clientId);
      evaluateStartVotes(room, state);
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state) });
    }

    if (action === "chat") {
      if (!room.players.includes(clientId) && !room.spectators.includes(clientId)) {
        throw httpError(403, "只有房间内成员可以聊天。");
      }
      addChatMessage(room, ensureKnownPlayer(state, clientId), body.message);
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state) });
    }

    if (action === "disband") {
      ensureHost(room, clientId);
      disbandRoom(room, state);
      broadcast(state);
      return sendJson(res, 200, { ok: true });
    }

    if (action === "transfer-host") {
      ensureHost(room, clientId);
      const newHostId = requiredString(body.newHostId, "newHostId");
      if (!room.players.includes(newHostId)) throw new Error("只能转让给房间内玩家。");
      room.hostId = newHostId;
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state) });
    }

    if (action === "start") {
      ensureHost(room, clientId);
      const game = startRoomGame(room, state, cardLibraries);
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state), game: publicGameFor(game, state.data) });
    }

    if (action === "loading-ready") {
      const game = state.games.get(room.gameId);
      if (!game || game.status !== "loading") throw new Error("当前房间不在加载阶段。");
      const player = game.players.find((item) => item.clientId === clientId);
      if (player) updateLoadingProgress(player, {
        loaded: player.loadingTotal || 1,
        total: player.loadingTotal || 1,
        done: true,
      });
      advanceLoading(room, game, state);
      finalizeGameIfNeeded(game, state, dataFile);
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state), game: publicGameFor(game, state.data) });
    }

    if (action === "loading-progress") {
      const game = state.games.get(room.gameId);
      if (!game || game.status !== "loading") throw new Error("当前房间不在加载阶段。");
      const player = game.players.find((item) => item.clientId === clientId);
      if (!player) throw httpError(403, "你不在这局游戏中。");
      updateLoadingProgress(player, body);
      advanceLoading(room, game, state);
      finalizeGameIfNeeded(game, state, dataFile);
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state), game: publicGameFor(game, state.data) });
    }
  }

  const gameMatch = /^\/api\/games\/([^/]+)\/([^/]+)$/.exec(pathname);
  if (gameMatch && req.method === "POST") {
    const gameId = gameMatch[1];
    const action = gameMatch[2];
    const game = state.games.get(gameId);
    if (!game) return sendJson(res, 404, { error: "对局不存在。" });
    const { clientId } = requireAuth(req, url, body, context);

    if (action === "play-card") {
      const result = performPlayCard(game, clientId, { now: Date.now() });
      if (!result.ok) return sendJson(res, 409, { error: result.error });
      finalizeGameIfNeeded(game, state, dataFile);
      broadcast(state);
      emitAudioEvent(state, "play-card", { roomId: game.roomId, gameId: game.id });
      return sendJson(res, 200, { game: publicGameFor(game, state.data) });
    }

    if (action === "ring-bell") {
      const result = performRingBell(game, clientId, { now: Date.now() });
      if (!result.ok) return sendJson(res, 409, { error: result.error });
      finalizeGameIfNeeded(game, state, dataFile);
      broadcast(state);
      emitAudioEvent(state, "ring-bell", { roomId: game.roomId, gameId: game.id });
      return sendJson(res, 200, { game: publicGameFor(game, state.data) });
    }

    if (action === "continue") {
      const room = state.rooms.get(game.roomId);
      if (!room) throw new Error("房间不存在。");
      if (room.gameId !== game.id) throw new Error("这局游戏已经结算完成。");
      if (!game.continueVotes.includes(clientId)) game.continueVotes.push(clientId);
      maybeReturnToWaiting(room, game, state);
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state), game: publicGameFor(game, state.data) });
    }
  }

  const profileMatch = /^\/api\/profile\/([^/]+)$/.exec(pathname);
  if (profileMatch && req.method === "GET") {
    const { clientId } = requireAuth(req, url, body, context);
    if (profileMatch[1] !== clientId) throw httpError(403, "只能查看自己的个人信息。");
    return sendJson(res, 200, { profile: profileFor(clientId, state.data) });
  }

  if (req.method === "GET" && pathname === "/api/leaderboard") {
    return sendJson(res, 200, leaderboardFor(state.data));
  }

  return sendJson(res, 404, { error: "Not found" });
}

function handleEvents(req, res, url, context) {
  const { state } = context;
  let clientId;
  try {
    ({ clientId } = requireAuth(req, url, {}, context));
  } catch (error) {
    return sendJson(res, error.statusCode || 401, { error: error.message || "请先登录。" });
  }

  res.writeHead(200, {
    "Content-Type": "text/event-stream; charset=utf-8",
    "Cache-Control": "no-cache, no-transform",
    Connection: "keep-alive",
  });
  res.write("event: hello\ndata: {}\n\n");

  if (!state.streams.has(clientId)) state.streams.set(clientId, new Set());
  state.streams.get(clientId).add(res);
  clearTimeout(state.disconnectTimers.get(clientId));
  state.disconnectTimers.delete(clientId);
  setConnected(state, clientId, true);
  broadcast(state);

  const heartbeat = setInterval(() => {
    if (!res.destroyed) res.write("event: ping\ndata: {}\n\n");
  }, 15000);

  req.on("close", () => {
    clearInterval(heartbeat);
    const streams = state.streams.get(clientId);
    if (streams) {
      streams.delete(res);
      if (streams.size === 0) state.streams.delete(clientId);
    }
    const timer = setTimeout(() => {
      if (!state.streams.has(clientId)) {
        setConnected(state, clientId, false);
        broadcast(state);
      }
    }, 3500);
    state.disconnectTimers.set(clientId, timer);
  });
}

function createRoom(state, player, settingsInput, cardLibraries) {
  leaveOtherRooms(state, player.clientId);
  const roomId = allocateRoomId(state);
  const settings = normalizeSettings(settingsInput, cardLibraries.map((library) => library.id), libraryCardCountMap(cardLibraries));
  const room = {
    id: roomId,
    hostId: player.clientId,
    players: [player.clientId],
    spectators: [],
    status: "waiting",
    settings,
    gameId: null,
    lastWinnerId: null,
    startVotes: [],
    startCountdownStartedAt: null,
    startAt: null,
    chatMessages: [],
    createdAt: Date.now(),
  };
  state.rooms.set(roomId, room);
  player.currentRoomId = roomId;
  return room;
}

function joinRoom(room, player, state) {
  resetFinishedRoomToWaiting(room, state);
  leaveOtherRooms(state, player.clientId, room.id);
  const game = room.gameId ? state.games.get(room.gameId) : null;
  if (room.status === "waiting") {
    if (!room.players.includes(player.clientId)) {
      if (room.players.length >= room.settings.maxPlayers) throw new Error("房间人数已满。");
      room.players.push(player.clientId);
    }
    room.spectators = room.spectators.filter((id) => id !== player.clientId);
    player.currentRoomId = room.id;
    return { room: roomSummary(room, state), spectator: false };
  }

  if (game && game.players.some((item) => item.clientId === player.clientId)) {
    markConnection(game, player.clientId, true, { disconnectProtection: game.settings.disconnectProtection });
    player.currentRoomId = room.id;
    return { room: roomSummary(room, state), game: publicGameFor(game, state.data), spectator: false };
  }

  if (!room.spectators.includes(player.clientId)) room.spectators.push(player.clientId);
  if (game && !game.spectators.includes(player.clientId)) game.spectators.push(player.clientId);
  player.currentRoomId = room.id;
  return { room: roomSummary(room, state), game: game ? publicGameFor(game, state.data) : null, spectator: true };
}

function libraryCardCountMap(cardLibraries) {
  return new Map(cardLibraries.map((library) => [library.id, library.cardCount]));
}

function expandedCardsForRoom(room, cardLibraries) {
  const selectedIds = new Set(room.settings.libraryIds);
  return cardLibraries
    .filter((library) => selectedIds.has(library.id))
    .flatMap((library) => {
      const copies = Math.max(1, Number.parseInt(room.settings.libraryCopies?.[library.id], 10) || 1);
      return Array.from({ length: copies }, (_, copyIndex) => library.cards.map((card) => ({
        ...card,
        id: copies === 1 ? card.id : `${card.id}#copy${copyIndex + 1}`,
        copyIndex: copyIndex + 1,
      }))).flat();
    });
}

function addComputerToRoom(room, computerId, state, computerPlayers) {
  if (room.players.length >= room.settings.maxPlayers) throw new Error("房间人数已满。");
  if (room.players.some((clientId) => state.players.get(clientId)?.computerId === computerId)) {
    throw new Error("该人机已经在房间中。");
  }
  const profile = computerPlayers.find((computer) => computer.id === computerId);
  if (!profile) throw new Error("人机不存在。");
  const clientId = `computer:${room.id}:${profile.id}`;
  const player = {
    clientId,
    username: profile.name,
    connected: true,
    isComputer: true,
    computerId: profile.id,
    statsId: `computer:${profile.id}`,
    currentRoomId: room.id,
    joinedAt: Date.now(),
    lastSeenAt: Date.now(),
    profile,
  };
  state.players.set(clientId, player);
  room.players.push(clientId);
  if (!room.startVotes.includes(clientId)) room.startVotes.push(clientId);
  ensureStats(state.data, player.statsId, player.username).isComputer = true;
  ensureStats(state.data, player.statsId, player.username).computerId = profile.id;
  return player;
}

function removeComputerFromRoom(room, computerId, state) {
  const clientId = room.players.find((id) => state.players.get(id)?.computerId === computerId);
  if (!clientId) throw new Error("该人机不在房间中。");
  room.players = room.players.filter((id) => id !== clientId);
  room.startVotes = (room.startVotes || []).filter((id) => id !== clientId);
  state.players.delete(clientId);
}

function startVoteRequirement(room) {
  const currentPlayers = room.players.length;
  if (room.settings.startVoteThresholdMode === "manual" && room.settings.startVoteThreshold) {
    return Math.max(1, Math.min(currentPlayers || 1, Number.parseInt(room.settings.startVoteThreshold, 10) || 1));
  }
  return Math.max(1, currentPlayers - 2);
}

function evaluateStartVotes(room, state, now = Date.now()) {
  if (room.status !== "waiting") return false;
  const before = `${room.startCountdownStartedAt || ""}:${room.startAt || ""}`;
  const voteSet = new Set((room.startVotes || []).filter((clientId) => room.players.includes(clientId)));
  room.players.forEach((clientId) => {
    if (state.players.get(clientId)?.isComputer) voteSet.add(clientId);
  });
  room.startVotes = [...voteSet];
  const validVotes = room.startVotes.length;
  const required = startVoteRequirement(room);
  const ready = room.players.length >= room.settings.minPlayers && validVotes >= required;
  if (!ready) {
    room.startCountdownStartedAt = null;
    room.startAt = null;
  } else if (!room.startAt) {
    room.startCountdownStartedAt = now;
    room.startAt = now + 10000;
  }
  return before !== `${room.startCountdownStartedAt || ""}:${room.startAt || ""}`;
}

function pruneWaitingDisconnectedPlayers(room, state, now = Date.now()) {
  if (room.status !== "waiting") return false;
  const beforePlayers = room.players.length;
  const beforeSpectators = room.spectators.length;
  const shouldKick = (clientId) => {
    const player = state.players.get(clientId);
    if (!player || player.isComputer || player.connected !== false) return false;
    return now - (player.lastSeenAt || now) >= WAITING_DISCONNECT_KICK_MS;
  };
  const kickedIds = new Set([...room.players, ...room.spectators].filter(shouldKick));
  if (kickedIds.size === 0) return false;
  kickedIds.forEach((clientId) => {
    const player = state.players.get(clientId);
    if (player?.currentRoomId === room.id) player.currentRoomId = null;
  });
  room.players = room.players.filter((clientId) => !kickedIds.has(clientId));
  room.spectators = room.spectators.filter((clientId) => !kickedIds.has(clientId));
  room.startVotes = (room.startVotes || []).filter((clientId) => room.players.includes(clientId));
  if (room.hostId && kickedIds.has(room.hostId)) migrateHost(room, state);
  evaluateStartVotes(room, state, now);
  if (!hasHumanOccupants(room, state)) {
    deleteRoom(room, state);
  }
  return room.players.length !== beforePlayers || room.spectators.length !== beforeSpectators;
}

function pruneLoadingDisconnectedPlayers(room, state, now = Date.now()) {
  if (room.status !== "loading") return false;
  const game = room.gameId ? state.games.get(room.gameId) : null;
  if (!game || game.status !== "loading") return false;
  const shouldKick = (clientId) => {
    const player = state.players.get(clientId);
    if (!player || player.isComputer || player.connected !== false) return false;
    return now - (player.lastSeenAt || now) >= LOADING_DISCONNECT_KICK_MS;
  };
  const kickedIds = new Set(room.players.filter(shouldKick));
  if (kickedIds.size === 0) return false;

  kickedIds.forEach((clientId) => {
    const player = state.players.get(clientId);
    if (player?.currentRoomId === room.id) player.currentRoomId = null;
    const gamePlayer = game.players.find((item) => item.clientId === clientId);
    if (gamePlayer) {
      gamePlayer.connected = false;
      gamePlayer.exited = true;
      gamePlayer.ready = false;
    }
  });
  room.players = room.players.filter((clientId) => !kickedIds.has(clientId));
  room.startVotes = (room.startVotes || []).filter((clientId) => room.players.includes(clientId));
  if (room.hostId && kickedIds.has(room.hostId)) migrateHost(room, state);

  if (!hasHumanOccupants(room, state)) {
    deleteRoom(room, state);
    return true;
  }

  advanceLoading(room, game, state);
  return true;
}

function addChatMessage(room, player, rawMessage) {
  const message = sanitizeChatMessage(rawMessage);
  if (!message) throw new Error("聊天内容不能为空。");
  if (!Array.isArray(room.chatMessages)) room.chatMessages = [];
  room.chatMessages.push({
    id: crypto.randomUUID(),
    at: Date.now(),
    clientId: player.clientId,
    username: player.username,
    message,
  });
  if (room.chatMessages.length > 100) room.chatMessages.splice(0, room.chatMessages.length - 100);
}

function sanitizeChatMessage(value) {
  return [...String(value || "").replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ").trim()]
    .slice(0, 40)
    .join("");
}

function advanceComputerPlayers(game, state, now = Date.now()) {
  const result = { changed: false, played: false, rang: false };
  for (const player of game.players) {
    if (!player.isComputer || player.eliminated || player.exited) continue;
    const profile = state.players.get(player.clientId)?.profile;
    if (!profile) continue;
    if (!player.computerState) player.computerState = computerState("start", game);
    const stateName = player.computerState.name;

    if (stateName === "start") {
      player.computerState = computerState(!tableHasDisplayCards(game) && canComputerPlay(game, player, now) ? "play" : "wait", game);
      continue;
    }

    if (stateName === "wait") {
      if (game.playCount !== player.computerState.observedPlayCount) {
        player.computerState = computerState("analysis", game);
      } else if (canComputerPlay(game, player, now)) {
        player.computerState = computerState("play", game);
      }
      continue;
    }

    if (stateName === "play") {
      if (game.bellCount !== player.computerState.observedBellCount) {
        player.computerState = game.lockedUntil > now
          ? computerState("settling", game, { waitUntil: game.lockedUntil })
          : computerState("next", game);
        continue;
      }
      if (game.playCount !== player.computerState.observedPlayCount) {
        player.computerState = computerState("analysis", game);
        continue;
      }
      if (!canComputerPlay(game, player, now)) {
        if (game.players[game.turnIndex]?.clientId !== player.clientId) {
          player.computerState = computerState("analysis", game);
        }
        continue;
      }
      if (!player.computerState.actionAt) {
        player.computerState.actionAt = Math.max(now, game.turnAvailableAt || now) + sampleClampedMs(profile.playDelayMeanSeconds, profile.playDelayStdSeconds, 1500, 7000);
        continue;
      }
      if (now >= player.computerState.actionAt) {
        const played = performPlayCard(game, player.clientId, { now, auto: true });
        player.computerState = computerState("analysis", game);
        if (played.ok) {
          result.changed = true;
          result.played = true;
          return result;
        }
      }
      continue;
    }

    if (stateName === "analysis") {
      const match = findCurrentMatch(game);
      const shouldRing = match
        ? Math.random() < profile.matchDetectionProbability
        : Math.random() < profile.falseRingProbability;
      player.computerState = shouldRing
        ? computerState("ring", game, {
            actionAt: now + sampleClampedMs(profile.reactionMeanSeconds, profile.reactionStdSeconds, 100, Number.MAX_SAFE_INTEGER),
          })
        : computerState("next", game);
      continue;
    }

    if (stateName === "ring") {
      if (game.bellCount !== player.computerState.observedBellCount) {
        player.computerState = game.lockedUntil > now
          ? computerState("settling", game, { waitUntil: game.lockedUntil })
          : computerState("next", game);
        continue;
      }
      if (game.playCount !== player.computerState.observedPlayCount) {
        player.computerState = computerState("analysis", game);
        continue;
      }
      if (game.lockedUntil > now) {
        player.computerState = computerState("settling", game, { waitUntil: game.lockedUntil });
        continue;
      }
      if (now >= player.computerState.actionAt) {
        const rang = performRingBell(game, player.clientId, { now });
        player.computerState = rang.ok && game.lockedUntil > now
          ? computerState("settling", game, { waitUntil: game.lockedUntil })
          : computerState("next", game);
        if (rang.ok) {
          result.changed = true;
          result.rang = true;
          return result;
        }
      }
      continue;
    }

    if (stateName === "settling") {
      if (now >= (player.computerState.waitUntil || 0)) {
        player.computerState = computerState("next", game);
      }
      continue;
    }

    if (stateName === "next") {
      player.computerState = computerState(canComputerPlay(game, player, now) ? "play" : "wait", game);
    }
  }
  return result;
}

function computerState(name, game, extra = {}) {
  return {
    name,
    observedPlayCount: game.playCount,
    observedBellCount: game.bellCount,
    ...extra,
  };
}

function tableHasDisplayCards(game) {
  return game.players.some((player) => player.displayPile.length > 0);
}

function canComputerPlay(game, player, now) {
  return game.status === "playing"
    && game.lockedUntil <= now
    && game.players[game.turnIndex]?.clientId === player.clientId
    && !player.eliminated
    && !player.exited
    && player.drawPile.length > 0
    && now >= (game.turnAvailableAt || 0);
}

function sampleClampedMs(meanSeconds, stdSeconds, minMs, maxMs) {
  const mean = Number(meanSeconds) || 0;
  const std = Math.max(0, Number(stdSeconds) || 0);
  const sampled = std === 0 ? mean : mean + gaussianRandom() * std;
  return Math.max(minMs, Math.min(maxMs, Math.round(sampled * 1000)));
}

function gaussianRandom() {
  let u = 0;
  let v = 0;
  while (u === 0) u = Math.random();
  while (v === 0) v = Math.random();
  return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}

function startRoomGame(room, state, cardLibraries) {
  if (room.status !== "waiting") throw new Error("房间不在等待状态。");
  if (room.players.length < room.settings.minPlayers) throw new Error("人数不足，无法开始游戏。");
  const cards = expandedCardsForRoom(room, cardLibraries);
  if (cards.length < room.players.length) throw new Error("选中的卡牌太少，无法发牌。");
  const players = room.players.map((clientId) => ensureKnownPlayer(state, clientId));
  const game = createGame({ room, players, cards });
  state.games.set(game.id, game);
  room.gameId = game.id;
  room.status = "loading";
  room.startVotes = [];
  room.startCountdownStartedAt = null;
  room.startAt = null;
  return game;
}

function advanceLoading(room, game, state) {
  const expectedIds = new Set(room.players);
  const expectedPlayers = game.players.filter((player) => expectedIds.has(player.clientId) && !player.exited);
  if (expectedPlayers.length < game.settings.minPlayers) {
    room.status = "waiting";
    room.gameId = null;
    game.status = "aborted";
    return;
  }
  const allExpectedReady = expectedPlayers.every((player) => player.ready);
  if (allExpectedReady) {
    room.status = "playing";
    startPlaying(game, Date.now());
  }
}

function updateLoadingProgress(player, input = {}) {
  const previousTotal = Math.max(0, player.loadingTotal || 0);
  const previousLoaded = Math.max(0, player.loadingLoaded || 0);
  const reportedTotal = Math.max(0, Number.parseInt(input.total, 10) || 0);
  const total = Math.max(previousTotal, reportedTotal);
  const reportedLoaded = Math.max(0, Number.parseInt(input.loaded, 10) || 0);
  const loaded = Math.max(previousLoaded, Math.min(total || Number.MAX_SAFE_INTEGER, reportedLoaded));
  player.loadingTotal = total;
  player.loadingLoaded = loaded;
  player.loadingProgress = Math.max(player.loadingProgress || 0, total > 0 ? Math.round((loaded / total) * 100) : 0);
  player.loadingCached = player.loadingCached || Boolean(input.cached);
  player.loadingManifestKey = typeof input.manifestKey === "string" ? input.manifestKey.slice(0, 80) : player.loadingManifestKey || "";
  player.loadingStartedAt = player.loadingStartedAt || Date.now();
  if (player.ready || input.done || (total > 0 && loaded >= total)) {
    player.loadingLoaded = total || loaded;
    player.loadingProgress = 100;
    player.ready = true;
    player.loadingFinishedAt = player.loadingFinishedAt || Date.now();
  }
}

function maybeReturnToWaiting(room, game, state, now = Date.now()) {
  if (game.status !== "finished") return false;
  const connectedHumanIds = game.players
    .filter((player) => !player.isComputer && player.connected !== false && !player.exited)
    .map((player) => player.clientId);
  const validVotes = game.continueVotes.filter((clientId) => connectedHumanIds.includes(clientId));
  const threshold = Math.max(1, Math.floor(connectedHumanIds.length / 2) + 1);

  if (!game.continueReturnAt) {
    if (validVotes.length < threshold) return false;
    game.continueCountdownStartedAt = now;
    game.continueReturnAt = now + 10000;
    return true;
  }

  if (now < game.continueReturnAt && validVotes.length < connectedHumanIds.length) return false;

  game.players.forEach((gamePlayer) => {
    if (!gamePlayer.isComputer && !validVotes.includes(gamePlayer.clientId)) {
      const player = state.players.get(gamePlayer.clientId);
      if (player?.currentRoomId === room.id) player.currentRoomId = null;
    }
  });
  const computerIds = game.players
    .filter((player) => player.isComputer && !player.exited && state.players.has(player.clientId))
    .map((player) => player.clientId);
  room.players = [...validVotes.filter((clientId) => state.players.has(clientId)), ...computerIds];
  room.players.forEach((clientId) => {
    const player = state.players.get(clientId);
    if (player) player.currentRoomId = room.id;
  });
  room.spectators = [];
  room.status = "waiting";
  room.gameId = null;
  room.lastWinnerId = game.winnerId;
  migrateHost(room, state);
  return true;
}

function resetFinishedRoomToWaiting(room, state) {
  const game = room.gameId ? state.games.get(room.gameId) : null;
  if (room.status !== "finished" || game?.status !== "finished") return false;
  const remainingGamePlayers = new Set(game.players
    .filter((player) => !player.exited)
    .map((player) => player.clientId));
  game.players
    .filter((player) => player.exited)
    .forEach((gamePlayer) => {
      const player = state.players.get(gamePlayer.clientId);
      if (player?.currentRoomId === room.id) player.currentRoomId = null;
    });
  room.players = [...new Set(room.players.filter((clientId) => state.players.has(clientId) && remainingGamePlayers.has(clientId)))];
  room.spectators = [];
  room.status = "waiting";
  room.gameId = null;
  room.lastWinnerId = game.winnerId;
  room.players.forEach((clientId) => {
    const player = state.players.get(clientId);
    if (player) player.currentRoomId = room.id;
  });
  migrateHost(room, state);
  return true;
}

function deleteRoom(room, state) {
  const game = room.gameId ? state.games.get(room.gameId) : null;
  const ids = new Set([...room.players, ...room.spectators]);
  ids.forEach((clientId) => {
    const player = state.players.get(clientId);
    if (player?.currentRoomId === room.id) player.currentRoomId = null;
    if (player?.isComputer) state.players.delete(clientId);
  });
  if (game) {
    if (game.status !== "finished") game.status = "aborted";
    state.games.delete(game.id);
  }
  state.rooms.delete(room.id);
}

function disbandRoom(room, state) {
  const game = room.gameId ? state.games.get(room.gameId) : null;
  if (game) {
    game.players.forEach((player) => {
      player.connected = false;
      player.exited = true;
    });
    game.spectators = [];
  }
  deleteRoom(room, state);
}

function finalizeGameIfNeeded(game, state, dataFile) {
  if (game.status !== "finished" || game.statsSaved) return;
  game.statsSaved = true;
  const summary = summarizeGameForStats(game);
  state.data.matchHistory.unshift(summary);
  if (state.data.matchHistory.length > 200) state.data.matchHistory.length = 200;
  summary.players.forEach((entry) => updatePlayerStats(state.data, entry, summary));
  updateComputerDefeatStats(state.data, summary);
  const room = state.rooms.get(game.roomId);
  if (room) {
    room.status = "finished";
    room.lastWinnerId = game.winnerId;
  }
  saveData(state.data, dataFile);
}

function updatePlayerStats(data, playerEntry, matchSummary) {
  const statsId = playerEntry.statsId || playerEntry.clientId;
  const stats = ensureStats(data, statsId, playerEntry.username);
  stats.username = playerEntry.username;
  stats.isComputer = Boolean(playerEntry.isComputer);
  stats.computerId = playerEntry.computerId || stats.computerId || null;
  stats.gamesPlayed += 1;
  stats.wins += playerEntry.clientId === matchSummary.winnerId ? 1 : 0;
  stats.rings += playerEntry.stats.rings;
  stats.correctRings += playerEntry.stats.correctRings;
  stats.wrongRings += playerEntry.stats.wrongRings;
  stats.wonCards += playerEntry.stats.wonCards;
  stats.totalRank += playerEntry.rank || matchSummary.playerCount;
  stats.history.unshift({
    gameId: matchSummary.gameId,
    roomId: matchSummary.roomId,
    at: matchSummary.at,
    playerCount: matchSummary.playerCount,
    rank: playerEntry.rank,
    plays: playerEntry.stats.plays,
    rings: playerEntry.stats.rings,
    correctRings: playerEntry.stats.correctRings,
    wrongRings: playerEntry.stats.wrongRings,
    wonCards: playerEntry.stats.wonCards,
  });
  if (stats.history.length > 100) stats.history.length = 100;
}

function updateComputerDefeatStats(data, summary) {
  const computers = summary.players.filter((entry) => entry.isComputer && entry.computerId && entry.rank);
  summary.players
    .filter((entry) => !entry.isComputer && entry.rank)
    .forEach((entry) => {
      const stats = ensureStats(data, entry.statsId || entry.clientId, entry.username);
      stats.defeatedComputers = stats.defeatedComputers || {};
      computers.forEach((computer) => {
        if (entry.rank < computer.rank) {
          if (computer.computerId === GOD_COMPUTER_ID && Number(entry.finalDrawCount || 0) < 3) return;
          const previous = stats.defeatedComputers[computer.computerId] || 0;
          stats.defeatedComputers[computer.computerId] = previous + 1;
          if (computer.computerId === GOD_COMPUTER_ID && previous === 0) {
            stats.godDefeatedAt = summary.at;
            stats.godRewardGameId = summary.gameId;
          }
        }
      });
    });
}

function setConnected(state, clientId, connected) {
  const player = state.players.get(clientId);
  if (player) {
    player.connected = connected;
    player.lastSeenAt = Date.now();
  }
  for (const room of state.rooms.values()) {
    if (room.players.includes(clientId) && room.hostId === clientId && !connected) {
      migrateHost(room, state);
    }
  }
  for (const game of state.games.values()) {
    const room = state.rooms.get(game.roomId);
    const remainsInRoom = room?.players.includes(clientId) || room?.spectators.includes(clientId);
    if (connected && !remainsInRoom) continue;
    const changed = markConnection(game, clientId, connected, { disconnectProtection: game.settings.disconnectProtection });
    if (changed) {
      if (!connected && game.status === "playing" && game.players[game.turnIndex]?.clientId === clientId && game.settings.disconnectProtection) {
        setTurnTiming(game, Date.now(), 0);
      }
      if (room && room.gameId === game.id && game.status === "finished") room.status = "finished";
    }
  }
}

function leaveOtherRooms(state, clientId, keepRoomId = null) {
  for (const [roomId, room] of state.rooms.entries()) {
    if (roomId === keepRoomId) continue;
    const wasPlayer = room.players.includes(clientId);
    const wasSpectator = room.spectators.includes(clientId);
    if (!wasPlayer && !wasSpectator) continue;

    room.players = room.players.filter((id) => id !== clientId);
    room.spectators = room.spectators.filter((id) => id !== clientId);

    const game = room.gameId ? state.games.get(room.gameId) : null;
    if (game) {
      const gamePlayer = game.players.find((player) => player.clientId === clientId);
      if (gamePlayer) {
        markConnection(game, clientId, false, { disconnectProtection: game.settings.disconnectProtection });
        gamePlayer.exited = true;
        game.continueVotes = game.continueVotes.filter((id) => id !== clientId);
        if (game.status !== "finished") checkGameOver(game, Date.now());
      }
      game.spectators = game.spectators.filter((id) => id !== clientId);
    }

    if (!hasHumanOccupants(room, state)) {
      deleteRoom(room, state);
      continue;
    }

    if (room.hostId === clientId) migrateHost(room, state);
  }

  const player = state.players.get(clientId);
  if (player && player.currentRoomId !== keepRoomId) {
    player.currentRoomId = keepRoomId;
  }
}

function leaveRoom(room, clientId, state) {
  const game = room.gameId ? state.games.get(room.gameId) : null;
  room.players = room.players.filter((id) => id !== clientId);
  room.spectators = room.spectators.filter((id) => id !== clientId);

  if (game) {
    const gamePlayer = game.players.find((player) => player.clientId === clientId);
    if (gamePlayer) {
      markConnection(game, clientId, false, { disconnectProtection: game.settings.disconnectProtection });
      gamePlayer.exited = true;
      game.continueVotes = game.continueVotes.filter((id) => id !== clientId);
      if (game.status !== "finished" && game.players[game.turnIndex]?.clientId === clientId && game.settings.disconnectProtection) {
        setTurnTiming(game, Date.now(), 0);
      }
      if (game.status !== "finished") checkGameOver(game, Date.now());
    }
    game.spectators = game.spectators.filter((id) => id !== clientId);
  }

  const player = state.players.get(clientId);
  if (player?.currentRoomId === room.id) player.currentRoomId = null;

  if (!hasHumanOccupants(room, state)) {
    deleteRoom(room, state);
  } else if (room.hostId === clientId) {
    migrateHost(room, state);
  }

  return game;
}

function migrateHost(room, state) {
  if (room.players.includes(room.hostId) && state.players.get(room.hostId)?.connected !== false && !state.players.get(room.hostId)?.isComputer) return;
  const nextHost = room.players.find((clientId) => {
    const player = state.players.get(clientId);
    return player && !player.isComputer && player.connected !== false;
  }) || room.players.find((clientId) => !state.players.get(clientId)?.isComputer);
  if (nextHost) room.hostId = nextHost;
}

function hasHumanOccupants(room, state) {
  return [...room.players, ...room.spectators].some((clientId) => {
    const player = state.players.get(clientId);
    return player && !player.isComputer;
  });
}

function ensureLegacyUsernameAvailable(state, username, clientId) {
  const key = usernameKey(username);
  for (const player of state.players.values()) {
    if (player.clientId !== clientId && usernameKey(player.username) === key) {
      throw httpError(409, "用户名已被占用，请换一个。");
    }
  }
  for (const player of Object.values(state.data.players)) {
    if (player.clientId !== clientId && usernameKey(player.username) === key) {
      throw httpError(409, "用户名已被占用，请换一个。");
    }
  }
}

function createUser(state, username, password) {
  const clientId = crypto.randomUUID();
  const user = {
    clientId,
    username,
    createdAt: Date.now(),
    lastLoginAt: Date.now(),
  };
  setUserPassword(user, password);
  state.data.users[clientId] = user;
  ensureStats(state.data, clientId, username).username = username;
  return user;
}

function setUserPassword(user, password) {
  const salt = crypto.randomBytes(16).toString("hex");
  user.passwordSalt = salt;
  user.passwordHash = hashPassword(password, salt);
  user.passwordIterations = PASSWORD_ITERATIONS;
  user.passwordDigest = PASSWORD_DIGEST;
  user.passwordUpdatedAt = Date.now();
}

function hashPassword(password, salt, iterations = PASSWORD_ITERATIONS) {
  return crypto.pbkdf2Sync(password, salt, iterations, PASSWORD_KEY_LENGTH, PASSWORD_DIGEST).toString("hex");
}

function verifyPassword(user, password) {
  if (user.passwordHash === "123456") return { ok: true, resetPassword: true };
  if (!user.passwordSalt || !user.passwordHash) return { ok: false, resetPassword: false };
  const expected = hashPassword(password, user.passwordSalt, user.passwordIterations || PASSWORD_ITERATIONS);
  return { ok: timingSafeEqualHex(expected, user.passwordHash), resetPassword: false };
}

function timingSafeEqualHex(left, right) {
  try {
    const leftBuffer = Buffer.from(String(left), "hex");
    const rightBuffer = Buffer.from(String(right), "hex");
    if (leftBuffer.length !== rightBuffer.length) return false;
    return crypto.timingSafeEqual(leftBuffer, rightBuffer);
  } catch {
    return false;
  }
}

function createSession(state, clientId) {
  const token = crypto.randomBytes(32).toString("hex");
  state.sessions.set(token, { clientId, createdAt: Date.now(), lastSeenAt: Date.now() });
  return token;
}

function tokenFromRequest(req, url, body = {}) {
  const authHeader = req.headers.authorization || "";
  if (authHeader.toLowerCase().startsWith("bearer ")) return authHeader.slice(7).trim();
  return body.token || url.searchParams.get("token") || "";
}

function optionalAuth(req, url, body, context) {
  const token = tokenFromRequest(req, url, body);
  if (!token) return null;
  try {
    return requireAuth(req, url, body, context);
  } catch {
    return null;
  }
}

function requireAuth(req, url, body, context) {
  const { state } = context;
  const token = tokenFromRequest(req, url, body);
  if (!token) throw httpError(401, "请先登录。");
  const session = state.sessions.get(token);
  const now = Date.now();
  if (!session || now - session.lastSeenAt > SESSION_TTL_MS) {
    state.sessions.delete(token);
    throw httpError(401, "登录已过期，请重新登录。");
  }
  if (body.clientId && body.clientId !== session.clientId) {
    throw httpError(403, "会话与玩家不匹配。");
  }
  const user = state.data.users[session.clientId];
  if (!user) {
    state.sessions.delete(token);
    throw httpError(401, "账号不存在，请重新登录。");
  }
  session.lastSeenAt = now;
  const player = upsertPlayerFromUser(state, user, getClientIp(req));
  return { token, session, clientId: user.clientId, user, player };
}

function upsertPlayerFromUser(state, user, ip) {
  const existing = state.players.get(user.clientId) || {};
  const player = {
    clientId: user.clientId,
    username: user.username,
    ip,
    connected: existing.connected !== false,
    currentRoomId: existing.currentRoomId || null,
    joinedAt: existing.joinedAt || Date.now(),
    lastSeenAt: Date.now(),
  };
  state.players.set(user.clientId, player);
  ensureStats(state.data, user.clientId, user.username).username = user.username;
  return player;
}

function publicPlayer(player, data = null) {
  if (!player) return null;
  const isComputer = Boolean(player.isComputer);
  const statsId = player.statsId || player.clientId;
  return {
    clientId: player.clientId,
    username: player.username,
    isComputer,
    computerId: player.computerId || null,
    statsId,
    godSlayer: godSlayerForStatsId(data, statsId, isComputer),
    godRewardGameId: godRewardGameIdForStatsId(data, statsId, isComputer),
    connected: player.connected !== false,
    currentRoomId: player.currentRoomId || null,
    joinedAt: player.joinedAt,
    lastSeenAt: player.lastSeenAt,
  };
}

function publicGameFor(game, data) {
  const out = publicGame(game);
  const byId = new Map(out.players.map((player) => [player.clientId, player]));
  out.players = out.players.map((player) => ({
    ...player,
    godSlayer: godSlayerForStatsId(data, player.statsId || player.clientId, player.isComputer),
  }));
  if (out.resultInfo?.players) {
    out.resultInfo.players = out.resultInfo.players.map((player) => {
      const gamePlayer = byId.get(player.clientId);
      const isComputer = Boolean(gamePlayer?.isComputer);
      const statsId = gamePlayer?.statsId || player.clientId;
      return {
        ...player,
        isComputer,
        statsId,
        godSlayer: godSlayerForStatsId(data, statsId, isComputer),
      };
    });
  }
  return out;
}

function findUserByUsername(data, username) {
  const key = usernameKey(username);
  return Object.values(data.users || {}).find((user) => usernameKey(user.username) === key) || null;
}

function ensureUsernameAvailable(state, username) {
  if (findUserByUsername(state.data, username)) {
    throw httpError(409, "用户名已被注册，请换一个。");
  }
  const key = usernameKey(username);
  for (const player of state.players.values()) {
    if (usernameKey(player.username) === key) throw httpError(409, "用户名已被注册，请换一个。");
  }
}

function httpError(statusCode, message) {
  const error = new Error(message);
  error.statusCode = statusCode;
  return error;
}

function usernameKey(username) {
  return String(username || "").trim().toLocaleLowerCase("zh-CN");
}

function ensureKnownPlayer(state, clientId) {
  const player = state.players.get(clientId);
  if (!player) throw new Error("请先设置用户名。");
  return player;
}

function ensureStats(data, clientId, username = "玩家") {
  if (!data.players[clientId]) {
    data.players[clientId] = {
      clientId,
      username,
      gamesPlayed: 0,
      wins: 0,
      rings: 0,
      correctRings: 0,
      wrongRings: 0,
      wonCards: 0,
      totalRank: 0,
      isComputer: false,
      computerId: null,
      defeatedComputers: {},
      godRewardGameId: null,
      godDefeatedAt: null,
      history: [],
    };
  }
  data.players[clientId].defeatedComputers = data.players[clientId].defeatedComputers || {};
  return data.players[clientId];
}

function profileFor(clientId, data) {
  const stats = data.players[clientId] || {
    clientId,
    username: "玩家",
    gamesPlayed: 0,
    wins: 0,
    rings: 0,
    correctRings: 0,
    wrongRings: 0,
    wonCards: 0,
    totalRank: 0,
    isComputer: false,
    computerId: null,
    defeatedComputers: {},
    godRewardGameId: null,
    godDefeatedAt: null,
    history: [],
  };
  return enrichStats(stats);
}

function leaderboardFor(data) {
  const players = Object.values(data.players)
    .map(enrichStats)
    .sort((a, b) => b.wins - a.wins || b.gamesPlayed - a.gamesPlayed);
  const matches = data.matchHistory.slice().sort((a, b) => b.playCount - a.playCount);
  return { players, matches };
}

function enrichStats(stats) {
  const gamesPlayed = stats.gamesPlayed || 0;
  const rings = stats.rings || 0;
  return {
    ...stats,
    godSlayer: godSlayerForStats(stats),
    godRewardGameId: stats.godRewardGameId || null,
    godDefeatedAt: stats.godDefeatedAt || null,
    winRate: gamesPlayed ? stats.wins / gamesPlayed : 0,
    correctRate: rings ? stats.correctRings / rings : 0,
    ringsPerGame: gamesPlayed ? rings / gamesPlayed : 0,
    wonCardsPerGame: gamesPlayed ? stats.wonCards / gamesPlayed : 0,
    averageRank: gamesPlayed ? stats.totalRank / gamesPlayed : 0,
  };
}

function godSlayerForStats(stats) {
  return Boolean(!stats?.isComputer && Number(stats?.defeatedComputers?.[GOD_COMPUTER_ID] || 0) > 0);
}

function godSlayerForStatsId(data, statsId, isComputer = false) {
  if (!data || isComputer) return false;
  return godSlayerForStats(data.players?.[statsId]);
}

function godRewardGameIdForStatsId(data, statsId, isComputer = false) {
  if (!data || isComputer) return null;
  const stats = data.players?.[statsId];
  return godSlayerForStats(stats) ? stats.godRewardGameId || null : null;
}

function snapshotFor(clientId, state, cardLibraries, computerPlayers = []) {
  const player = state.players.get(clientId) || null;
  const rooms = [...state.rooms.values()].map((room) => roomSummary(room, state));
  let assignedRoom = player?.currentRoomId ? state.rooms.get(player.currentRoomId) : null;
  if (player?.currentRoomId && (!assignedRoom || !roomHasClient(assignedRoom, clientId))) {
    player.currentRoomId = null;
    assignedRoom = null;
  }
  const currentRoom = assignedRoom || [...state.rooms.values()].find((room) => roomHasClient(room, clientId));
  const currentGame = currentRoom?.gameId ? state.games.get(currentRoom.gameId) : null;
  return {
    serverTime: Date.now(),
    player: publicPlayer(player, state.data),
    cardLibraries: publicLibraries(cardLibraries),
    computerPlayers: publicComputerPlayers(computerPlayers),
    rooms,
    currentRoom: currentRoom ? roomSummary(currentRoom, state) : null,
    currentGame: currentGame ? publicGameFor(currentGame, state.data) : null,
  };
}

function roomHasClient(room, clientId) {
  return room.players.includes(clientId) || room.spectators.includes(clientId);
}

function roomSummary(room, state) {
  const game = room.gameId ? state.games.get(room.gameId) : null;
  return {
    id: room.id,
    hostId: room.hostId,
    hostName: state.players.get(room.hostId)?.username || "房主",
    status: room.status,
    gameId: room.gameId,
    settings: room.settings,
    createdAt: room.createdAt,
    playerCount: room.players.length,
    spectatorCount: room.spectators.length,
    startVotes: (room.startVotes || []).slice(),
    startVoteRequired: startVoteRequirement(room),
    startCountdownStartedAt: room.startCountdownStartedAt || null,
    startAt: room.startAt || null,
    chatMessages: (room.chatMessages || []).slice(-100).map((message) => ({ ...message })),
    players: room.players.map((clientId) => {
      const player = state.players.get(clientId);
      const gamePlayer = game?.players.find((item) => item.clientId === clientId);
      const isComputer = Boolean(player?.isComputer || gamePlayer?.isComputer);
      const statsId = player?.statsId || gamePlayer?.statsId || clientId;
      return {
        clientId,
        username: player?.username || gamePlayer?.username || "玩家",
        isComputer,
        computerId: player?.computerId || gamePlayer?.computerId || null,
        statsId,
        godSlayer: godSlayerForStatsId(state.data, statsId, isComputer),
        connected: gamePlayer ? gamePlayer.connected : player?.connected !== false,
        eliminated: gamePlayer?.eliminated || false,
        ready: gamePlayer?.ready || false,
        loadingLoaded: gamePlayer?.loadingLoaded || 0,
        loadingTotal: gamePlayer?.loadingTotal || 0,
        loadingProgress: gamePlayer?.loadingProgress || 0,
        loadingCached: gamePlayer?.loadingCached || false,
      };
    }),
    spectators: room.spectators.map((clientId) => {
      const player = state.players.get(clientId);
      return { clientId, username: player?.username || "观战者", connected: player?.connected !== false };
    }),
  };
}

function publicLibraries(cardLibraries) {
  return cardLibraries.map((library) => ({
    id: library.id,
    name: library.name,
    title: library.title,
    curator: library.curator,
    description: library.description,
    backUrl: library.backUrl,
    cardCount: library.cardCount,
    pmvCount: library.pmvCount,
    manifest: library.manifest,
  }));
}

function publicComputerPlayers(computerPlayers) {
  return computerPlayers.map((computer) => ({ ...computer }));
}

function cardViewerPayload(requestedIds, cardLibraries) {
  const ids = requestedIds.length ? new Set(requestedIds) : new Set(cardLibraries.slice(0, 1).map((library) => library.id));
  const libraries = cardLibraries.filter((library) => ids.has(library.id));
  const assets = new Set();
  libraries.forEach((library) => {
    assets.add(library.backUrl);
    library.cards.forEach((card) => assets.add(card.imageUrl));
  });
  const fingerprint = crypto.createHash("sha256")
    .update(JSON.stringify(libraries.map((library) => ({
      id: library.id,
      cardCount: library.cardCount,
      cards: library.cards.map((card) => card.imageUrl),
    }))))
    .digest("hex")
    .slice(0, 20);
  return {
    key: `card-viewer-${fingerprint}`,
    assets: [...assets],
    libraries: libraries.map((library) => ({
      ...publicLibraries([library])[0],
      pmvs: cardsGroupedByPmv(library),
    })),
  };
}

function pmvIndexPayload(cardLibraries) {
  const rows = cardLibraries
    .flatMap((library) => library.manifest.map((entry) => ({
      libraryId: library.id,
      libraryName: library.name,
      pmvId: entry.pmvId,
      name: entry.name,
      author: entry.author,
    })))
    .sort((a, b) => a.pmvId - b.pmvId || a.libraryName.localeCompare(b.libraryName, "zh-Hans-CN"));
  return { rows };
}

function assetManifestForRoom(room, cardLibraries) {
  const selectedIds = new Set(room.settings.libraryIds);
  const libraries = cardLibraries.filter((library) => selectedIds.has(library.id));
  const assets = new Set(["/assets/bell.png", "/assets/logo.png", "/ding.wav", "/sendcard.mp3"]);
  libraries.forEach((library) => {
    assets.add(library.backUrl);
    library.cards.forEach((card) => assets.add(card.imageUrl));
  });
  const fingerprint = crypto.createHash("sha256")
    .update(JSON.stringify(libraries.map((library) => ({
      id: library.id,
      cardCount: library.cardCount,
      backUrl: library.backUrl,
      cards: library.cards.map((card) => card.imageUrl),
    }))))
    .digest("hex")
    .slice(0, 20);
  return {
    key: `room-assets-${fingerprint}`,
    libraries: publicLibraries(libraries),
    assets: [...assets],
  };
}

function allocateRoomId(state) {
  for (let attempts = 0; attempts < 1001; attempts += 1) {
    const id = String(Math.floor(Math.random() * 1001));
    if (!state.rooms.has(id)) return id;
  }
  throw new Error("没有可用房间号。");
}

function ensureHost(room, clientId) {
  if (room.hostId !== clientId) throw new Error("只有房主可以执行该操作。");
}

function requiredString(value, name) {
  if (typeof value !== "string" || !value.trim()) throw new Error(`${name} is required.`);
  return value.trim();
}

function cleanName(value) {
  return value.trim().slice(0, 24) || "玩家";
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = "";
    req.setEncoding("utf8");
    req.on("data", (chunk) => {
      raw += chunk;
      if (raw.length > 1_000_000) {
        reject(new Error("Request body is too large."));
        req.destroy();
      }
    });
    req.on("end", () => {
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch (error) {
        reject(new Error("Invalid JSON body."));
      }
    });
    req.on("error", reject);
  });
}

function sendJson(res, statusCode, payload) {
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
  });
  res.end(JSON.stringify(payload));
}

function broadcast(state) {
  const payload = `event: state\ndata: ${JSON.stringify({ at: Date.now() })}\n\n`;
  for (const streams of state.streams.values()) {
    for (const res of streams) {
      if (!res.destroyed) res.write(payload);
    }
  }
}

function emitAudioEvent(state, type, meta) {
  const payload = `event: audio\ndata: ${JSON.stringify({ type, ...meta, at: Date.now() })}\n\n`;
  for (const streams of state.streams.values()) {
    for (const res of streams) {
      if (!res.destroyed) res.write(payload);
    }
  }
}

function serveStatic(req, res, pathname, context) {
  const { rootDir, publicDir } = context;
  let filePath;

  if (pathname === "/assets/bell.png" || pathname === "/bell.png") {
    filePath = path.join(rootDir, "bell.png");
  } else if (pathname === "/assets/logo.png" || pathname === "/logo.png") {
    filePath = path.join(rootDir, "logo.png");
  } else if (/^\/(?:assets\/)?bg[1-3]\.jpg$/.test(pathname)) {
    filePath = path.join(rootDir, path.basename(pathname));
  } else if (pathname === "/ding.wav") {
    filePath = path.join(rootDir, "ding.wav");
  } else if (pathname === "/sendcard.mp3") {
    filePath = path.join(rootDir, "sendcard.mp3");
  } else if (pathname.startsWith("/cards/")) {
    filePath = path.join(rootDir, pathname.slice(1));
  } else {
    const safePath = pathname === "/" ? "index.html" : pathname.slice(1);
    filePath = path.join(publicDir, safePath);
    if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
      filePath = path.join(publicDir, "index.html");
    }
  }

  const resolved = path.resolve(filePath);
  const safeRoots = [path.resolve(publicDir), path.resolve(path.join(rootDir, "cards")), path.resolve(rootDir)];
  if (!safeRoots.some((safeRoot) => resolved === safeRoot || resolved.startsWith(`${safeRoot}${path.sep}`))) {
    res.writeHead(403);
    return res.end("Forbidden");
  }

  fs.readFile(resolved, (error, content) => {
    if (error) {
      res.writeHead(404);
      res.end("Not found");
      return;
    }
    res.writeHead(200, {
      "Content-Type": mimeType(resolved),
      "Cache-Control": assetCacheControl(pathname),
      "X-Content-Type-Options": "nosniff",
    });
    res.end(content);
  });
}

function assetCacheControl(pathname) {
  if (pathname.startsWith("/cards/") || /^\/(?:assets\/)?bg[1-3]\.jpg$/.test(pathname) || pathname === "/assets/bell.png" || pathname === "/bell.png" || pathname === "/assets/logo.png" || pathname === "/logo.png" || pathname === "/ding.wav" || pathname === "/sendcard.mp3") {
    return "public, max-age=31536000, immutable";
  }
  return "no-cache";
}

function mimeType(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  return {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".svg": "image/svg+xml",
    ".wav": "audio/wav",
    ".mp3": "audio/mpeg",
  }[ext] || "application/octet-stream";
}

function loadData(dataFile) {
  if (!fs.existsSync(dataFile)) return { users: {}, players: {}, matchHistory: [] };
  try {
    const parsed = JSON.parse(fs.readFileSync(dataFile, "utf8"));
    return {
      users: parsed.users || {},
      players: parsed.players || {},
      matchHistory: Array.isArray(parsed.matchHistory) ? parsed.matchHistory : [],
    };
  } catch {
    return { users: {}, players: {}, matchHistory: [] };
  }
}

function saveData(data, dataFile) {
  fs.mkdirSync(path.dirname(dataFile), { recursive: true });
  fs.writeFileSync(dataFile, JSON.stringify(data, null, 2), "utf8");
}

function loadComputerPlayers(rootDir) {
  const filePath = path.join(rootDir, "config", "computerPlayers.json");
  const raw = fs.readFileSync(filePath, "utf8");
  const parsed = JSON.parse(raw);
  if (!Array.isArray(parsed.players)) throw new Error("config/computerPlayers.json must contain players.");
  return parsed.players.map((player) => ({
    id: cleanComputerId(player.id),
    name: cleanName(String(player.name || "Computer")),
    description: String(player.description || "").trim(),
    playDelayMeanSeconds: Number(player.playDelayMeanSeconds),
    playDelayStdSeconds: Number(player.playDelayStdSeconds),
    reactionMeanSeconds: Number(player.reactionMeanSeconds),
    reactionStdSeconds: Number(player.reactionStdSeconds),
    matchDetectionProbability: clampProbability(player.matchDetectionProbability),
    falseRingProbability: clampProbability(player.falseRingProbability),
  }));
}

function cleanComputerId(value) {
  const id = String(value || "").trim().replace(/[^a-zA-Z0-9_-]/g, "");
  if (!id) throw new Error("Computer id is required.");
  return id;
}

function clampProbability(value) {
  const number = Number(value);
  return Math.max(0, Math.min(1, Number.isFinite(number) ? number : 0));
}

function getClientIp(req) {
  return req.headers["x-forwarded-for"]?.split(",")[0]?.trim()
    || req.socket.remoteAddress
    || os.hostname();
}

function getLanUrls(port) {
  return Object.values(os.networkInterfaces())
    .flat()
    .filter((item) => item && item.family === "IPv4" && !item.internal)
    .map((item) => `http://${item.address}:${port}`);
}

if (require.main === module) {
  const { server } = createApp();
  server.listen(DEFAULT_PORT, DEFAULT_HOST, () => {
    console.log(`Clash of Frames running at http://localhost:${DEFAULT_PORT}`);
    getLanUrls(DEFAULT_PORT).forEach((address) => console.log(`LAN: ${address}`));
  });
}

module.exports = {
  createApp,
};
