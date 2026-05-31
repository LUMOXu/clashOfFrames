"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { createApp } = require("../server");

function listen(server) {
  return new Promise((resolve) => {
    server.listen(0, () => resolve(server.address().port));
  });
}

function close(server) {
  return new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
}

async function request(base, method, pathname, body) {
  const response = await fetch(`${base}${pathname}`, {
    method,
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await response.json();
  if (!response.ok) throw new Error(json.error || `${method} ${pathname} failed`);
  return json;
}

async function requestFailure(base, method, pathname, body) {
  const response = await fetch(`${base}${pathname}`, {
    method,
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await response.json();
  assert.equal(response.ok, false);
  return json;
}

async function requestRaw(base, pathname) {
  return fetch(`${base}${pathname}`);
}

async function register(base, username, password = "secret123") {
  const result = await request(base, "POST", "/api/register", { username, password });
  return {
    token: result.token,
    clientId: result.player.clientId,
    username: result.player.username,
  };
}

function payload(auth, extra = {}) {
  return { token: auth.token, clientId: auth.clientId, ...extra };
}

async function loadingProgress(base, roomId, auth, loaded, total, done = false) {
  return request(base, "POST", `/api/rooms/${roomId}/loading-progress`, payload(auth, {
    loaded,
    total,
    cached: loaded === total,
    manifestKey: "test-manifest",
    done,
  }));
}

async function openEvents(base, auth) {
  const controller = new AbortController();
  const response = await fetch(`${base}/api/events?token=${encodeURIComponent(auth.token)}`, {
    signal: controller.signal,
  });
  assert.equal(response.ok, true);
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  return {
    async nextEvent(timeoutMs = 1500) {
      const deadline = Date.now() + timeoutMs;
      while (Date.now() < deadline) {
        const separator = buffer.indexOf("\n\n");
        if (separator >= 0) {
          const raw = buffer.slice(0, separator);
          buffer = buffer.slice(separator + 2);
          return raw;
        }
        const remaining = Math.max(1, deadline - Date.now());
        const result = await Promise.race([
          reader.read(),
          new Promise((resolve) => setTimeout(() => resolve({ timeout: true }), remaining)),
        ]);
        if (result.timeout) break;
        if (result.done) break;
        buffer += decoder.decode(result.value, { stream: true });
      }
      throw new Error("Timed out waiting for SSE event.");
    },
    async close() {
      controller.abort();
      await reader.cancel().catch(() => undefined);
    },
  };
}

async function waitForEvent(stream, name, timeoutMs = 2000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const event = await stream.nextEvent(Math.max(1, deadline - Date.now()));
    if (event.includes(`event: ${name}`)) return event;
  }
  throw new Error(`Timed out waiting for ${name} SSE event.`);
}

test("API smoke: auth, assets, room flow, loading, and play", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const publicBootstrap = await request(base, "GET", "/api/bootstrap");
    assert.equal(publicBootstrap.player, null);
    assert.ok(publicBootstrap.cardLibraries.length >= 1);
    assert.equal("cards" in publicBootstrap.cardLibraries[0], false);
    assert.equal("leaderboard" in publicBootstrap, false);
    assert.equal("profile" in publicBootstrap, false);
    const libraryIds = publicBootstrap.cardLibraries.map((library) => library.id);

    const p1 = await register(base, "Player 1");
    const duplicate = await requestFailure(base, "POST", "/api/register", { username: "Player 1", password: "secret123" });
    assert.match(duplicate.error, /用户名/);
    const p2 = await register(base, "Player 2");
    const p3 = await register(base, "Player 3");
    const p4 = await register(base, "Player 4");

    const reset = await register(base, "Reset Me", "oldpass123");
    state.data.users[reset.clientId].passwordHash = "123456";
    const resetLogin = await request(base, "POST", "/api/login", { username: "Reset Me", password: "newpass123" });
    assert.equal(resetLogin.passwordReset, true);
    assert.notEqual(state.data.users[reset.clientId].passwordHash, "123456");
    await request(base, "POST", "/api/login", { username: "Reset Me", password: "newpass123" });

    const firstRoom = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    }));
    const secondRoom = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    }));
    assert.notEqual(secondRoom.room.id, firstRoom.room.id);
    const p1Bootstrap = await request(base, "GET", `/api/bootstrap?token=${encodeURIComponent(p1.token)}`);
    assert.equal(p1Bootstrap.currentRoom.id, secondRoom.room.id);
    assert.equal(p1Bootstrap.rooms.some((room) => room.id === firstRoom.room.id), false);

    const p2OwnRoom = await request(base, "POST", "/api/rooms", payload(p2, {
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    }));
    assert.ok(p2OwnRoom.room.id);
    const p4Room = await request(base, "POST", "/api/rooms", payload(p4, {
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    }));
    await request(base, "POST", `/api/rooms/${p4Room.room.id}/leave`, payload(p4));
    const p4Bootstrap = await request(base, "GET", `/api/bootstrap?token=${encodeURIComponent(p4.token)}`);
    assert.equal(p4Bootstrap.currentRoom, null);

    const roomId = secondRoom.room.id;
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p2));
    const p2Bootstrap = await request(base, "GET", `/api/bootstrap?token=${encodeURIComponent(p2.token)}`);
    assert.equal(p2Bootstrap.currentRoom.id, roomId);
    assert.equal(p2Bootstrap.rooms.some((room) => room.id === p2OwnRoom.room.id), false);
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p3));

    const started = await request(base, "POST", `/api/rooms/${roomId}/start`, payload(p1));
    assert.equal(started.game.status, "loading");
    const assets = await request(base, "GET", `/api/rooms/${roomId}/assets?token=${encodeURIComponent(p1.token)}`);
    assert.ok(assets.key);
    assert.ok(assets.assets.includes("/assets/bell.png"));
    assert.ok(assets.assets.some((asset) => asset.startsWith("/cards/")));

    const gameState = state.games.get(started.game.id);
    gameState.players.find((player) => player.clientId === p3.clientId).connected = false;
    const partial = await loadingProgress(base, roomId, p1, assets.assets.length, assets.assets.length, true);
    assert.equal(partial.game.status, "loading");
    assert.equal(partial.room.players.find((player) => player.clientId === p1.clientId).loadingProgress, 100);
    await loadingProgress(base, roomId, p2, Math.floor(assets.assets.length / 2), assets.assets.length, false);
    await loadingProgress(base, roomId, p2, assets.assets.length, assets.assets.length, true);
    const loaded = await loadingProgress(base, roomId, p3, assets.assets.length, assets.assets.length, true);
    assert.equal(loaded.game.status, "playing");
    assert.ok(loaded.game.players[0].drawCount >= loaded.game.players[0].drawPile.length);
    assert.equal("pmvId" in loaded.game.players[0].drawPile[0], false);

    const authById = new Map([p1, p2, p3].map((auth) => [auth.clientId, auth]));
    const current = loaded.game.players[loaded.game.turnIndex].clientId;
    const played = await request(base, "POST", `/api/games/${loaded.game.id}/play-card`, payload(authById.get(current)));
    assert.equal(played.game.playCount, 1);
    assert.ok(played.game.players.some((player) => player.displayPile.some((card) => card.imageUrl)));
  } finally {
    await close(server);
  }
});

