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
