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
  renderChat: typeof renderChat === "function" ? renderChat : undefined,
  renderHome: typeof renderHome === "function" ? renderHome : undefined,
  renderResult: typeof renderResult === "function" ? renderResult : undefined,
  renderPlayerLabel: typeof renderPlayerLabel === "function" ? renderPlayerLabel : undefined,
  renderRoomSettings: typeof renderRoomSettings === "function" ? renderRoomSettings : undefined,
  handleFocusOut: typeof handleFocusOut === "function" ? handleFocusOut : undefined,
  autoRouteFromState: typeof autoRouteFromState === "function" ? autoRouteFromState : undefined,
  sortRows: typeof sortRows === "function" ? sortRows : undefined,
  fmtPct: typeof fmtPct === "function" ? fmtPct : undefined,
  fmtNum: typeof fmtNum === "function" ? fmtNum : undefined,
  filterPmvIndexRows: typeof filterPmvIndexRows === "function" ? filterPmvIndexRows : undefined,
  renderShell: typeof renderShell === "function" ? renderShell : undefined,
  renderTable: typeof renderTable === "function" ? renderTable : undefined,
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

test("home menu includes a prominent game introduction above the action buttons", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  context.__app.snapshot = { currentRoom: null };

  const html = context.__helpers.renderHome();
  const introIndex = html.indexOf("PMV德国心脏病");
  const menuIndex = html.indexOf("menu-grid");

  assert.ok(introIndex >= 0);
  assert.ok(menuIndex > introIndex);
  assert.match(html, /class="[^"]*game-intro/);
});

test("percentage formatting preserves visible decimals", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));

  assert.equal(context.__helpers.fmtPct(0.98), "98%");
  assert.equal(context.__helpers.fmtPct(0.005), "0.5%");
  assert.equal(context.__helpers.fmtPct(0.0005), "0.05%");
});

test("result continue count and god labels ignore computers", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  const game = {
    ...sampleFinishedGame(),
    winnerId: "a",
    continueVotes: ["a"],
    continueReturnAt: null,
    players: [
      { clientId: "a", username: "Slayer", connected: true, godSlayer: true },
      { clientId: "bot", username: "Bot", connected: true, isComputer: true },
    ],
  };

  assert.match(context.__helpers.renderResult(game), /1\/1/);
  assert.match(context.__helpers.renderPlayerLabel({ username: "Slayer", godSlayer: true }), /god-slayer-name/);
});

test("god slayer styling is limited to player name render points", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  context.__app.route = { name: "home" };
  context.__app.snapshot = {
    player: { clientId: "one", username: "1", godSlayer: true },
    currentRoom: null,
    currentGame: null,
    rooms: [],
    cardLibraries: [],
    computerPlayers: [],
  };

  const html = context.__helpers.renderShell();

  assert.match(html, /<span class="pill"><span class="god-slayer-name">1<\/span><\/span>/);
  assert.match(html, /Version 1\.1/);
  assert.doesNotMatch(html, /Version <span class="god-slayer-name">1<\/span>\.1/);
});

test("table cells do not infer god slayer styling from matching numbers", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  context.__app.snapshot = { player: { username: "1", godSlayer: true } };
  context.__app.profile = { username: "1", godSlayer: true };
  context.__app.leaderboard = { players: [{ username: "1", godSlayer: true }] };

  const html = context.__helpers.renderTable("players", [{ username: "1", wins: 1 }], [
    ["username", "玩家", (row) => row.username],
    ["wins", "胜场", (row) => row.wins],
  ]);

  assert.doesNotMatch(html, /<td><span class="god-slayer-name">1<\/span><\/td><td><span class="god-slayer-name">1<\/span><\/td>/);
});

test("chat render keeps the current draft through game refreshes", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  context.__app.chat = { draft: "中文草稿", focused: true, composing: false, pendingRender: false };

  const html = context.__helpers.renderChat({ chatMessages: [] }, "game");

  assert.match(html, /value="中文草稿"/);
});

test("chat draft is not cleared by a stale empty blur after rerender", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  const target = {
    value: "",
    matches(selector) {
      return selector === "[data-chat-input]";
    },
  };
  context.__app.chat = { draft: "typing during refresh", focused: true, composing: false, pendingRender: false };

  context.__helpers.handleFocusOut({ target });

  assert.equal(context.__app.chat.draft, "typing during refresh");
  assert.equal(context.__app.chat.focused, false);
});

test("room settings render keeps unsaved card copy draft across refreshes", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  context.__app.clientId = "host";
  context.__app.route = { name: "settings", roomId: "1" };
  const room = {
    id: "1",
    hostId: "host",
    status: "waiting",
    settings: {
      libraryIds: ["lib"],
      libraryCopies: { lib: 1 },
      startVoteThresholdMode: "auto",
      startVoteThreshold: null,
    },
    players: [],
  };
  context.__app.snapshot = {
    cardLibraries: [{ id: "lib", name: "Lib", cardCount: 40, pmvCount: 10 }],
    currentRoom: room,
    rooms: [room],
  };
  context.__app.roomSettingsDraft = {
    libraryIds: ["lib"],
    libraryCopies: { lib: 2 },
    startVoteThresholdMode: "auto",
    startVoteThreshold: null,
    allowEmptyBell: false,
    randomBacks: false,
    conflictResolution: true,
    disconnectProtection: true,
  };

  const html = context.__helpers.renderRoomSettings();

  assert.match(html, /name="libraryCopies\.lib"[^>]*value="2"/);
});

test("state refresh keeps host on the room settings page while room waits", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  const room = {
    id: "1",
    hostId: "host",
    status: "waiting",
    gameId: null,
    settings: { libraryIds: ["lib"], libraryCopies: { lib: 1 } },
    players: [],
  };
  context.__app.clientId = "host";
  context.__app.route = { name: "settings", roomId: "1" };
  context.__app.snapshot = {
    player: { clientId: "host" },
    currentRoom: room,
    currentGame: null,
    rooms: [room],
  };

  context.__helpers.autoRouteFromState();

  assert.equal(context.__app.route.name, "settings");
  assert.equal(context.__app.route.roomId, "1");
});

test("empty leaderboard metrics render as dash and sort last", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  const rows = [
    { username: "Empty", winRate: null },
    { username: "Low", winRate: 0.25 },
    { username: "High", winRate: 0.75 },
  ];

  assert.equal(context.__helpers.fmtPct(null), "-");
  assert.equal(context.__helpers.fmtNum(null), "-");
  assert.deepEqual(
    context.__helpers.sortRows(rows, { key: "winRate", dir: "desc" }).map((row) => row.username),
    ["High", "Low", "Empty"],
  );
  assert.deepEqual(
    context.__helpers.sortRows(rows, { key: "winRate", dir: "asc" }).map((row) => row.username),
    ["Low", "High", "Empty"],
  );
});

test("PMV index search is case insensitive across visible fields", () => {
  const context = loadClientWithFetch(async () => ({ ok: true, json: async () => ({}) }));
  const rows = [
    { pmvId: 7, name: "Lose You Now", author: "LUMO_Xu", libraryName: "Base" },
    { pmvId: 8, name: "Stay", author: "Shiron", libraryName: "Extra" },
  ];

  assert.deepEqual(context.__helpers.filterPmvIndexRows(rows, "lumo").map((row) => row.pmvId), [7]);
  assert.deepEqual(context.__helpers.filterPmvIndexRows(rows, "STAY").map((row) => row.pmvId), [8]);
  assert.deepEqual(context.__helpers.filterPmvIndexRows(rows, "8").map((row) => row.pmvId), [8]);
});