test("API supports computers, start votes, chat, card viewer, PMV index, and computer stats", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryId = bootstrap.cardLibraries[0].id;
    const computers = await request(base, "GET", "/api/computer-players");
    assert.ok(computers.players.length >= 6);
    assert.ok(computers.players.some((computer) => computer.id === "computer_god"));

    const p1 = await register(base, "Human 1");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: {
        minPlayers: 2,
        maxPlayers: 3,
        isPublic: true,
        libraryIds: [libraryId],
        libraryCopies: { [libraryId]: 2 },
      },
    }));
    assert.equal(created.room.settings.minPlayers, 2);
    assert.equal(created.room.settings.libraryCopies[libraryId], 2);

    const withComputer = await request(base, "POST", `/api/rooms/${created.room.id}/add-computer`, payload(p1, {
      computerId: computers.players[0].id,
    }));
    const computerPlayer = withComputer.room.players.find((player) => player.isComputer);
    assert.ok(computerPlayer);
    assert.equal(computerPlayer.computerId, computers.players[0].id);
    assert.ok(withComputer.room.startAt, "computer auto-vote should be enough for a two-player room");

    const chat = await request(base, "POST", `/api/rooms/${created.room.id}/chat`, payload(p1, {
      message: "<script>alert(1)</script>\nThis message is intentionally longer than forty characters.",
    }));
    assert.equal(chat.room.chatMessages.length, 1);
    assert.equal(chat.room.chatMessages[0].message.includes("\n"), false);
    assert.ok([...chat.room.chatMessages[0].message].length <= 40);

    const viewer = await request(base, "GET", `/api/card-viewer?libraryIds=${encodeURIComponent(libraryId)}`);
    assert.equal(viewer.libraries[0].id, libraryId);
    assert.ok(viewer.libraries[0].pmvs[0].shots.length > 0);
    assert.ok(viewer.assets.some((asset) => asset.startsWith("/cards/")));

    const pmvIndex = await request(base, "GET", "/api/pmv-index");
    assert.ok(pmvIndex.rows.some((row) => row.libraryId === libraryId && row.pmvId));

    const room = state.rooms.get(created.room.id);
    room.startAt = Date.now() - 1;
    await new Promise((resolve) => setTimeout(resolve, 250));
    assert.equal(room.status, "loading");

    const assets = await request(base, "GET", `/api/rooms/${room.id}/assets?token=${encodeURIComponent(p1.token)}`);
    const loaded = await loadingProgress(base, room.id, p1, assets.assets.length, assets.assets.length, true);
    assert.equal(loaded.game.status, "playing");

    const game = state.games.get(loaded.game.id);
    const humanIndex = game.players.findIndex((player) => player.clientId === p1.clientId);
    const computerIndex = game.players.findIndex((player) => player.isComputer);
    game.players[computerIndex].eliminated = true;
    game.eliminatedOrder = [game.players[computerIndex].clientId];
    game.turnIndex = humanIndex;
    game.turnAvailableAt = Date.now() - 1;
    game.turnDeadlineAt = Date.now() + 1000;
    const finished = await request(base, "POST", `/api/games/${game.id}/play-card`, payload(p1));
    assert.equal(finished.game.status, "finished");

    const profile = await request(base, "GET", `/api/profile/${p1.clientId}?token=${encodeURIComponent(p1.token)}`);
    assert.equal(profile.profile.defeatedComputers[computers.players[0].id], 1);
    const leaderboard = await request(base, "GET", "/api/leaderboard");
    assert.ok(leaderboard.players.some((player) => player.isComputer && player.computerId === computers.players[0].id));
  } finally {
    await close(server);
  }
});

