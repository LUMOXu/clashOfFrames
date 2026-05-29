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
  markConnection,
  checkGameOver,
  setTurnTiming,
  summarizeGameForStats,
} = require("./src/gameCore");
const { discoverCardLibraries } = require("./src/cardLibraries");

const ROOT_DIR = __dirname;
const DEFAULT_PORT = Number.parseInt(process.env.PORT || "3000", 10);
const DEFAULT_HOST = process.env.HOST || "0.0.0.0";
const PASSWORD_ITERATIONS = 210000;
const PASSWORD_KEY_LENGTH = 32;
const PASSWORD_DIGEST = "sha256";
const SESSION_TTL_MS = 7 * 24 * 60 * 60 * 1000;

function createApp(options = {}) {
  const rootDir = options.rootDir || ROOT_DIR;
  const dataFile = options.dataFile || path.join(rootDir, "data", "state.json");
  const publicDir = path.join(rootDir, "public");
  const cardLibraries = discoverCardLibraries(rootDir);
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

  const server = http.createServer((req, res) => {
    handleRequest(req, res, { rootDir, publicDir, dataFile, cardLibraries, state })
      .catch((error) => {
        const statusCode = error.statusCode || 500;
        if (statusCode >= 500) console.error(error);
        sendJson(res, statusCode, { error: error.message || "Internal server error" });
      });
  });

  const tick = setInterval(() => {
    let changed = false;
    for (const game of state.games.values()) {
      if (game.status === "finished") {
        const room = state.rooms.get(game.roomId);
        if (room && maybeReturnToWaiting(room, game, state)) changed = true;
        continue;
      }
      if (game.status !== "playing") continue;
      const now = Date.now();
      if (game.lockedUntil > now) continue;
      const player = game.players[game.turnIndex];
      if (player && player.connected === false && game.settings.disconnectProtection) {
        const disconnectedDeadline = (game.turnStartedAt || game.turnAvailableAt || now) + 2000;
        if (game.turnDeadlineAt > disconnectedDeadline) game.turnDeadlineAt = disconnectedDeadline;
      }
      if (player && !player.eliminated && !player.exited && player.drawPile.length > 0 && now >= game.turnDeadlineAt) {
        performPlayCard(game, player.clientId, { now, auto: true });
        changed = true;
        finalizeGameIfNeeded(game, state, dataFile);
      }
    }
    if (changed) broadcast(state);
  }, 500);

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
  const { state, cardLibraries, dataFile } = context;
  const body = req.method === "POST" ? await readBody(req) : {};

  if (req.method === "GET" && pathname === "/api/bootstrap") {
    const auth = optionalAuth(req, url, body, context);
    return sendJson(res, 200, snapshotFor(auth?.clientId || "", state, cardLibraries));
  }

  if (req.method === "GET" && pathname === "/api/card-libraries") {
    return sendJson(res, 200, publicLibraries(cardLibraries));
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
      broadcast(state);
      return sendJson(res, 200, result);
    }

    if (action === "leave") {
      const affectedGame = leaveRoom(room, clientId, state);
      if (affectedGame?.status === "loading" && state.rooms.has(room.id)) {
        advanceLoading(room, affectedGame, state);
      }
      if (affectedGame) finalizeGameIfNeeded(affectedGame, state, dataFile);
      broadcast(state);
      return sendJson(res, 200, { ok: true });
    }

    if (action === "settings") {
      ensureHost(room, clientId);
      if (room.status !== "waiting") throw new Error("只有等待中房间可以修改设置。");
      room.settings = {
        ...room.settings,
        ...normalizeSettings({ ...room.settings, ...(body.settings || {}) }, cardLibraries.map((library) => library.id)),
        minPlayers: room.settings.minPlayers,
        maxPlayers: room.settings.maxPlayers,
        isPublic: room.settings.isPublic,
      };
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state) });
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
      return sendJson(res, 200, { room: roomSummary(room, state), game: publicGame(game) });
    }

    if (action === "loading-ready") {
      const game = state.games.get(room.gameId);
      if (!game || game.status !== "loading") throw new Error("当前房间不在加载阶段。");
      const player = game.players.find((item) => item.clientId === clientId);
      if (player) player.ready = true;
      advanceLoading(room, game, state);
      finalizeGameIfNeeded(game, state, dataFile);
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state), game: publicGame(game) });
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
      return sendJson(res, 200, { game: publicGame(game) });
    }

    if (action === "ring-bell") {
      const result = performRingBell(game, clientId, { now: Date.now() });
      if (!result.ok) return sendJson(res, 409, { error: result.error });
      finalizeGameIfNeeded(game, state, dataFile);
      broadcast(state);
      return sendJson(res, 200, { game: publicGame(game) });
    }

    if (action === "continue") {
      const room = state.rooms.get(game.roomId);
      if (!room) throw new Error("房间不存在。");
      if (!game.continueVotes.includes(clientId)) game.continueVotes.push(clientId);
      maybeReturnToWaiting(room, game, state);
      broadcast(state);
      return sendJson(res, 200, { room: roomSummary(room, state), game: publicGame(game) });
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
  const settings = normalizeSettings(settingsInput, cardLibraries.map((library) => library.id));
  const room = {
    id: roomId,
    hostId: player.clientId,
    players: [player.clientId],
    spectators: [],
    status: "waiting",
    settings,
    gameId: null,
    lastWinnerId: null,
    createdAt: Date.now(),
  };
  state.rooms.set(roomId, room);
  player.currentRoomId = roomId;
  return room;
}

