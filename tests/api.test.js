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

test("API smoke: register players, create room, start, load, and play", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "cof-test-"));
  const { server } = createApp({ rootDir: path.join(__dirname, ".."), dataFile: path.join(tmpDir, "state.json") });
  const port = await listen(server);
  const base = `http://127.0.0.1:${port}`;

  try {
    const bootstrap = await request(base, "GET", "/api/bootstrap?clientId=p1");
    assert.ok(bootstrap.cardLibraries.length >= 1);
    const libraryIds = bootstrap.cardLibraries.map((library) => library.id);

    await request(base, "POST", "/api/player", { clientId: "p1", username: "Player 1" });
    const duplicate = await requestFailure(base, "POST", "/api/player", { clientId: "p2", username: "Player 1" });
    assert.match(duplicate.error, /用户名已被占用/);
    await request(base, "POST", "/api/player", { clientId: "p2", username: "Player 2" });
    await request(base, "POST", "/api/player", { clientId: "p3", username: "Player 3" });
    await request(base, "POST", "/api/player", { clientId: "p4", username: "Player 4" });

    const firstRoom = await request(base, "POST", "/api/rooms", {
      clientId: "p1",
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    });
    const secondRoom = await request(base, "POST", "/api/rooms", {
      clientId: "p1",
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    });
    assert.notEqual(secondRoom.room.id, firstRoom.room.id);
    const p1Bootstrap = await request(base, "GET", "/api/bootstrap?clientId=p1");
    assert.equal(p1Bootstrap.currentRoom.id, secondRoom.room.id);
    assert.equal(p1Bootstrap.rooms.some((room) => room.id === firstRoom.room.id), false);

    const p2OwnRoom = await request(base, "POST", "/api/rooms", {
      clientId: "p2",
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    });
    assert.ok(p2OwnRoom.room.id);
    const p4Room = await request(base, "POST", "/api/rooms", {
      clientId: "p4",
      settings: { minPlayers: 3, maxPlayers: 8, isPublic: true, libraryIds },
    });
    await request(base, "POST", `/api/rooms/${p4Room.room.id}/leave`, { clientId: "p4" });
    const p4Bootstrap = await request(base, "GET", "/api/bootstrap?clientId=p4");
    assert.equal(p4Bootstrap.currentRoom, null);
    const roomId = secondRoom.room.id;
    await request(base, "POST", `/api/rooms/${roomId}/join`, { clientId: "p2" });
    const p2Bootstrap = await request(base, "GET", "/api/bootstrap?clientId=p2");
    assert.equal(p2Bootstrap.currentRoom.id, roomId);
    assert.equal(p2Bootstrap.rooms.some((room) => room.id === p2OwnRoom.room.id), false);
    await request(base, "POST", `/api/rooms/${roomId}/join`, { clientId: "p3" });

    const started = await request(base, "POST", `/api/rooms/${roomId}/start`, { clientId: "p1" });
    assert.equal(started.game.status, "loading");

    await request(base, "POST", `/api/rooms/${roomId}/loading-ready`, { clientId: "p1" });
    await request(base, "POST", `/api/rooms/${roomId}/loading-ready`, { clientId: "p2" });
    const loaded = await request(base, "POST", `/api/rooms/${roomId}/loading-ready`, { clientId: "p3" });
    assert.equal(loaded.game.status, "playing");

    const current = loaded.game.players[loaded.game.turnIndex].clientId;
    const played = await request(base, "POST", `/api/games/${loaded.game.id}/play-card`, { clientId: current });
    assert.equal(played.game.playCount, 1);
  } finally {
    await close(server);
  }
});