test("god name font subset endpoint returns a small cacheable woff2", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const rootDir = path.join(__dirname, "..");
  const fontSourcePath = path.join(rootDir, "public", "assets", "fonts", "source-han-serif-sc-intro.woff2");
  const { server } = createApp({
    rootDir,
    dataFile: path.join(tmpDir, "state.json"),
    fontSourcePath,
    fontCacheDir: path.join(tmpDir, "font-cache"),
  });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const response = await requestRaw(base, `/api/fonts/god-name-subset.woff2?text=${encodeURIComponent("GODPMV")}`);
    assert.equal(response.status, 200);
    assert.match(response.headers.get("content-type") || "", /font\/woff2/);
    assert.match(response.headers.get("cache-control") || "", /max-age=31536000/);
    const bytes = Buffer.from(await response.arrayBuffer());
    assert.ok(bytes.length > 0);
    assert.ok(bytes.length < fs.statSync(fontSourcePath).size);

    const cachedResponse = await requestRaw(base, `/api/fonts/god-name-subset.woff2?text=${encodeURIComponent("GODPMV")}`);
    assert.equal(cachedResponse.status, 200);
  } finally {
    await close(server);
  }
});

test("leaderboard returns null for metrics that have not been played yet", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const player = await register(base, "No Games Yet");
    const leaderboard = await request(base, "GET", "/api/leaderboard");
    const row = leaderboard.players.find((item) => item.clientId === player.clientId);

    assert.equal(row.gamesPlayed, 0);
    assert.equal(row.winRate, null);
    assert.equal(row.correctRate, null);
    assert.equal(row.averageRank, null);
  } finally {
    await close(server);
  }
});

test("saved room library copies are used when starting a game", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const library = bootstrap.cardLibraries.find((item) => item.cardCount * 2 <= 120);
    assert.ok(library);
    const p1 = await register(base, "Copy Host");
    const p2 = await register(base, "Copy Guest");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: {
        minPlayers: 2,
        maxPlayers: 4,
        isPublic: true,
        libraryIds: [library.id],
        libraryCopies: { [library.id]: 1 },
      },
    }));
    await request(base, "POST", `/api/rooms/${created.room.id}/join`, payload(p2));
    const settings = await request(base, "POST", `/api/rooms/${created.room.id}/settings`, payload(p1, {
      settings: {
        libraryIds: [library.id],
        libraryCopies: { [library.id]: 2 },
        startVoteThresholdMode: "auto",
        startVoteThreshold: null,
        allowEmptyBell: false,
        randomBacks: false,
        conflictResolution: true,
        disconnectProtection: true,
      },
    }));
    assert.equal(settings.room.settings.libraryCopies[library.id], 2);

    const started = await request(base, "POST", `/api/rooms/${created.room.id}/start`, payload(p1));
    const game = state.games.get(started.game.id);

    assert.equal(game.discardedCards + game.players.reduce((sum, player) => sum + player.drawPile.length, 0), library.cardCount * 2);
  } finally {
    await close(server);
  }
});

test("continue votes after results ignore computer players", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const computers = await request(base, "GET", "/api/computer-players");
    const p1 = await register(base, "Continue Human");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 2, maxPlayers: 4, isPublic: true, libraryIds },
    }));
    await request(base, "POST", `/api/rooms/${created.room.id}/add-computer`, payload(p1, {
      computerId: computers.players[0].id,
    }));
    const started = await request(base, "POST", `/api/rooms/${created.room.id}/start`, payload(p1));
    await loadingProgress(base, created.room.id, p1, 1, 1, true);

    const game = state.games.get(started.game.id);
    const room = state.rooms.get(created.room.id);
    game.status = "finished";
    game.winnerId = p1.clientId;
    game.finishedAt = Date.now();
    game.continueVotes = [];
    game.continueCountdownStartedAt = null;
    game.continueReturnAt = null;
    room.status = "finished";

    await request(base, "POST", `/api/games/${game.id}/continue`, payload(p1));

    assert.deepEqual(game.continueVotes, [p1.clientId]);
    assert.ok(game.continueReturnAt, "one connected human should be enough to start continue countdown");
  } finally {
    await close(server);
  }
});