function joinRoom(room, player, state) {
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
    return { room: roomSummary(room, state), game: publicGame(game), spectator: false };
  }

  if (!room.spectators.includes(player.clientId)) room.spectators.push(player.clientId);
  if (game && !game.spectators.includes(player.clientId)) game.spectators.push(player.clientId);
  player.currentRoomId = room.id;
  return { room: roomSummary(room, state), game: game ? publicGame(game) : null, spectator: true };
}

function startRoomGame(room, state, cardLibraries) {
  if (room.status !== "waiting") throw new Error("房间不在等待状态。");
  if (room.players.length < room.settings.minPlayers) throw new Error("人数不足，无法开始游戏。");
  const cards = cardLibraries
    .filter((library) => room.settings.libraryIds.includes(library.id))
    .flatMap((library) => library.cards);
  if (cards.length < room.players.length) throw new Error("选中的卡牌太少，无法发牌。");
  const players = room.players.map((clientId) => ensureKnownPlayer(state, clientId));
  const game = createGame({ room, players, cards });
  state.games.set(game.id, game);
  room.gameId = game.id;
  room.status = "loading";
  return game;
}

function advanceLoading(room, game, state) {
  const connected = game.players.filter((player) => player.connected !== false && !player.exited);
  if (connected.length < game.settings.minPlayers) {
    room.status = "waiting";
    room.gameId = null;
    game.status = "aborted";
    return;
  }
  const allConnectedReady = connected.every((player) => player.ready);
  if (allConnectedReady) {
    room.status = "playing";
    startPlaying(game, Date.now());
  }
}

function maybeReturnToWaiting(room, game, state, now = Date.now()) {
  if (game.status !== "finished") return false;
  const connectedIds = game.players
    .filter((player) => player.connected !== false && !player.exited)
    .map((player) => player.clientId);
  const validVotes = game.continueVotes.filter((clientId) => connectedIds.includes(clientId));
  const threshold = Math.max(1, Math.floor(connectedIds.length / 2) + 1);

  if (!game.continueReturnAt) {
    if (validVotes.length < threshold) return false;
    game.continueCountdownStartedAt = now;
    game.continueReturnAt = now + 10000;
    return true;
  }

  if (now < game.continueReturnAt && validVotes.length < connectedIds.length) return false;

  game.players.forEach((gamePlayer) => {
    if (!validVotes.includes(gamePlayer.clientId)) {
      const player = state.players.get(gamePlayer.clientId);
      if (player?.currentRoomId === room.id) player.currentRoomId = null;
    }
  });
  room.players = validVotes.filter((clientId) => state.players.has(clientId));
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

function finalizeGameIfNeeded(game, state, dataFile) {
  if (game.status !== "finished" || game.statsSaved) return;
  game.statsSaved = true;
  const summary = summarizeGameForStats(game);
  state.data.matchHistory.unshift(summary);
  if (state.data.matchHistory.length > 200) state.data.matchHistory.length = 200;
  summary.players.forEach((entry) => updatePlayerStats(state.data, entry, summary));
  const room = state.rooms.get(game.roomId);
  if (room) {
    room.status = "finished";
    room.lastWinnerId = game.winnerId;
  }
  saveData(state.data, dataFile);
}

function updatePlayerStats(data, playerEntry, matchSummary) {
  const stats = ensureStats(data, playerEntry.clientId, playerEntry.username);
  stats.username = playerEntry.username;
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
    const changed = markConnection(game, clientId, connected, { disconnectProtection: game.settings.disconnectProtection });
    if (changed) {
      if (!connected && game.status === "playing" && game.players[game.turnIndex]?.clientId === clientId && game.settings.disconnectProtection) {
        setTurnTiming(game, Date.now(), 0);
      }
      const room = state.rooms.get(game.roomId);
      if (room && game.status === "finished") room.status = "finished";
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
      if (gamePlayer && game.status !== "finished") {
        markConnection(game, clientId, false, { disconnectProtection: game.settings.disconnectProtection });
        checkGameOver(game, Date.now());
      }
      game.spectators = game.spectators.filter((id) => id !== clientId);
    }

    if (room.players.length === 0 && room.status === "waiting") {
      state.rooms.delete(roomId);
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
    if (gamePlayer && game.status !== "finished") {
      markConnection(game, clientId, false, { disconnectProtection: game.settings.disconnectProtection });
      if (game.players[game.turnIndex]?.clientId === clientId && game.settings.disconnectProtection) {
        setTurnTiming(game, Date.now(), 0);
      }
      checkGameOver(game, Date.now());
    }
    game.spectators = game.spectators.filter((id) => id !== clientId);
  }

  const player = state.players.get(clientId);
  if (player?.currentRoomId === room.id) player.currentRoomId = null;

  if (room.players.length === 0 && room.status === "waiting") {
    state.rooms.delete(room.id);
  } else if (room.hostId === clientId) {
    migrateHost(room, state);
  }

  return game;
}

function migrateHost(room, state) {
  if (room.players.includes(room.hostId) && state.players.get(room.hostId)?.connected !== false) return;
  const nextHost = room.players.find((clientId) => state.players.get(clientId)?.connected !== false) || room.players[0];
  if (nextHost) room.hostId = nextHost;
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

function publicPlayer(player) {
  if (!player) return null;
  return {
    clientId: player.clientId,
    username: player.username,
    connected: player.connected !== false,
    currentRoomId: player.currentRoomId || null,
    joinedAt: player.joinedAt,
    lastSeenAt: player.lastSeenAt,
  };
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
      history: [],
    };
  }
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
    winRate: gamesPlayed ? stats.wins / gamesPlayed : 0,
    correctRate: rings ? stats.correctRings / rings : 0,
    ringsPerGame: gamesPlayed ? rings / gamesPlayed : 0,
    wonCardsPerGame: gamesPlayed ? stats.wonCards / gamesPlayed : 0,
    averageRank: gamesPlayed ? stats.totalRank / gamesPlayed : 0,
  };
}

