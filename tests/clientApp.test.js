"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolveFn, rejectFn) => {
    resolve = resolveFn;
    reject = rejectFn;
  });
  return { promise, resolve, reject };
}

function loadClientWithFetch(fetchImpl) {
  const code = fs.readFileSync(path.join(__dirname, "..", "public", "app.js"), "utf8");
  const context = {
    Audio: function Audio() {
      return { preload: "", currentTime: 0, play: () => Promise.resolve() };
    },
    EventSource: function EventSource() {},
    console,
    document: {
      addEventListener() {},
      body: {
        classList: { toggle() {} },
        style: { setProperty() {} },
      },
      querySelector() {
        return { innerHTML: "" };
      },
    },
    fetch: fetchImpl,
    localStorage: {
      getItem() { return "token"; },
      removeItem() {},
      setItem() {},
    },
    setTimeout,
    clearTimeout,
    window: {
      setTimeout,
      clearTimeout,
    },
  };
  vm.createContext(context);
  vm.runInContext(`${code}\nglobalThis.__app = app; globalThis.__refresh = refresh;`, context);
  return context;
}

test("client refresh ignores stale bootstrap responses that finish late", async () => {
  const stale = deferred();
  const fresh = deferred();
  const calls = [stale, fresh];
  const context = loadClientWithFetch(() => {
    const next = calls.shift();
    assert.ok(next, "unexpected fetch call");
    return next.promise;
  });

  const staleRefresh = context.__refresh();
  const freshRefresh = context.__refresh();

  fresh.resolve({
    ok: true,
    status: 200,
    json: async () => ({
      player: { clientId: "self" },
      currentRoom: null,
      rooms: [],
    }),
  });
  await freshRefresh;

  stale.resolve({
    ok: true,
    status: 200,
    json: async () => ({
      player: { clientId: "self" },
      currentRoom: { id: "old-room", status: "waiting" },
      rooms: [{ id: "old-room", status: "waiting" }],
    }),
  });
  await staleRefresh;

  assert.equal(context.__app.snapshot.currentRoom, null);
});