test("loading room kicks disconnected players after ten seconds and returns to waiting if too small", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const p1 = await register(base, "Loading Host");
    const p2 = await register(base, "Loading Guest");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 2, maxPlayers: 4, isPublic: true, libraryIds },
    }));
    await request(base, "POST", `/api/rooms/${created.room.id}/join`, payload(p2));
    const started = await request(base, "POST", `/api/rooms/${created.room.id}/start`, payload(p1));

    const game = state.games.get(started.game.id);
    const room = state.rooms.get(created.room.id);
    const guest = state.players.get(p2.clientId);
    guest.connected = false;
    guest.lastSeenAt = Date.now() - 11000;
    const gameGuest = game.players.find((player) => player.clientId === p2.clientId);
    gameGuest.connected = false;

    await new Promise((resolve) => setTimeout(resolve, 250));

    assert.equal(room.players.includes(p2.clientId), false);
    assert.equal(state.players.get(p2.clientId).currentRoomId, null);
    assert.equal(room.status, "waiting");
    assert.equal(room.gameId, null);
    assert.equal(game.status, "aborted");
    assert.deepEqual(room.players, [p1.clientId]);
  } finally {
    await close(server);
  }
});

test("computer play and ring waits stop when another bell starts settling", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const computers = await request(base, "GET", "/api/computer-players");
    const p1 = await register(base, "FSM Host");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 2, maxPlayers: 4, isPublic: true, libraryIds },
    }));
    await request(base, "POST", `/api/rooms/${created.room.id}/add-computer`, payload(p1, {
      computerId: computers.players[0].id,
    }));
    await request(base, "POST", `/api/rooms/${created.room.id}/add-computer`, payload(p1, {
      computerId: computers.players[1].id,
    }));
    const started = await request(base, "POST", `/api/rooms/${created.room.id}/start`, payload(p1));
    await loadingProgress(base, created.room.id, p1, 1, 1, true);

    const game = state.games.get(started.game.id);
    const computerPlayers = game.players.filter((player) => player.isComputer);
    const now = Date.now();
    game.lockedUntil = now + 1500;
    game.bellCount += 1;
    computerPlayers[0].computerState = {
      name: "play",
      observedPlayCount: game.playCount,
      observedBellCount: game.bellCount - 1,
      actionAt: now + 5000,
    };
    computerPlayers[1].computerState = {
      name: "ring",
      observedPlayCount: game.playCount,
      observedBellCount: game.bellCount - 1,
      actionAt: now + 5000,
    };

    await new Promise((resolve) => setTimeout(resolve, 250));

    assert.equal(computerPlayers[0].computerState.name, "settling");
    assert.equal(computerPlayers[1].computerState.name, "settling");
    assert.equal(computerPlayers[0].computerState.waitUntil, game.lockedUntil);
    assert.equal(computerPlayers[1].computerState.waitUntil, game.lockedUntil);
  } finally {
    await close(server);
  }
});

test("defeating GOD with fewer than three unplayed cards does not grant god reward", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const computers = await request(base, "GET", "/api/computer-players");
    const god = computers.players.find((computer) => computer.id === "computer_god");
    assert.ok(god);
    const p1 = await register(base, "Almost God Slayer");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 2, maxPlayers: 4, isPublic: true, libraryIds },
    }));
    await request(base, "POST", `/api/rooms/${created.room.id}/add-computer`, payload(p1, {
      computerId: god.id,
    }));
    const started = await request(base, "POST", `/api/rooms/${created.room.id}/start`, payload(p1));
    await loadingProgress(base, created.room.id, p1, 1, 1, true);

    const game = state.games.get(started.game.id);
    const humanIndex = game.players.findIndex((player) => player.clientId === p1.clientId);
    const godIndex = game.players.findIndex((player) => player.computerId === "computer_god");
    game.players[godIndex].eliminated = true;
    game.players[godIndex].rank = 2;
    game.eliminatedOrder = [game.players[godIndex].clientId];
    game.players[humanIndex].drawPile = game.players[humanIndex].drawPile.slice(0, 3);
    game.turnIndex = humanIndex;
    game.turnAvailableAt = Date.now() - 1;
    game.turnDeadlineAt = Date.now() + 1000;
    const finished = await request(base, "POST", `/api/games/${game.id}/play-card`, payload(p1));
    assert.equal(finished.game.status, "finished");
    assert.equal(state.data.matchHistory[0].players.find((player) => player.clientId === p1.clientId).finalDrawCount, 2);

    const profile = await request(base, "GET", `/api/profile/${p1.clientId}?token=${encodeURIComponent(p1.token)}`);
    assert.equal(profile.profile.defeatedComputers.computer_god || 0, 0);
    assert.equal(profile.profile.godSlayer, false);
    assert.equal(profile.profile.godRewardGameId, null);
    const winnerSnapshot = await request(base, "GET", `/api/bootstrap?token=${encodeURIComponent(p1.token)}`);
    assert.equal(winnerSnapshot.player.godSlayer, false);
    assert.equal(winnerSnapshot.player.godRewardGameId, null);
  } finally {
    await close(server);
  }
});

