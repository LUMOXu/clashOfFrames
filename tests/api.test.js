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