function snapshotFor(clientId, state, cardLibraries) {
  const player = state.players.get(clientId) || null;
  const rooms = [...state.rooms.values()].map((room) => roomSummary(room, state));
  const assignedRoom = player?.currentRoomId ? state.rooms.get(player.currentRoomId) : null;
  const currentRoom = assignedRoom || [...state.rooms.values()].find((room) => room.players.includes(clientId) || room.spectators.includes(clientId));
  const currentGame = currentRoom?.gameId ? state.games.get(currentRoom.gameId) : null;
  return {
    serverTime: Date.now(),
    player: publicPlayer(player),
    cardLibraries: publicLibraries(cardLibraries),
    rooms,
    currentRoom: currentRoom ? roomSummary(currentRoom, state) : null,
    currentGame: currentGame ? publicGame(currentGame) : null,
    profile: player ? profileFor(clientId, state.data) : null,
    leaderboard: leaderboardFor(state.data),
  };
}

function roomSummary(room, state) {
  const game = room.gameId ? state.games.get(room.gameId) : null;
  return {
    id: room.id,
    hostId: room.hostId,
    status: room.status,
    gameId: room.gameId,
    settings: room.settings,
    createdAt: room.createdAt,
    players: room.players.map((clientId) => {
      const player = state.players.get(clientId);
      const gamePlayer = game?.players.find((item) => item.clientId === clientId);
      return {
        clientId,
        username: player?.username || gamePlayer?.username || "玩家",
        connected: gamePlayer ? gamePlayer.connected : player?.connected !== false,
        eliminated: gamePlayer?.eliminated || false,
        ready: gamePlayer?.ready || false,
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
    backUrl: library.backUrl,
    cardCount: library.cardCount,
    pmvCount: library.pmvCount,
    manifest: library.manifest,
  }));
}

function assetManifestForRoom(room, cardLibraries) {
  const selectedIds = new Set(room.settings.libraryIds);
  const libraries = cardLibraries.filter((library) => selectedIds.has(library.id));
  const assets = new Set(["/assets/bell.png", "/assets/logo.png"]);
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

function serveStatic(req, res, pathname, context) {
  const { rootDir, publicDir } = context;
  let filePath;

  if (pathname === "/assets/bell.png" || pathname === "/bell.png") {
    filePath = path.join(rootDir, "bell.png");
  } else if (pathname === "/assets/logo.png" || pathname === "/logo.png") {
    filePath = path.join(rootDir, "logo.png");
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
  if (pathname.startsWith("/cards/") || pathname === "/assets/bell.png" || pathname === "/bell.png" || pathname === "/assets/logo.png" || pathname === "/logo.png") {
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