test("first human defeat of GOD with three unplayed cards adds god badge and reward marker", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const computers = await request(base, "GET", "/api/computer-players");
    const god = computers.players.find((computer) => computer.id === "computer_god");
    assert.ok(god);
    const p1 = await register(base, "God Slayer");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 2, maxPlayers: 4, isPublic: true, libraryIds },
    }));
    await request(base, "POST", `/api/rooms/${created.room.id}/add-computer`, payload(p1, {
      computerId: god.id,
    }));
    const started = await request(base, "POST", `/api/rooms/${created.room.id}/start`, payload(p1));
    await loadingProgress(base, created.room.id, p1, 1, 1, true);

    const game = state.games.get(started.game.id);
    const humanIndex = game.players.findIndex((player) => player.clientId === p1.clientId);
    const godIndex = game.players.findIndex((player) => player.computerId === "computer_god");
    game.players[godIndex].eliminated = true;
    game.players[godIndex].rank = 2;
    game.eliminatedOrder = [game.players[godIndex].clientId];
    game.players[humanIndex].drawPile = game.players[humanIndex].drawPile.slice(0, 4);
    game.turnIndex = humanIndex;
    game.turnAvailableAt = Date.now() - 1;
    game.turnDeadlineAt = Date.now() + 1000;
    const finished = await request(base, "POST", `/api/games/${game.id}/play-card`, payload(p1));
    assert.equal(finished.game.status, "finished");
    assert.equal(state.data.matchHistory[0].players.find((player) => player.clientId === p1.clientId).finalDrawCount, 3);

    const profile = await request(base, "GET", `/api/profile/${p1.clientId}?token=${encodeURIComponent(p1.token)}`);
    assert.equal(profile.profile.defeatedComputers.computer_god, 1);
    assert.equal(profile.profile.godSlayer, true);
    assert.equal(profile.profile.godRewardGameId, game.id);
    const winnerSnapshot = await request(base, "GET", `/api/bootstrap?token=${encodeURIComponent(p1.token)}`);
    assert.equal(winnerSnapshot.player.godSlayer, true);
    assert.equal(winnerSnapshot.player.godRewardGameId, game.id);
    assert.equal(winnerSnapshot.currentRoom.players.find((player) => player.clientId === p1.clientId).godSlayer, true);
  } finally {
    await close(server);
  }
});

test("loading progress is monotonic and finished rooms can be reused or disbanded", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const p1 = await register(base, "Host");
    const p2 = await register(base, "Player B");
    const p3 = await register(base, "Player C");
    const p4 = await register(base, "Fresh Joiner");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    }));
    const roomId = created.room.id;
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p2));
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p3));
    const started = await request(base, "POST", `/api/rooms/${roomId}/start`, payload(p1));
    const total = 10;

    await loadingProgress(base, roomId, p1, total, total, true);
    const stale = await loadingProgress(base, roomId, p1, 2, total, false);
    const stalePlayer = stale.room.players.find((player) => player.clientId === p1.clientId);
    assert.equal(stalePlayer.ready, true);
    assert.equal(stalePlayer.loadingLoaded, total);
    assert.equal(stalePlayer.loadingProgress, 100);

    await loadingProgress(base, roomId, p2, total, total, true);
    await loadingProgress(base, roomId, p3, total, total, true);

    const game = state.games.get(started.game.id);
    const room = state.rooms.get(roomId);
    game.status = "finished";
    game.winnerId = p1.clientId;
    game.finishedAt = Date.now();
    room.status = "finished";

    const settings = await request(base, "POST", `/api/rooms/${roomId}/settings`, payload(p1, {
      settings: { libraryIds, allowEmptyBell: true, randomBacks: false, conflictResolution: true, disconnectProtection: true },
    }));
    assert.equal(settings.room.status, "waiting");
    assert.equal(settings.room.gameId, null);

    const joined = await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p4));
    assert.equal(joined.spectator, false);
    assert.equal(joined.room.players.some((player) => player.clientId === p4.clientId), true);

    await request(base, "POST", `/api/rooms/${roomId}/disband`, payload(p1));
    const afterDisband = await request(base, "GET", `/api/bootstrap?token=${encodeURIComponent(p4.token)}`);
    assert.equal(afterDisband.currentRoom, null);
    assert.equal(afterDisband.rooms.some((item) => item.id === roomId), false);
    const missing = await requestFailure(base, "POST", `/api/rooms/${roomId}/join`, payload(p4));
    assert.match(missing.error, /房间不存在/);
  } finally {
    await close(server);
  }
});

