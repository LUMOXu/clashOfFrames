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
  vm.runInContext(`${code}
globalThis.__app = app;
globalThis.__refresh = refresh;
globalThis.__helpers = {
  buildResultChartModel: typeof buildResultChartModel === "function" ? buildResultChartModel : undefined,
  resultReplayProgress: typeof resultReplayProgress === "function" ? resultReplayProgress : undefined,
  canContinueAfterResultReplay: typeof canContinueAfterResultReplay === "function" ? canContinueAfterResultReplay : undefined,
  turnBannerDetail: typeof turnBannerDetail === "function" ? turnBannerDetail : undefined,
  startVoteRequirement: typeof startVoteRequirement === "function" ? startVoteRequirement : undefined,
  libraryCopyLimit: typeof libraryCopyLimit === "function" ? libraryCopyLimit : undefined,
  formatChatLine: typeof formatChatLine === "function" ? formatChatLine : undefined,
  renderPlayerLabel: typeof renderPlayerLabel === "function" ? renderPlayerLabel : undefined,
};`, context);
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

function sampleFinishedGame() {
  return {
    status: "finished",
    playCount: 4,
    winnerId: "b",
    resultInfo: {
      players: [
        { clientId: "a", username: "A" },
        { clientId: "b", username: "B" },
      ],
      counts: [
        [3, 3],
        [2, 3],
        [2, 4],
        [1, 4],
        [1, 5],
      ],
    },
    players: [
      { clientId: "a", username: "A", connected: true },
      { clientId: "b", username: "B", connected: true },
    ],
  };
}

test("result chart model keeps fixed domains and clips lines continuously", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  const model = context.__helpers.buildResultChartModel(sampleFinishedGame(), 2.5);

  assert.equal(model.xMax, 4);
  assert.equal(model.yMax, 5);
  assert.equal(model.series.length, 2);
  assert.equal(model.series[1].winner, true);
  assert.deepEqual(Array.from(model.series[0].points.map((point) => point.x)), [0, 1, 2, 2.5]);
  assert.equal(model.series[0].points[3].y, 1.5);
});

test("result replay progresses at twenty cards per second and gates continue", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  const progressGame = { ...sampleFinishedGame(), playCount: 10 };
  const gateGame = sampleFinishedGame();

  assert.equal(context.__helpers.resultReplayProgress(progressGame, 1000, 1250), 5);
  assert.equal(context.__helpers.canContinueAfterResultReplay(gateGame, 1000, 1199), false);
  assert.equal(context.__helpers.canContinueAfterResultReplay(gateGame, 1000, 1200), true);
});

test("turn banner detail shows countdown only for connected current players", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  const self = { clientId: "a" };
  const current = { clientId: "a", username: "A", connected: true };
  const game = { status: "playing", turnDeadlineAt: 9000 };

  assert.match(context.__helpers.turnBannerDetail(game, current, self, false, 1000), /8 秒/);
  assert.equal(
    context.__helpers.turnBannerDetail(game, { ...current, connected: false }, self, false, 1000),
    "玩家掉线，等待自动出牌",
  );
  assert.equal(
    context.__helpers.turnBannerDetail({ status: "playing", turnDeadlineAt: 90000 }, current, self, false, 1000),
    "点击高亮牌堆出牌",
  );
});

test("client helpers format voting, copies, chat, and computer labels", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  const room = {
    players: [{}, {}, {}, {}],
    settings: { startVoteThresholdMode: "auto", startVoteThreshold: null },
  };

  assert.equal(context.__helpers.startVoteRequirement(room), 2);
  assert.equal(context.__helpers.libraryCopyLimit({ cardCount: 45 }), 2);
  assert.equal(
    context.__helpers.formatChatLine({ at: 1000, username: "User", message: "<hello>" }),
    `[${new Date(1000).toLocaleTimeString("zh-CN", { hour12: false })}][User]<hello>`,
  );
  assert.match(context.__helpers.renderPlayerLabel({ username: "Bot", isComputer: true }), /Computer/);
});