test("leaving a finished room is not undone by a later SSE reconnect", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;
  let stream = null;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const p1 = await register(base, "Host Finished");
    const p2 = await register(base, "Leaving Player");
    const p3 = await register(base, "Stayer");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    }));
    const roomId = created.room.id;
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p2));
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p3));
    const started = await request(base, "POST", `/api/rooms/${roomId}/start`, payload(p1));
    await loadingProgress(base, roomId, p1, 1, 1, true);
    await loadingProgress(base, roomId, p2, 1, 1, true);
    await loadingProgress(base, roomId, p3, 1, 1, true);

    const game = state.games.get(started.game.id);
    const room = state.rooms.get(roomId);
    game.status = "finished";
    game.winnerId = p1.clientId;
    game.finishedAt = Date.now();
    game.continueVotes = [p1.clientId, p2.clientId, p3.clientId];
    game.continueCountdownStartedAt = Date.now() - 11000;
    game.continueReturnAt = Date.now() - 1;
    room.status = "finished";

    await request(base, "POST", `/api/rooms/${roomId}/leave`, payload(p2));
    stream = await openEvents(base, p2);
    await stream.nextEvent();
    await new Promise((resolve) => setTimeout(resolve, 650));

    const p2Bootstrap = await request(base, "GET", `/api/bootstrap?token=${encodeURIComponent(p2.token)}`);
    const p1Bootstrap = await request(base, "GET", `/api/bootstrap?token=${encodeURIComponent(p1.token)}`);
    assert.equal(p2Bootstrap.currentRoom, null);
    assert.equal(p1Bootstrap.currentRoom.players.some((player) => player.clientId === p2.clientId), false);
  } finally {
    if (stream) await stream.close();
    await close(server);
  }
});

test("leaving a finished room with an active SSE stream survives later waiting reset", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;
  let stream = null;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const p1 = await register(base, "Host Active Stream");
    const p2 = await register(base, "Leaving Active Stream");
    const p3 = await register(base, "Voting Stayer");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    }));
    const roomId = created.room.id;
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p2));
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p3));
    const started = await request(base, "POST", `/api/rooms/${roomId}/start`, payload(p1));
    await loadingProgress(base, roomId, p1, 1, 1, true);
    await loadingProgress(base, roomId, p2, 1, 1, true);
    await loadingProgress(base, roomId, p3, 1, 1, true);

    const game = state.games.get(started.game.id);
    const room = state.rooms.get(roomId);
    game.status = "finished";
    game.winnerId = p1.clientId;
    game.finishedAt = Date.now();
    game.continueVotes = [p1.clientId, p2.clientId, p3.clientId];
    game.continueCountdownStartedAt = Date.now() - 11000;
    game.continueReturnAt = Date.now() - 1;
    room.status = "finished";

    stream = await openEvents(base, p2);
    await stream.nextEvent();
    await request(base, "POST", `/api/rooms/${roomId}/leave`, payload(p2));
    await waitForEvent(stream, "state", 2000);
    await new Promise((resolve) => setTimeout(resolve, 650));

    const p2Bootstrap = await request(base, "GET", `/api/bootstrap?token=${encodeURIComponent(p2.token)}`);
    const publicBootstrap = await request(base, "GET", "/api/bootstrap");
    const publicRoom = publicBootstrap.rooms.find((item) => item.id === roomId);

    assert.equal(p2Bootstrap.currentRoom, null);
    assert.equal(publicRoom.players.some((player) => player.clientId === p2.clientId), false);
  } finally {
    if (stream) await stream.close();
    await close(server);
  }
});

test("bootstrap ignores stale currentRoomId when room membership has been removed", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const p1 = await register(base, "Host Stale Room");
    const p2 = await register(base, "Stale Current Room");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    }));
    const roomId = created.room.id;
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p2));

    const room = state.rooms.get(roomId);
    room.players = room.players.filter((clientId) => clientId !== p2.clientId);
    state.players.get(p2.clientId).currentRoomId = roomId;

    const p2Bootstrap = await request(base, "GET", `/api/bootstrap?token=${encodeURIComponent(p2.token)}`);

    assert.equal(p2Bootstrap.currentRoom, null);
    assert.equal(p2Bootstrap.rooms.find((item) => item.id === roomId).players.some((player) => player.clientId === p2.clientId), false);
  } finally {
    await close(server);
  }
});

test("resetting a finished room does not resurrect players who already left", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const p1 = await register(base, "Host Reset");
    const p2 = await register(base, "Left Before Reset");
    const p3 = await register(base, "Reset Stayer");
    const p4 = await register(base, "Reset Joiner");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    }));
    const roomId = created.room.id;
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p2));
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p3));
    const started = await request(base, "POST", `/api/rooms/${roomId}/start`, payload(p1));
    await loadingProgress(base, roomId, p1, 1, 1, true);
    await loadingProgress(base, roomId, p2, 1, 1, true);
    await loadingProgress(base, roomId, p3, 1, 1, true);

    const game = state.games.get(started.game.id);
    const room = state.rooms.get(roomId);
    game.status = "finished";
    game.winnerId = p1.clientId;
    game.finishedAt = Date.now();
    room.status = "finished";

    await request(base, "POST", `/api/rooms/${roomId}/leave`, payload(p2));
    room.players.push(p2.clientId);
    const joined = await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p4));

    assert.equal(joined.room.status, "waiting");
    assert.equal(joined.room.players.some((player) => player.clientId === p2.clientId), false);
    assert.equal(joined.room.players.some((player) => player.clientId === p4.clientId), true);
  } finally {
    await close(server);
  }
});

test("old finished game cannot reclaim a room after it has returned to waiting", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const p1 = await register(base, "Host Old Game");
    const p2 = await register(base, "Leaves Waiting Room");
    const p3 = await register(base, "Old Game Stayer");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    }));
    const roomId = created.room.id;
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p2));
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p3));
    const started = await request(base, "POST", `/api/rooms/${roomId}/start`, payload(p1));
    await loadingProgress(base, roomId, p1, 1, 1, true);
    await loadingProgress(base, roomId, p2, 1, 1, true);
    await loadingProgress(base, roomId, p3, 1, 1, true);

    const game = state.games.get(started.game.id);
    const room = state.rooms.get(roomId);
    game.status = "finished";
    game.winnerId = p1.clientId;
    game.finishedAt = Date.now();
    game.continueVotes = [p1.clientId, p2.clientId, p3.clientId];
    game.continueCountdownStartedAt = Date.now() - 11000;
    game.continueReturnAt = Date.now() - 1;
    room.status = "finished";

    await new Promise((resolve) => setTimeout(resolve, 650));
    assert.equal(room.status, "waiting");
    assert.equal(room.gameId, null);
    assert.equal(room.players.includes(p2.clientId), true);

    await request(base, "POST", `/api/rooms/${roomId}/leave`, payload(p2));
    assert.equal(room.players.includes(p2.clientId), false);
    await new Promise((resolve) => setTimeout(resolve, 650));

    const p2Bootstrap = await request(base, "GET", `/api/bootstrap?token=${encodeURIComponent(p2.token)}`);
    const publicBootstrap = await request(base, "GET", "/api/bootstrap");
    const publicRoom = publicBootstrap.rooms.find((item) => item.id === roomId);

    assert.equal(p2Bootstrap.currentRoom, null);
    assert.equal(publicRoom.players.some((player) => player.clientId === p2.clientId), false);
  } finally {
    await close(server);
  }
});

test("automatic play emits the same play-card audio event as manual play", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;
  let stream = null;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const p1 = await register(base, "Audio Host");
    const p2 = await register(base, "Audio B");
    const p3 = await register(base, "Audio C");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    }));
    const roomId = created.room.id;
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p2));
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p3));
    const started = await request(base, "POST", `/api/rooms/${roomId}/start`, payload(p1));
    await loadingProgress(base, roomId, p1, 1, 1, true);
    await loadingProgress(base, roomId, p2, 1, 1, true);
    await loadingProgress(base, roomId, p3, 1, 1, true);

    stream = await openEvents(base, p1);
    await stream.nextEvent();
    const game = state.games.get(started.game.id);
    game.turnDeadlineAt = Date.now() - 1;
    game.turnAvailableAt = Date.now() - 1;

    const event = await waitForEvent(stream, "audio", 2500);
    assert.match(event, /"type":"play-card"/);
    assert.match(event, new RegExp(`"gameId":"${started.game.id}"`));
  } finally {
    if (stream) await stream.close();
    await close(server);
  }
});

test("waiting room disconnected players are kicked after two minutes", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const p1 = await register(base, "Kick Host");
    const p2 = await register(base, "Kick Guest");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 2, maxPlayers: 8, isPublic: true, libraryIds },
    }));
    const roomId = created.room.id;
    await request(base, "POST", `/api/rooms/${roomId}/join`, payload(p2));

    const guest = state.players.get(p2.clientId);
    guest.connected = false;
    guest.lastSeenAt = Date.now() - 121000;
    await new Promise((resolve) => setTimeout(resolve, 250));

    const room = state.rooms.get(roomId);
    assert.equal(room.players.includes(p2.clientId), false);
    assert.equal(state.players.get(p2.clientId).currentRoomId, null);
    const snapshot = await request(base, "GET", `/api/bootstrap?token=${encodeURIComponent(p1.token)}`);
    assert.equal(snapshot.currentRoom.players.some((player) => player.clientId === p2.clientId), false);
  } finally {
    await close(server);
  }
});

test("short server tick does not broadcast state events while waiting room state is unchanged", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;
  let stream = null;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const p1 = await register(base, "Quiet Host");
    await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 2, maxPlayers: 8, isPublic: true, libraryIds },
    }));

    stream = await openEvents(base, p1);
    await stream.nextEvent();
    await stream.nextEvent();
    const timedOut = await Promise.race([
      stream.nextEvent(650).then(() => false).catch(() => true),
      new Promise((resolve) => setTimeout(() => resolve(true), 700)),
    ]);

    assert.equal(timedOut, true);
  } finally {
    if (stream) await stream.close();
    await close(server);
  }
});

test("aggressive computer player acts through the server FSM", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;
  let stream = null;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const computers = await request(base, "GET", "/api/computer-players");
    const p1 = await register(base, "AI Host");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 2, maxPlayers: 3, isPublic: true, libraryIds },
    }));
    await request(base, "POST", `/api/rooms/${created.room.id}/add-computer`, payload(p1, {
      computerId: computers.players[0].id,
    }));
    const computerId = state.rooms.get(created.room.id).players.find((clientId) => state.players.get(clientId)?.isComputer);
    state.players.get(computerId).profile = {
      ...state.players.get(computerId).profile,
      playDelayMeanSeconds: 0,
      playDelayStdSeconds: 0,
      reactionMeanSeconds: 0,
      reactionStdSeconds: 0,
      matchDetectionProbability: 1,
      falseRingProbability: 0,
    };

    const started = await request(base, "POST", `/api/rooms/${created.room.id}/start`, payload(p1));
    const assets = await request(base, "GET", `/api/rooms/${created.room.id}/assets?token=${encodeURIComponent(p1.token)}`);
    await loadingProgress(base, created.room.id, p1, assets.assets.length, assets.assets.length, true);

    const game = state.games.get(started.game.id);
    game.turnIndex = game.players.findIndex((player) => player.clientId === computerId);
    game.turnAvailableAt = Date.now() - 1;
    game.turnDeadlineAt = Date.now() + 6000;
    stream = await openEvents(base, p1);
    await stream.nextEvent();
    await stream.nextEvent();

    const event = await waitForEvent(stream, "audio", 3000);
    assert.match(event, /"type":"play-card"/);
    assert.ok(game.players.find((player) => player.clientId === computerId).stats.plays >= 1);
  } finally {
    if (stream) await stream.close();
    await close(server);
  }
});

test("aggressive computer-only game keeps making progress", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server, state } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap");
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);
    const computers = await request(base, "GET", "/api/computer-players");
    const p1 = await register(base, "AI Liveness Host");
    const created = await request(base, "POST", "/api/rooms", payload(p1, {
      settings: { minPlayers: 2, maxPlayers: 4, isPublic: true, libraryIds },
    }));
    for (const computer of computers.players.slice(0, 3)) {
      await request(base, "POST", `/api/rooms/${created.room.id}/add-computer`, payload(p1, {
        computerId: computer.id,
      }));
    }

    const computerIds = state.rooms.get(created.room.id).players.filter((clientId) => state.players.get(clientId)?.isComputer);
    for (const computerId of computerIds) {
      state.players.get(computerId).profile = {
        ...state.players.get(computerId).profile,
        playDelayMeanSeconds: 0,
        playDelayStdSeconds: 0,
        reactionMeanSeconds: 0,
        reactionStdSeconds: 0,
        matchDetectionProbability: 1,
        falseRingProbability: 1,
      };
    }

    const started = await request(base, "POST", `/api/rooms/${created.room.id}/start`, payload(p1));
    const assets = await request(base, "GET", `/api/rooms/${created.room.id}/assets?token=${encodeURIComponent(p1.token)}`);
    await loadingProgress(base, created.room.id, p1, assets.assets.length, assets.assets.length, true);

    const game = state.games.get(started.game.id);
    const host = game.players.find((player) => player.clientId === p1.clientId);
    host.eliminated = true;
    host.rank = game.players.length;
    game.turnIndex = game.players.findIndex((player) => computerIds.includes(player.clientId));
    game.turnAvailableAt = Date.now() - 1;
    game.turnDeadlineAt = Date.now() + 6000;

    const signature = () => [
      game.status,
      game.playCount,
      game.successBellCount,
      game.failBellCount,
      game.lockedUntil,
      game.turnIndex,
      game.players.filter((player) => !player.eliminated && !player.exited).length,
    ].join(":");
    const startedAt = Date.now();
    let lastSignature = signature();
    let lastChangedAt = Date.now();
    let maxQuietMs = 0;
    while (Date.now() - startedAt < 10000 && game.status === "playing") {
      await new Promise((resolve) => setTimeout(resolve, 100));
      const nextSignature = signature();
      if (nextSignature !== lastSignature) {
        maxQuietMs = Math.max(maxQuietMs, Date.now() - lastChangedAt);
        lastSignature = nextSignature;
        lastChangedAt = Date.now();
      }
      assert.ok(Date.now() - lastChangedAt < 7500, `computer FSM appears stuck for ${Date.now() - lastChangedAt}ms`);
    }
    maxQuietMs = Math.max(maxQuietMs, Date.now() - lastChangedAt);

    assert.ok(game.playCount >= 2, `expected repeated computer plays, got ${game.playCount}`);
    assert.ok(game.successBellCount + game.failBellCount >= 1, "expected at least one computer bell attempt");
    assert.ok(maxQuietMs < 7500, `expected no long quiet period, got ${maxQuietMs}ms`);
  } finally {
    await close(server);
  }
});
