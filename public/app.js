"use strict";

const app = {
  token: localStorage.getItem("cof.token") || "",
  clientId: "",
  snapshot: null,
  profile: null,
  leaderboard: null,
  pmvIndex: null,
  pmvIndexSearch: "",
  pmvSearchFocused: false,
  cardViewer: { loading: false, payload: null, key: "", selectedLibraryIds: [], loaded: 0, total: 0 },
  profileLoading: false,
  leaderboardLoading: false,
  pmvIndexLoading: false,
  route: { name: "home" },
  message: "",
  toast: null,
  loading: { gameId: null, loaded: 0, total: 0, done: false, running: false, cached: false, manifestKey: "", lastReportAt: 0 },
  sorts: {
    profile: { key: "at", dir: "desc" },
    players: { key: "wins", dir: "desc" },
    matches: { key: "playCount", dir: "desc" },
    pmvIndex: { key: "pmvId", dir: "asc" },
  },
  eventSource: null,
  refreshInFlight: null,
  refreshQueued: false,
  refreshRequestSeq: 0,
  stateRefreshTimer: null,
  countdownTimer: null,
  resultReplay: { gameId: null, startedAt: 0 },
  chat: { draft: "", focused: false, composing: false, pendingRender: false },
  roomSettingsDraft: null,
  roomSettingsAutosaveTimer: null,
  roomSettingsAutosaveSeq: 0,
  roomSettingsSaving: false,
  rendering: false,
  backdropRouteName: "",
};

const RESULT_REPLAY_RATE = 20;
const RESULT_CHART_COLORS = ["#f3d775", "#54c4a8", "#ee6b72", "#8ab6ff", "#c58cff", "#ffad66", "#72d37d", "#f08ec2"];
const MAX_VISIBLE_TURN_COUNTDOWN_SECONDS = 8;

document.addEventListener("DOMContentLoaded", init);
document.addEventListener("click", handleClick);
document.addEventListener("submit", handleSubmit);
document.addEventListener("change", handleChange);
document.addEventListener("input", handleInput);
document.addEventListener("focusin", handleFocusIn);
document.addEventListener("focusout", handleFocusOut);
document.addEventListener("compositionstart", handleCompositionStart);
document.addEventListener("compositionend", handleCompositionEnd);

async function init() {
  await refresh();
  if (!app.snapshot.player) {
    setRoute("auth");
    return;
  }
  connectEvents();
  render();
}

async function refresh() {
  const requestSeq = ++app.refreshRequestSeq;
  const query = app.token ? `?token=${encodeURIComponent(app.token)}` : "";
  const previousClientId = app.clientId;
  const snapshot = await getJson(`/api/bootstrap${query}`);
  if (requestSeq < app.refreshRequestSeq) return app.snapshot;
  app.snapshot = snapshot;
  if (app.snapshot.player) {
    app.clientId = app.snapshot.player.clientId;
    if (previousClientId && previousClientId !== app.clientId) {
      app.profile = null;
      app.leaderboard = null;
    }
  } else {
    app.clientId = "";
    app.profile = null;
    app.leaderboard = null;
    if (app.token) {
      localStorage.removeItem("cof.token");
      app.token = "";
    }
  }
}

function connectEvents() {
  if (app.eventSource) app.eventSource.close();
  if (!app.token) return;
  app.eventSource = new EventSource(`/api/events?token=${encodeURIComponent(app.token)}`);
  app.eventSource.addEventListener("state", scheduleStateRefresh);
  app.eventSource.addEventListener("audio", (event) => {
    try {
      const data = JSON.parse(event.data);
      handleAudioEvent(data);
    } catch { /* ignore malformed audio events */ }
  });
}

function scheduleStateRefresh() {
  if (app.stateRefreshTimer) return;
  app.stateRefreshTimer = window.setTimeout(async () => {
    app.stateRefreshTimer = null;
    try {
      await refreshLatest();
      autoRouteFromState();
      render();
    } catch (error) {
      showToast(humanizeError(error.message));
    }
  }, stateRefreshDelay());
}

async function refreshLatest() {
  if (app.refreshInFlight) {
    app.refreshQueued = true;
    return app.refreshInFlight;
  }
  do {
    app.refreshQueued = false;
    try {
      app.refreshInFlight = refresh();
      await app.refreshInFlight;
    } finally {
      app.refreshInFlight = null;
    }
  } while (app.refreshQueued);
  return app.snapshot;
}

function stateRefreshDelay() {
  if (app.route.name === "game" || app.route.name === "loading") return 120;
  return 500;
}

function autoRouteFromState() {
  if (!app.snapshot.player) {
    app.route = { name: "auth" };
    return;
  }
  const room = app.snapshot.currentRoom;
  const game = app.snapshot.currentGame;
  if (!room) {
    if (["waiting", "settings", "loading", "game"].includes(app.route.name)) app.route = { name: "home" };
    return;
  }
  if (["waiting", "settings", "loading", "game"].includes(app.route.name)) {
    if (room.status === "loading") app.route = { name: "loading" };
    if (room.status === "playing" || room.status === "finished") app.route = { name: "game" };
    if (room.status === "waiting" && app.route.name !== "settings" && (!game || game.status !== "playing")) {
      app.route = { name: "waiting", roomId: room.id };
    }
  }
}

function setRoute(name, params = {}) {
  app.route = { name, ...params };
  app.message = "";
  if (name !== "settings" || params.transfer) app.roomSettingsDraft = null;
  if (name === "profile") app.profile = null;
  if (name === "leaderboard") app.leaderboard = null;
  render();
}

function render() {
  captureChatDraft();
  if (app.chat.composing) {
    app.chat.pendingRender = true;
    return;
  }
  syncBackdrop();
  const root = document.querySelector("#app");
  if (!app.snapshot) {
    root.innerHTML = `<main class="page"><div class="panel">加载中...</div></main>`;
    return;
  }

  const content = app.route.name === "auth" || !app.snapshot.player ? renderAuth() : renderShell();
  const shouldRestoreChat = app.chat.focused;
  const shouldRestorePmvSearch = app.pmvSearchFocused;
  app.rendering = true;
  root.innerHTML = content;
  app.rendering = false;
  if (shouldRestoreChat) restoreChatFocus();
  if (shouldRestorePmvSearch) restorePmvSearchFocus();
  renderGodRewardModal();
  if (app.route.name === "loading") beginPreload();
  if (app.route.name === "card-loading") beginCardViewerPreload();
  scheduleCountdownRender();
}

function chatInputElement(root = document) {
  return root?.querySelector?.("[data-chat-input]") || null;
}

function captureChatDraft() {
  const input = chatInputElement();
  if (!input) return;
  app.chat.draft = input.value || "";
  app.chat.focused = document.activeElement === input || app.chat.focused;
}

function restoreChatFocus() {
  const input = chatInputElement();
  if (!input) return;
  input.focus({ preventScroll: true });
  const end = input.value.length;
  try {
    input.setSelectionRange(end, end);
  } catch { /* ignore unsupported input selection */ }
}

function renderGodRewardModal() {
  const player = app.snapshot?.player;
  const game = app.snapshot?.currentGame;
  if (!player?.godSlayer || !player.godRewardGameId || game?.id !== player.godRewardGameId || game.status !== "finished") return;
  const storageKey = `cof.godReward.${player.clientId}.${player.godRewardGameId}`;
  if (localStorage.getItem(storageKey) === "1") return;
  const root = document.querySelector("#app");
  if (!root?.insertAdjacentHTML) return;
  root.insertAdjacentHTML("beforeend", `
    <div class="god-reward-modal" role="dialog" aria-modal="true">
      <div class="god-reward-panel">
        <h2>恭喜！！！</h2>
        <p>你击败了帧封相对之神，胜天至少三子！以后，你的名字会以更醒目的样式出现在所有玩家列表和对局里！</p>
        <p>请截图这个页面发给页面下方的作者，领取GOD Slayer纪念奖励——一份CSBC'25的PMV静帧扑克牌！</p>
        <button class="primary" data-action="dismiss-god-reward" data-game="${escapeAttr(player.godRewardGameId)}">收下</button>
      </div>
    </div>
  `);
}

function isChatInput(target) {
  return Boolean(target?.matches?.("[data-chat-input]"));
}

function pmvSearchInputElement(root = document) {
  return root?.querySelector?.("[data-pmv-search]") || null;
}

function restorePmvSearchFocus() {
  const input = pmvSearchInputElement();
  if (!input) return;
  input.focus({ preventScroll: true });
  const end = input.value.length;
  try {
    input.setSelectionRange(end, end);
  } catch { /* ignore unsupported input selection */ }
}

function isRoomSettingsControl(target) {
  const form = target?.closest?.("form");
  return form?.id === "room-settings-form" && Boolean(target.name);
}

function isPmvSearchInput(target) {
  return Boolean(target?.matches?.("[data-pmv-search]"));
}

function syncBackdrop() {
  const body = document.body;
  if (!body) return;
  const isGame = app.route.name === "game";
  body.classList.toggle("game-route", isGame);
  body.classList.toggle("menu-backdrop", !isGame);
  if (isGame) {
    app.backdropRouteName = "game";
    return;
  }
  const routeKey = `${app.route.name}:${app.route.roomId || ""}:${app.route.transfer ? "transfer" : ""}`;
  if (app.backdropRouteName === routeKey) return;
  app.backdropRouteName = routeKey;
  body.style.setProperty("--menu-bg-image", `url("${randomMenuBackground()}")`);
}

function randomMenuBackground() {
  return `/assets/bg${Math.floor(Math.random() * 3) + 1}.jpg`;
}

function renderShell() {
  return `
    <div class="app">
      <header class="topbar">
        <div class="brand">
          <strong>帧封相对</strong>
          <span>Clash of Frames</span>
        </div>
        <div class="top-actions">
          <span class="pill">${renderPlayerName(app.snapshot.player, "未命名")}</span>
          <button data-action="home">主页</button>
          <button data-action="profile">个人信息</button>
          <button data-action="logout">退出登录</button>
        </div>
      </header>
      ${app.message ? `<main class="page"><div class="message">${escapeHtml(app.message)}</div></main>` : ""}
      ${app.toast ? `<div class="toast">${escapeHtml(app.toast.message)}</div>` : ""}
      ${renderRoute()}
      <footer class="app-footer">Version 1.1, Built by LUMO_Xu &amp; DrowningYu with good vibes.</footer>
    </div>
  `;
}

function renderRoute() {
  const route = app.route.name;
  if (route === "create") return renderCreateRoom();
  if (route === "join") return renderJoinRoom();
  if (route === "rooms") return renderRooms();
  if (route === "rules") return renderRules();
  if (route === "profile") return renderProfile();
  if (route === "leaderboard") return renderLeaderboard();
  if (route === "card-select") return renderCardSelect();
  if (route === "card-loading") return renderCardLoading();
  if (route === "card-info") return renderCardInfo();
  if (route === "pmv-index") return renderPmvIndex();
  if (route === "waiting") return renderWaiting();
  if (route === "settings") return renderRoomSettings();
  if (route === "loading") return renderLoading();
  if (route === "game") return renderGame();
  return renderHome();
}

function renderAuth() {
  return `
    <main class="page narrow">
      <section class="panel">
        <h1>帧封相对</h1>
        <p class="muted">登录后才能创建房间、加入对局和保存战绩。</p>
        <p class="auth-warning">没有找回密码功能！忘记密码请联系管理员重置。</p>
        ${app.message ? `<div class="message">${escapeHtml(app.message)}</div>` : ""}
        <div class="auth-grid">
          <form id="login-form" class="grid">
            <h2>登录</h2>
            <label>用户名
              <input name="username" maxlength="24" required autocomplete="username">
            </label>
            <label>密码
              <input name="password" type="password" required autocomplete="current-password">
            </label>
            <button class="primary" type="submit">登录</button>
          </form>
          <form id="register-form" class="grid">
            <h2>注册</h2>
            <p class="auth-warning">这是一个娱乐项目，请不要用你常用的密码以防止数据泄露。</p>
            <label>用户名
              <input name="username" maxlength="24" required autocomplete="username">
            </label>
            <label>密码
              <input name="password" type="password" minlength="6" required autocomplete="new-password">
            </label>
            <label>确认密码
              <input name="confirmPassword" type="password" minlength="6" required autocomplete="new-password">
            </label>
            <button type="submit">注册并进入</button>
          </form>
        </div>
      </section>
    </main>
  `;
}

function renderHome() {
  const room = app.snapshot.currentRoom;
  return `
    <main class="page">
      <section class="panel">
        <h2>主菜单</h2>
        ${room ? `<p class="status-line">当前房间：${escapeHtml(room.id)}，状态：${statusText(room.status)}</p>` : ""}
        <div class="game-intro">PMV德国心脏病——点击牌堆出牌，如果观察到翻开的牌有两张来自一个PMV，立刻按铃！</div>
        <div class="menu-grid">
          <button class="primary" data-action="create">创建房间</button>
          <button data-action="join">加入房间</button>
          <button data-action="rooms">查看房间</button>
          <button data-action="profile">个人信息</button>
          <button data-action="leaderboard">排行榜</button>
          ${room ? `<button data-action="go-current-room">返回当前房间</button><button class="danger" data-action="leave-room">退出房间</button>` : ""}
        </div>
      </section>
      <section class="panel menu-info-panel">
        <div>
          <h3>详细说明</h3>
          <p class="status-line">翻牌、按铃、淘汰与房间选项的完整说明，也包含网站各页面的功能介绍。</p>
        </div>
        <button data-action="rules">详细说明</button>
      </section>
      <section class="panel menu-info-panel">
        <div>
          <h3>卡组资料</h3>
          <p class="status-line">不知道每张牌属于什么 PMV？打开这个页面吧。</p>
        </div>
        <div class="actions">
          <button data-action="card-select">查看卡牌</button>
        </div>
      </section>
      <section class="panel menu-info-panel">
        <div>
          <h3>卡组提交</h3>
          <p class="status-line">想提交自己的卡组？请查看指南。</p>
        </div>
        <div class="actions">
          <button data-action="pmv-index">卡组提交指南</button>
        </div>
      </section>
    </main>
  `;
}

function renderCreateRoom() {
  const libs = app.snapshot.cardLibraries;
  return `
    <main class="page">
      <section class="panel">
        <h2>创建房间</h2>
        <form id="create-room-form" class="grid">
          <div class="form-grid">
            <label>房间最小人数
              <input name="minPlayers" type="number" min="2" max="8" value="2">
            </label>
            <label>房间最大人数
              <input name="maxPlayers" type="number" min="2" max="8" value="8">
            </label>
          </div>
          <label class="toggle-row">
            <span>公开房间</span>
            <input name="isPublic" type="checkbox" checked>
          </label>
          ${renderLibraryPicker(libs)}
          ${renderComputerPicker(app.snapshot.computerPlayers || [])}
          ${renderStartVoteSettings()}
          ${renderAdvancedSettings()}
          <div class="actions">
            <button class="primary" type="submit">创建房间</button>
            <button type="button" data-action="home">返回</button>
          </div>
        </form>
      </section>
    </main>
  `;
}

function renderJoinRoom() {
  return `
    <main class="page narrow">
      <section class="panel">
        <h2>加入房间</h2>
        <form id="join-room-form" class="grid">
          <label>房间 ID
            <input name="roomId" inputmode="numeric" required>
          </label>
          <div class="actions">
            <button class="primary" type="submit">进入</button>
            <button type="button" data-action="home">返回</button>
          </div>
        </form>
      </section>
    </main>
  `;
}

function renderRooms() {
  const publicRooms = app.snapshot.rooms.filter((room) => room.settings.isPublic);
  return `
    <main class="page">
      <section class="panel">
        <h2>公开房间</h2>
        <div class="grid">
          ${publicRooms.length ? publicRooms.map(renderRoomRow).join("") : `<p class="muted">当前没有公开房间。</p>`}
        </div>
        <div class="actions"><button data-action="home">返回</button></div>
      </section>
    </main>
  `;
}

function renderRoomRow(room) {
  const names = room.players.map((player) => renderPlayerName(player)).join("、") || "无";
  const full = room.players.length >= room.settings.maxPlayers && room.status === "waiting";
  const libraries = libraryNamesForRoom(room).join("、") || "未选择";
  const options = settingTags(room.settings).join("、") || "默认规则";
  return `
    <div class="card room-row">
      <div>
        <strong>#${escapeHtml(room.id)}</strong>
        <span class="pill">${statusText(room.status)}</span>
        <div class="room-meta">
          <span>房主：${escapeHtml(room.hostName || room.players.find((player) => player.clientId === room.hostId)?.username || "房主")}</span>
          <span>${room.players.length}/${room.settings.maxPlayers} 人，至少 ${room.settings.minPlayers} 人开局</span>
          <span>卡库：${escapeHtml(libraries)}</span>
          <span>选项：${escapeHtml(options)}</span>
          <span>玩家：${names}</span>
        </div>
      </div>
      <button data-action="join-public-room" data-room="${escapeAttr(room.id)}" ${full ? "disabled" : ""}>加入</button>
    </div>
  `;
}

function libraryNamesForRoom(room) {
  const selected = new Set(room.settings.libraryIds || []);
  return app.snapshot.cardLibraries
    .filter((library) => selected.has(library.id))
    .map((library) => `${library.name}(${library.cardCount})`);
}

function settingTags(settings = {}) {
  const tags = [];
  if (settings.allowEmptyBell) tags.push("空牌可抢铃");
  if (settings.randomBacks) tags.push("随机卡背");
  if (settings.conflictResolution) tags.push("冲突判定");
  if (settings.disconnectProtection) tags.push("断线保护");
  if (settings.isPublic) tags.push("公开");
  return tags;
}

function renderRules() {
  return `
    <main class="page">
      <section class="panel">
        <h2>详细说明</h2>
        <div class="rules-grid">
          <section>
            <h3>基本流程</h3>
            <p>每名玩家获得等量未出牌，剩余不能平分的牌会被弃置。轮到某名玩家时，点击自己的未出牌堆出牌；牌会进入该玩家的已出牌堆。</p>
            <p>桌面只按每名玩家已出牌堆最上方的牌判断匹配。如果至少两名玩家的顶部牌来自同一个 PMV，任意未淘汰玩家都可以按铃。</p>
            <p>默认出牌有 1 秒最小间隔；轮到玩家后 8 秒未出牌会自动出牌。开启断线保护时，掉线玩家轮到自己后 2 秒自动出牌。</p>
          </section>
          <section>
            <h3>按铃结算</h3>
            <p>按铃正确时，会先高亮匹配的牌，再把所有玩家的已出牌堆收给按铃者，并加入其未出牌堆底部。</p>
            <p>按铃错误时，按铃者从自己的未出牌堆顶部开始，按顺时针顺序给其他未淘汰玩家各一张牌。牌不足时能给几张就给几张。</p>
            <p>结算动画播放期间不能出牌或再次按铃。结算完成后不会立刻自动出牌，需要当前玩家手动继续。</p>
          </section>
          <section>
            <h3>淘汰与胜利</h3>
            <p>默认规则下，玩家未出牌堆耗尽就会被淘汰；已出牌堆仍留在桌面上，并继续参与之后的匹配判断。</p>
            <p>只剩一名未淘汰玩家时游戏结束。结算页显示胜者、出牌数、按铃统计和继续人数；超过半数未掉线且未退出的玩家继续后，会倒计时返回等待区。</p>
          </section>
          <section>
            <h3>房间选项</h3>
            <p>最少/最多人数决定开局门槛和房间容量。公开房间会显示在房间列表，非公开房间需要输入房间 ID 加入。</p>
            <p>卡牌库决定本局会加载和发放哪些牌；加载页会预加载所选牌组，同一牌组再次进入时浏览器会优先复用缓存。</p>
            <p>“空牌可抢铃”允许没有未出牌的玩家继续按铃，但双人局中只剩两人且有人空牌时会直接淘汰空牌玩家。“随机卡背”会混用卡背。“冲突判定”会在出牌与按铃接近同时发生时兼看按铃前一刻的桌面。“断线保护”会保留掉线玩家，并用更短的自动出牌时间维持对局。</p>
          </section>
          <section>
            <h3>主菜单</h3>
            <p>创建房间会新建一个你担任房主的房间；加入房间用于输入房间 ID；查看房间列出公开房间和基本设置。</p>
            <p>个人信息展示你的历史战绩、按铃数据与战胜人机记录。排行榜展示所有玩家与人机的累计战绩，没有有效分母的数据会显示为 “-”。</p>
            <p>如果你已经在房间里，主菜单会出现返回当前房间和退出房间。一个账号同一时间只能在一个房间中。</p>
          </section>
          <section>
            <h3>对局界面</h3>
            <p>自己的玩家位固定在下方视角。高亮的未出牌堆表示当前轮到该玩家；如果轮到你，点击未出牌堆即可出牌。中央铃铛用于抢铃。</p>
            <p>左上角显示正确/错误按铃提示和观战状态。下方日志记录出牌、按铃、淘汰和结算信息。</p>
          </section>
          <section>
            <h3>卡牌与卡组</h3>
            <p>查看卡牌页面可以按卡组浏览所有截图、PMV id、作者、链接和卡背信息，方便对照每张牌来自哪个 PMV。</p>
            <p>卡组提交指南说明 manifest.json、back.png 和 cards 文件夹的组织方式，并提供 PMV id 查询，避免不同卡组给同一个 PMV 重复编号。</p>
          </section>
          <section>
            <h3>加载与同步</h3>
            <p>开始游戏后会进入加载页，浏览器会预加载所选卡组的卡面、卡背和音效。加载完成后服务器同步进入对局。</p>
            <p>房间聊天会随房间状态保存；页面因倒计时、出牌、按铃等事件刷新时，正在输入的草稿会保留在输入框中。</p>
          </section>
        </div>
        <div class="actions"><button data-action="home">返回</button></div>
      </section>
    </main>
  `;
}

function renderWaiting() {
  const room = currentRoom();
  if (!room) return missingRoom();
  const isHost = room.hostId === app.clientId;
  return `
    <main class="page">
      <section class="panel">
        <h2>房间 #${escapeHtml(room.id)}</h2>
        <p class="status-line">${room.players.length}/${room.settings.maxPlayers} 人，至少 ${room.settings.minPlayers} 人开始。</p>
        <div class="grid">
          ${room.players.map((player) => `
            <div class="card player-row">
              <span>${renderPlayerLabel(player)} ${player.clientId === room.hostId ? `<span class="pill ok">房主</span>` : ""}</span>
              <span class="pill ${player.connected ? "ok" : "warn"}">${player.connected ? "在线" : "掉线"}</span>
            </div>
          `).join("")}
        </div>
        ${renderStartVotePanel(room)}
        ${isHost ? renderComputerManager(room) : ""}
        <div class="actions">
          ${isHost ? `<button data-action="settings">房间设置</button><button data-action="transfer">转让房主</button><button class="primary" data-action="start-game" ${room.players.length < room.settings.minPlayers ? "disabled" : ""}>开始游戏</button><button class="danger" data-action="disband-room">解散房间</button>` : ""}
          <button data-action="home">回主菜单</button>
        </div>
        ${renderChat(room)}
      </section>
    </main>
  `;
}

function renderRoomSettings() {
  const room = currentRoom();
  if (!room) return missingRoom();
  if (app.route.transfer) return renderTransferHost(room);
  const settings = roomSettingsForRender(room);
  return `
    <main class="page">
      <section class="panel">
        <h2>房间设置</h2>
        <form id="room-settings-form" class="grid">
          ${renderLibraryPicker(app.snapshot.cardLibraries, settings.libraryIds, settings.libraryCopies)}
          ${renderStartVoteSettings(settings)}
          ${renderAdvancedSettings(settings)}
          ${app.roomSettingsSaving ? `<p class="status-line">正在保存设置...</p>` : ""}
          <div class="actions">
            <button class="primary" type="submit">保存设置</button>
            <button type="button" data-action="waiting">返回</button>
          </div>
        </form>
      </section>
    </main>
  `;
}

function renderTransferHost(room) {
  const candidates = room.players.filter((player) => player.clientId !== app.clientId);
  return `
    <main class="page narrow">
      <section class="panel">
        <h2>转让房主</h2>
        <form id="transfer-host-form" class="grid">
          <label>新房主
            <select name="newHostId" required>
              ${candidates.map((player) => `<option value="${escapeAttr(player.clientId)}">${escapeHtml(player.username)}</option>`).join("")}
            </select>
          </label>
          <div class="actions">
            <button class="primary" type="submit" ${candidates.length ? "" : "disabled"}>转让</button>
            <button type="button" data-action="waiting">返回</button>
          </div>
        </form>
      </section>
    </main>
  `;
}

function renderComputerStats(computer) {
  return `
    <span class="computer-stats">
      <span>出牌 <strong>${fmtNum(computer.playDelayMeanSeconds)}±${fmtNum(computer.playDelayStdSeconds)}s</strong></span>
      <span>反应 <strong>${fmtNum(computer.reactionMeanSeconds)}±${fmtNum(computer.reactionStdSeconds)}s</strong></span>
      <span>发现 <strong>${fmtPct(computer.matchDetectionProbability)}</strong></span>
      <span>错按 <strong>${fmtPct(computer.falseRingProbability)}</strong></span>
    </span>
  `;
}

function roomSettingsForRender(room) {
  if (app.roomSettingsDraft && (!app.roomSettingsDraft.roomId || app.roomSettingsDraft.roomId === room.id)) {
    return app.roomSettingsDraft;
  }
  return room.settings;
}

function renderComputerManager(room) {
  const computers = app.snapshot.computerPlayers || [];
  const present = new Set(room.players.filter((player) => player.isComputer).map((player) => player.computerId));
  if (!computers.length) return "";
  return `
    <div class="panel computer-manager">
      <h3>人机玩家</h3>
      <div class="computer-list">
        ${computers.map((computer) => {
          const inRoom = present.has(computer.id);
          return `
            <div class="computer-row ${isGodComputer(computer) ? "god-computer" : ""}">
              <div>
                <strong>${renderComputerName(computer)}</strong>
                <div class="muted">${escapeHtml(computer.description || "")}</div>
                ${renderComputerStats(computer)}
              </div>
              <button data-action="${inRoom ? "remove-computer" : "add-computer"}" data-computer="${escapeAttr(computer.id)}" ${!inRoom && room.players.length >= room.settings.maxPlayers ? "disabled" : ""}>${inRoom ? "移除" : "邀请"}</button>
            </div>
          `;
        }).join("")}
      </div>
    </div>
  `;
}

function renderStartVotePanel(room) {
  const voted = (room.startVotes || []).includes(app.clientId);
  const remaining = room.startAt ? Math.max(0, Math.ceil((room.startAt - Date.now()) / 1000)) : null;
  return `
    <div class="panel vote-panel">
      <h3>投票开始</h3>
      <p class="status-line">${(room.startVotes || []).length}/${startVoteRequirement(room)} 票，当前 ${room.players.length} 人。</p>
      ${remaining !== null ? `<p class="status-line">倒计时 ${remaining} 秒后自动开始。</p>` : ""}
      <div class="actions">
        <button class="primary" data-action="start-vote" ${voted ? "disabled" : ""}>投票开始</button>
        <button data-action="cancel-start-vote" ${voted ? "" : "disabled"}>取消投票</button>
      </div>
    </div>
  `;
}

function startVoteRequirement(room) {
  if (room?.startVoteRequired) return room.startVoteRequired;
  const count = room?.players?.length || 0;
  if (room?.settings?.startVoteThresholdMode === "manual" && room.settings.startVoteThreshold) {
    return Math.max(1, Math.min(count || 1, Number.parseInt(room.settings.startVoteThreshold, 10) || 1));
  }
  return Math.max(1, count - 2);
}

function renderChat(room, mode = "") {
  const messages = room?.chatMessages || [];
  const draft = app.chat.draft || "";
  return `
    <section class="chat-area ${mode === "game" ? "game-chat" : ""}">
      <h3>聊天</h3>
      <div class="chat-messages">
        ${messages.length ? messages.map((message) => `<div>${escapeHtml(formatChatLine(message))}</div>`).join("") : `<div class="muted">暂无聊天。</div>`}
      </div>
      <form id="chat-form" class="chat-form">
        <input name="message" maxlength="40" autocomplete="off" placeholder="最多 40 字">
        <button type="submit">发送</button>
      </form>
    </section>
  `.replace("name=\"message\"", `data-chat-input name="message" value="${escapeAttr(draft)}"`);
}

function formatChatLine(message) {
  const time = new Date(message.at).toLocaleTimeString("zh-CN", { hour12: false });
  return `[${time}][${message.username}]${message.message}`;
}

function renderPlayerName(player, fallback = "Player") {
  const name = escapeHtml(player?.username || fallback);
  if (isGodComputer(player) || String(player?.username || "").toUpperCase() === "GOD") return `<span class="god-name">${name}</span>`;
  if (player?.godSlayer && !player?.isComputer) return `<span class="god-slayer-name">${name}</span>`;
  return name;
}

function renderPlayerLabel(player) {
  return `${renderPlayerName(player, "玩家")} ${player.isComputer ? `<span class="pill">Computer</span>` : ""}`;
}

function isGodComputer(value) {
  return value?.id === "computer_god"
    || value?.computerId === "computer_god"
    || (value?.isComputer && String(value?.username || value?.name || "").toUpperCase() === "GOD");
}

function renderComputerName(computer) {
  const name = computer?.name || computer?.username || "Computer";
  if (isGodComputer(computer) || String(name).toUpperCase() === "GOD") return `<span class="god-name">${escapeHtml(name)}</span>`;
  return escapeHtml(name);
}

function renderLoading() {
  const room = currentRoom();
  const game = app.snapshot.currentGame;
  if (!room || !game) return missingRoom();
  const rows = loadingRows(room, game);
  const allReady = rows.length > 0 && rows.every((row) => row.ready);
  return `
    <main class="page narrow">
      <section class="panel">
        <h2>游戏加载</h2>
        <p class="status-line">${allReady ? "全部玩家加载完成，正在同步进入对局。" : "等待所有玩家完成所选牌组资源加载。"}</p>
        <div class="loading-list">
          ${rows.map(renderLoadingRow).join("")}
        </div>
        ${renderChat(room)}
      </section>
    </main>
  `;
}

function loadingRows(room, game) {
  return room.players.map((roomPlayer) => {
    const gamePlayer = game.players.find((player) => player.clientId === roomPlayer.clientId) || {};
    const isSelf = roomPlayer.clientId === app.clientId;
    const localTotal = isSelf ? app.loading.total : 0;
    const localLoaded = isSelf ? app.loading.loaded : 0;
    const total = Math.max(gamePlayer.loadingTotal || roomPlayer.loadingTotal || 0, localTotal);
    const loaded = Math.max(gamePlayer.loadingLoaded || roomPlayer.loadingLoaded || 0, localLoaded);
    const progress = total > 0 ? Math.min(100, Math.round(loaded / total * 100)) : (gamePlayer.loadingProgress || roomPlayer.loadingProgress || 0);
    return {
      ...roomPlayer,
      loaded,
      total,
      progress,
      cached: Boolean(gamePlayer.loadingCached || roomPlayer.loadingCached || (isSelf && app.loading.cached)),
      ready: Boolean(gamePlayer.ready || roomPlayer.ready),
    };
  });
}

function renderLoadingRow(player) {
  const detail = player.total ? `${player.loaded}/${player.total} 个资源` : "等待开始";
  const cache = player.cached ? "缓存" : "下载";
  return `
    <div class="loading-player-row">
      <div class="loading-player-head">
        <span>${renderPlayerName(player)}</span>
        <span class="pill ${player.ready ? "ok" : ""}">${player.ready ? "已完成" : `${player.progress}%`}</span>
      </div>
      <div class="loading-bar"><div class="loading-fill" style="width:${player.progress}%"></div></div>
      <div class="loading-detail">${escapeHtml(detail)} · ${escapeHtml(cache)} ${player.connected ? "" : "· 掉线"}</div>
    </div>
  `;
}

function renderGame() {
  const game = app.snapshot.currentGame;
  const room = currentRoom();
  if (!game || !room) return missingRoom();
  const self = game.players.find((player) => player.clientId === app.clientId);
  const spectator = !self;
  const current = game.players[game.turnIndex];
  const locked = game.lockedUntil > Date.now();
  const canPlay = self && game.status === "playing" && current?.clientId === app.clientId && !self.eliminated && drawCount(self) > 0 && !locked;
  const canRing = self && game.status === "playing" && !self.eliminated && !locked;
  return `
    <main class="game-shell">
      <section class="table-area">
        <img class="table-logo" src="/assets/logo.png" alt="">
        <div class="game-hud">
          ${renderGameAlert(game)}
          ${spectator ? `<div class="spectator-badge">观战模式</div>` : ""}
        </div>
        <button class="bell" data-action="ring-bell" ${canRing ? "" : "disabled"} title="按铃">
          <img src="/assets/bell.png" alt="bell">
        </button>
        ${renderStations(game, current, canPlay)}
        ${renderActionAnimation(game)}
        ${game.status === "finished" ? renderResult(game) : ""}
      </section>
      <section class="game-bottom">
        <div class="log-area">
          ${game.logs.map((log) => `<div>${escapeHtml(log.text)}</div>`).join("")}
        </div>
        ${renderChat(room, "game")}
      </section>
    </main>
  `;
}

function renderGameAlert(game) {
  const last = game.lastMatch;
  const actor = last ? game.players.find((player) => player.clientId === last.by) || last : null;
  if (!last || Date.now() - last.at > 5200) return "";
  if (last.type === "fail") {
    return `<div class="game-alert fail"><strong>错误按铃</strong><div>${escapeHtml(last.username)} 交出 ${last.given} 张牌</div></div>`;
  }
  return `
    <div class="game-alert ok">
      <strong>玩家 ${escapeHtml(last.username)} 匹配成功！</strong>
      <div>${escapeHtml(last.pmvName)}</div>
      <div class="match-preview">${last.cards.map((entry) => `<img src="${escapeAttr(entry.card.imageUrl)}" alt="">`).join("")}</div>
    </div>
  `;
}

function renderTurnBanner(game, current, self, locked) {
  if (game.status === "playing" && current?.godSlayer && current.clientId !== self?.clientId) {
    const detail = turnBannerDetail(game, current, self, locked, Date.now());
    return `<div class="turn-banner"><strong>轮到 ${renderPlayerName(current)} 出牌</strong>${detail ? `<span>${escapeHtml(detail)}</span>` : ""}</div>`;
  }
  if (game.status !== "playing" || !current) return "";
  const label = current.clientId === self?.clientId ? "轮到你出牌" : `轮到 ${current.username} 出牌`;
  const detail = turnBannerDetail(game, current, self, locked, Date.now());
  return `<div class="turn-banner"><strong>${escapeHtml(label)}</strong>${detail ? `<span>${escapeHtml(detail)}</span>` : ""}</div>`;
}

function renderStations(game, current, canPlay) {
  const layouts = playerLayouts(game.players);
  const self = game.players.find((item) => item.clientId === app.clientId);
  const stationInfo = layouts.map(({ player, x, y }) => {
    const isCurrent = current?.clientId === player.clientId;
    return `
      <div class="station ${isCurrent ? "current" : ""} ${player.eliminated ? "eliminated" : ""}" style="left:${x}%;top:${y}%">
        <div class="station-name">${renderPlayerLabel(player)} ${player.connected ? "" : "（退出）"} ${player.eliminated ? "（淘汰）" : ""}</div>
        <div class="count">未出 ${drawCount(player)} | 已出 ${displayCount(player)}</div>
        ${isCurrent ? renderTurnBanner(game, current, self, game.lockedUntil > Date.now()) : ""}
      </div>
    `;
  }).join("");
  const drawLayer = layouts.map(({ player, drawX, drawY }) => {
    const own = player.clientId === app.clientId;
    const isCurrent = current?.clientId === player.clientId;
    const visibleDrawPile = visualDrawPile(player, game);
    return `
      <button class="draw-stack table-pile ${isCurrent ? "current" : ""}" style="left:${drawX}%;top:${drawY}%" data-action="play-card" ${own && canPlay ? "" : "disabled"} title="出牌">
        ${renderBackStack(visibleDrawPile)}
      </button>
    `;
  }).join("");
  const displayLayer = layouts.map(({ player, displayX, displayY }) => `
    <div class="display-stack table-pile ${player.eliminated ? "eliminated" : ""}" style="left:${displayX}%;top:${displayY}%">
      ${renderDisplayStack(player.displayPile)}
    </div>
  `).join("");
  return `
    <div class="station-layer">${stationInfo}</div>
    <div class="pile-layer draw-layer">${drawLayer}</div>
    <div class="pile-layer display-layer">${displayLayer}</div>
  `;
}

function drawCount(player) {
  return Number.isFinite(player.drawCount) ? player.drawCount : (player.drawPile || []).length;
}

function displayCount(player) {
  return Number.isFinite(player.displayCount) ? player.displayCount : (player.displayPile || []).length;
}

function visualDrawPile(player, game) {
  const cards = (player.drawPile || []).slice();
  const animation = game.lastAnimation;
  if (!animation || animation.type !== "fail") return cards;
  const elapsed = Math.max(0, Date.now() - animation.startedAt);
  const pendingIds = new Set((animation.transfers || [])
    .filter((transfer) => transfer.toPlayerId === player.clientId && elapsed < transfer.delayMs + animation.moveMs)
    .map((transfer) => transfer.card.id));
  if (pendingIds.size === 0) return cards;
  return cards.filter((card) => !pendingIds.has(card.id));
}

function playerLayouts(players) {
  const rotated = rotatePlayersForSelf(players);
  const n = rotated.length || 1;
  return rotated.map((player, index) => {
    const angle = Math.PI / 2 + (Math.PI * 2 * index / n);
    const x = 50 + Math.cos(angle) * 38;
    const y = 46 + Math.sin(angle) * 34;
    return {
      player,
      x,
      y,
      drawX: x - 8.2,
      drawY: y + 1.2,
      displayX: x + 8.4,
      displayY: y + 1.2,
    };
  });
}

function renderBackStack(cards) {
  if (!cards.length) return "";
  return cards.slice(0, 8).map((card, index) => `
    <img class="mini-card" src="${escapeAttr(card.backUrl)}" alt="" style="${stackStyle(card.id, index, "draw", true)}">
  `).join("");
}

function renderDisplayStack(cards) {
  return cards.map((card, index) => `
    <img class="mini-card" src="${escapeAttr(card.imageUrl)}" alt="${escapeAttr(card.pmvName || "")}" style="${stackStyle(card.id, index, "display", false)}">
  `).join("");
}

function stackStyle(cardId, index, type, topFirst) {
  const hash = hashText(`${type}:${cardId}`);
  const xRange = type === "draw" ? 13 : 16;
  const yRange = type === "draw" ? 16 : 13;
  const rotRange = type === "draw" ? 13 : 11;
  const x = ((hash % 1000) / 999 * 2 - 1) * xRange;
  const y = (((hash >>> 10) % 1000) / 999 * 2 - 1) * yRange;
  const rot = (((hash >>> 20) % 1000) / 999 * 2 - 1) * rotRange;
  const zIndex = topFirst ? 100 - index : index + 1;
  return `transform:translate(${x.toFixed(1)}px, ${y.toFixed(1)}px) rotate(${rot.toFixed(1)}deg); z-index:${zIndex};`;
}

function hashText(text) {
  let hash = 2166136261;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function renderActionAnimation(game) {
  const animation = game.lastAnimation;
  const elapsed = animation ? Math.max(0, Date.now() - animation.startedAt) : 0;
  if (!animation || elapsed > animation.durationMs + 700) return "";
  const layouts = new Map(playerLayouts(game.players).map((layout) => [layout.player.clientId, layout]));
  if (animation.type === "success") {
    const target = layouts.get(animation.targetPlayerId);
    if (!target) return "";
    const matchIds = new Set(animation.matchCardIds || []);
    let order = 0;
    const ghosts = animation.piles.flatMap((pile) => {
      const from = layouts.get(pile.playerId);
      if (!from) return [];
      return pile.cards.map((card) => {
        order += 1;
        const style = flyStyle(from.displayX, from.displayY, target.drawX, target.drawY, animation.durationMs, 0, elapsed);
        const matchClass = matchIds.has(card.id) ? " match-ghost" : "";
        return `<img class="fly-card collect-card${matchClass}" src="${escapeAttr(card.imageUrl)}" alt="" style="${style}">`;
      });
    }).join("");
    return `<div class="animation-layer">${ghosts}</div>`;
  }

  if (animation.type === "fail") {
    const ghosts = (animation.transfers || []).map((transfer) => {
      const from = layouts.get(transfer.fromPlayerId);
      const to = layouts.get(transfer.toPlayerId);
      if (!from || !to) return "";
      const style = flyStyle(from.drawX, from.drawY, to.drawX, to.drawY, animation.moveMs, transfer.delayMs, elapsed);
      return `<img class="fly-card fail-card" src="${escapeAttr(transfer.card.backUrl)}" alt="" style="${style}">`;
    }).join("");
    return `<div class="animation-layer">${ghosts}</div>`;
  }
  return "";
}

function flyStyle(fromX, fromY, toX, toY, durationMs, delayMs = 0, elapsedMs = 0) {
  const effectiveDelay = delayMs - elapsedMs;
  return `--from-left:${fromX}%;--from-top:${fromY}%;--to-left:${toX}%;--to-top:${toY}%;animation-duration:${durationMs}ms;animation-delay:${effectiveDelay}ms;`;
}

function resultChartXMax(game) {
  const rowMax = Math.max(0, (game.resultInfo?.counts || []).length - 1);
  return Math.max(0, Number(game.playCount) || rowMax);
}

function resultReplayProgress(game, startedAt, now = Date.now()) {
  const xMax = resultChartXMax(game);
  if (xMax <= 0) return 0;
  const elapsedSeconds = Math.max(0, now - startedAt) / 1000;
  return Math.min(xMax, elapsedSeconds * RESULT_REPLAY_RATE);
}

function canContinueAfterResultReplay(game, startedAt, now = Date.now()) {
  if (!game?.resultInfo?.counts?.length) return true;
  return resultReplayProgress(game, startedAt, now) >= resultChartXMax(game);
}

function ensureResultReplay(game, now = Date.now()) {
  if (!game || game.status !== "finished" || !game.resultInfo?.counts?.length) return 0;
  if (app.resultReplay.gameId !== game.id) {
    app.resultReplay = { gameId: game.id, startedAt: now };
  }
  return app.resultReplay.startedAt;
}

function clippedLinePoints(points, progressX) {
  if (!points.length) return [];
  if (progressX < points[0].x) return [];
  const out = [];
  for (let index = 0; index < points.length; index += 1) {
    const point = points[index];
    if (point.x <= progressX) {
      out.push(point);
      continue;
    }
    const previous = points[index - 1];
    if (previous && previous.x < progressX) {
      const span = point.x - previous.x || 1;
      const ratio = (progressX - previous.x) / span;
      out.push({
        x: progressX,
        y: previous.y + (point.y - previous.y) * ratio,
      });
    }
    break;
  }
  return out;
}

function chartColor(index, winner) {
  return winner ? "#f3d775" : RESULT_CHART_COLORS[index % RESULT_CHART_COLORS.length];
}

function chartPoint(point, model) {
  const plotWidth = model.width - model.pad.left - model.pad.right;
  const plotHeight = model.height - model.pad.top - model.pad.bottom;
  const x = model.pad.left + (model.xMax > 0 ? point.x / model.xMax : 0) * plotWidth;
  const y = model.pad.top + (1 - (model.yMax > 0 ? point.y / model.yMax : 0)) * plotHeight;
  return { x, y };
}

function linePath(points, model) {
  return points.map((point, index) => {
    const plotted = chartPoint(point, model);
    return `${index === 0 ? "M" : "L"} ${plotted.x.toFixed(2)} ${plotted.y.toFixed(2)}`;
  }).join(" ");
}

function buildResultChartModel(game, progressX = resultChartXMax(game)) {
  const counts = game.resultInfo?.counts || [];
  const players = game.resultInfo?.players || [];
  const xMax = resultChartXMax(game);
  const maxCount = counts.reduce((max, row) => Math.max(max, ...row.map((value) => Number(value) || 0)), 0);
  const model = {
    width: 640,
    height: 260,
    pad: { left: 46, right: 18, top: 18, bottom: 34 },
    xMax,
    yMax: Math.max(1, maxCount),
    series: [],
  };
  model.series = players.map((player, playerIndex) => {
    const rawPoints = counts.map((row, rowIndex) => ({
      x: rowIndex,
      y: Number(row[playerIndex]) || 0,
    }));
    const winner = player.clientId === game.winnerId;
    const points = clippedLinePoints(rawPoints, progressX);
    return {
      ...player,
      index: playerIndex,
      winner,
      color: chartColor(playerIndex, winner),
      points,
      path: linePath(points, model),
    };
  });
  return model;
}

function turnBannerDetail(game, current, self, locked, now = Date.now()) {
  if (game.status !== "playing" || !current) return "";
  if (locked) return `结算倒计时 ${Math.ceil((game.lockedUntil - now) / 1000)} 秒`;
  if (current.connected === false) return "玩家掉线，等待自动出牌";
  const remaining = game.turnDeadlineAt ? Math.max(0, Math.ceil((game.turnDeadlineAt - now) / 1000)) : 0;
  const countdown = remaining > 0 && remaining <= MAX_VISIBLE_TURN_COUNTDOWN_SECONDS ? `出牌倒计时 ${remaining} 秒` : "";
  if (current.clientId === self?.clientId) {
    return countdown ? `点击高亮牌堆出牌 · ${countdown}` : "点击高亮牌堆出牌";
  }
  return countdown;
}

function renderResultChart(game, startedAt, now = Date.now()) {
  if (!game.resultInfo?.counts?.length) return "";
  const progressX = resultReplayProgress(game, startedAt, now);
  const model = buildResultChartModel(game, progressX);
  const plotLeft = model.pad.left;
  const plotRight = model.width - model.pad.right;
  const plotTop = model.pad.top;
  const plotBottom = model.height - model.pad.bottom;
  const xTicks = [...new Set([0, Math.floor(model.xMax / 2), model.xMax])];
  const yTicks = [...new Set([0, Math.ceil(model.yMax / 2), model.yMax])];
  const grid = [
    ...xTicks.map((tick) => {
      const point = chartPoint({ x: tick, y: 0 }, model);
      return `<line class="result-grid" x1="${point.x.toFixed(2)}" y1="${plotTop}" x2="${point.x.toFixed(2)}" y2="${plotBottom}"></line><text class="result-axis-label" x="${point.x.toFixed(2)}" y="${model.height - 8}" text-anchor="middle">${tick}</text>`;
    }),
    ...yTicks.map((tick) => {
      const point = chartPoint({ x: 0, y: tick }, model);
      return `<line class="result-grid" x1="${plotLeft}" y1="${point.y.toFixed(2)}" x2="${plotRight}" y2="${point.y.toFixed(2)}"></line><text class="result-axis-label" x="8" y="${(point.y + 4).toFixed(2)}">${tick}</text>`;
    }),
  ].join("");
  const paths = model.series.map((series) => series.path ? `
    <path class="result-line ${series.winner ? "winner" : ""}" d="${escapeAttr(series.path)}" stroke="${escapeAttr(series.color)}"></path>
  ` : "").join("");
  const legend = model.series.map((series) => `
    <span class="result-legend-item ${series.winner ? "winner" : ""}">
      <span class="result-swatch" style="background:${escapeAttr(series.color)}"></span>
      ${renderPlayerName(series)}
    </span>
  `).join("");
  return `
    <div class="result-chart">
      <svg viewBox="0 0 ${model.width} ${model.height}" role="img" aria-label="剩余牌数折线图">
        ${grid}
        <line class="result-axis" x1="${plotLeft}" y1="${plotBottom}" x2="${plotRight}" y2="${plotBottom}"></line>
        <line class="result-axis" x1="${plotLeft}" y1="${plotTop}" x2="${plotLeft}" y2="${plotBottom}"></line>
        ${paths}
      </svg>
      <div class="result-legend">${legend}</div>
    </div>
  `;
}

function renderResult(game) {
  const winner = game.players.find((player) => player.clientId === game.winnerId);
  const average = game.successBellCount ? (game.playCount / game.successBellCount).toFixed(2) : game.playCount;
  const continuablePlayers = game.players.filter((player) => !player.isComputer && player.connected !== false && !player.exited);
  const connectedTotal = continuablePlayers.length;
  const validVotes = game.continueVotes.filter((clientId) => continuablePlayers.some((player) => player.clientId === clientId)).length;
  const countdown = game.continueReturnAt ? Math.max(0, Math.ceil((game.continueReturnAt - Date.now()) / 1000)) : null;
  const now = Date.now();
  const replayStartedAt = ensureResultReplay(game, now);
  const replayDone = canContinueAfterResultReplay(game, replayStartedAt, now);
  return `
    <div class="result-modal">
      <h2>祝贺 ${winner ? renderPlayerName(winner) : "无人"} 胜利</h2>
      <p>总出牌数：${game.playCount}</p>
      <p>抢铃：${game.bellCount} 次，成功 ${game.successBellCount}，失败 ${game.failBellCount}</p>
      <p>平均回合长度：${average}</p>
      ${renderResultChart(game, replayStartedAt, now)}
      <p>继续确认：${validVotes}/${connectedTotal} 人</p>
      ${countdown === null ? "" : `<p>返回等待区倒计时：${countdown} 秒</p>`}
      <button class="primary" data-action="continue-game" ${replayDone ? "" : "disabled"}>${replayDone ? "继续" : "回放中"}</button>
    </div>
  `;
}

function renderProfile() {
  const profile = app.profile;
  if (!profile) {
    loadProfile();
    return `<main class="page"><section class="panel"><h2>个人信息</h2><p class="muted">加载中...</p></section></main>`;
  }
  const history = sortRows(profile.history || [], app.sorts.profile);
  return `
    <main class="page">
      <section class="panel">
        <h2>个人信息</h2>
        <p class="status-line">当前账号：${renderPlayerName(profile || app.snapshot.player, "")}</p>
        <div class="menu-grid">
          <div class="card">参与 ${profile.gamesPlayed}</div>
          <div class="card">胜场 ${profile.wins}</div>
          <div class="card">胜率 ${fmtPct(profile.winRate)}</div>
          <div class="card">拍铃 ${profile.rings} / 正确 ${profile.correctRings} / 错误 ${profile.wrongRings}</div>
          <div class="card">赢牌 ${profile.wonCards}</div>
          <div class="card">平均排名 ${fmtNum(profile.averageRank)}</div>
        </div>
        <h3>战胜人机</h3>
        <div class="menu-grid">
          ${(app.snapshot.computerPlayers || []).map((computer) => `<div class="card">${renderComputerName(computer)}：${Number(profile.defeatedComputers?.[computer.id] || 0)}</div>`).join("")}
        </div>
        ${renderTable("profile", history, [
          ["at", "时间", (row) => formatDate(row.at)],
          ["roomId", "房间", (row) => row.roomId],
          ["playerCount", "人数", (row) => row.playerCount],
          ["rank", "排名", (row) => row.rank],
          ["plays", "出牌", (row) => row.plays],
          ["rings", "拍铃", (row) => row.rings],
          ["wonCards", "赢牌", (row) => row.wonCards],
        ])}
      </section>
    </main>
  `;
}

function renderLeaderboard() {
  const leaderboard = app.leaderboard;
  if (!leaderboard) {
    loadLeaderboard();
    return `<main class="page"><section class="panel"><h2>排行榜</h2><p class="muted">加载中...</p></section></main>`;
  }
  const computers = app.snapshot.computerPlayers || [];
  const playerRows = leaderboard.players.map((row) => ({
    ...row,
    ...Object.fromEntries(computers.map((computer) => [`defeated_${computer.id}`, row.defeatedComputers?.[computer.id] || 0])),
  }));
  const players = sortRows(playerRows, app.sorts.players);
  const matches = sortRows(leaderboard.matches, app.sorts.matches);
  const playerColumns = [
    ["username", "玩家", (row) => htmlCell(renderPlayerLabel(row))],
    ["gamesPlayed", "对局", (row) => row.gamesPlayed],
    ["wins", "胜场", (row) => row.wins],
    ["winRate", "胜率", (row) => fmtPct(row.winRate)],
    ["correctRate", "按铃正确率", (row) => fmtPct(row.correctRate)],
    ["wonCards", "赢牌", (row) => row.wonCards],
    ["averageRank", "平均排名", (row) => fmtNum(row.averageRank)],
    ...computers.map((computer) => [`defeated_${computer.id}`, htmlCell(`胜 ${renderComputerName(computer)}`), (row) => row[`defeated_${computer.id}`] || 0]),
  ];
  return `
    <main class="page">
      <section class="panel grid">
        <h2>排行榜</h2>
        ${renderTable("players", players, playerColumns)}
        <h3>对局记录</h3>
        ${renderTable("matches", matches, [
          ["at", "时间", (row) => formatDate(row.at)],
          ["roomId", "房间", (row) => row.roomId],
          ["playerCount", "人数", (row) => row.playerCount],
          ["playCount", "出牌数", (row) => row.playCount],
          ["bellCount", "拍铃数", (row) => row.bellCount],
          ["successBellCount", "成功", (row) => row.successBellCount],
        ])}
      </section>
    </main>
  `;
}

function renderCardSelect() {
  const selected = new Set(app.cardViewer.selectedLibraryIds.length ? app.cardViewer.selectedLibraryIds : app.snapshot.cardLibraries.slice(0, 1).map((library) => library.id));
  return `
    <main class="page">
      <section class="panel">
        <h2>卡组选择</h2>
        <form id="card-view-form" class="grid">
          <div class="library-list">
            ${app.snapshot.cardLibraries.map((library) => `
              <label class="library-row">
                <span>
                  <strong>${escapeHtml(library.name)}</strong>
                  <span class="pill">${library.cardCount} 张 / ${library.pmvCount} PMV</span>
                  <span class="muted">整理者：${escapeHtml(library.curator || "未填写")}</span>
                </span>
                <input name="libraryIds" value="${escapeAttr(library.id)}" type="checkbox" ${selected.has(library.id) ? "checked" : ""}>
              </label>
            `).join("")}
          </div>
          <div class="actions">
            <button class="primary" type="submit">查看</button>
            <button type="button" data-action="home">返回</button>
          </div>
        </form>
      </section>
    </main>
  `;
}

function renderCardLoading() {
  return `
    <main class="page narrow">
      <section class="panel">
        <h2>查看卡牌加载</h2>
        <p class="status-line">${app.cardViewer.loaded}/${app.cardViewer.total || 0} 个资源</p>
        <div class="loading-bar"><div class="loading-fill" style="width:${app.cardViewer.total ? Math.round(app.cardViewer.loaded / app.cardViewer.total * 100) : 0}%"></div></div>
      </section>
    </main>
  `;
}

function renderCardInfo() {
  const payload = app.cardViewer.payload;
  if (!payload) {
    setRoute("card-loading", { libraryIds: app.cardViewer.selectedLibraryIds });
    return renderCardLoading();
  }
  const active = payload.libraries.find((library) => library.id === (app.cardViewer.activeLibraryId || payload.libraries[0]?.id)) || payload.libraries[0];
  if (!active) return `<main class="page"><section class="panel"><h2>卡牌信息</h2><p class="muted">没有可展示的卡组。</p></section></main>`;
  app.cardViewer.activeLibraryId = active.id;
  return `
    <main class="page">
      <section class="panel grid">
        <h2>卡牌信息</h2>
        <div class="tabs">
          ${payload.libraries.map((library) => `<button class="${library.id === active.id ? "primary" : ""}" data-action="card-tab" data-library="${escapeAttr(library.id)}">${escapeHtml(library.name)}</button>`).join("")}
        </div>
        <div class="deck-info">
          <img src="${escapeAttr(active.backUrl)}" alt="">
          <div>
            <h3>${escapeHtml(active.name)}</h3>
            <p class="status-line">整理者：${escapeHtml(active.curator || "未填写")} · ${active.cardCount} 张 · ${active.pmvCount} PMV${active.version ? ` · 版本 ${escapeHtml(active.version)}` : ""}</p>
            <p class="muted">${escapeHtml(active.description || "暂无说明")}</p>
            ${active.link ? `<p><a href="${escapeAttr(active.link)}">卡组链接</a></p>` : ""}
            <p class="muted">文件夹：${escapeHtml(active.folderName || active.id)}</p>
          </div>
        </div>
        <div class="pmv-card-list">
          ${active.pmvs.map((pmv) => `
            <article class="pmv-card">
              <h3>pmv ${pmv.pmvId} · ${escapeHtml(pmv.name)}</h3>
              <div class="pmv-shots">${pmv.shots.map((shot) => `<img src="${escapeAttr(shot.imageUrl)}" alt="${escapeAttr(`${pmv.name} ${shot.shot}`)}">`).join("")}</div>
              ${pmv.author ? `<p>作者：${escapeHtml(pmv.author)}</p>` : ""}
              ${pmv.description ? `<p>${escapeHtml(pmv.description)}</p>` : ""}
              ${pmv.link ? `<p><a href="${escapeAttr(pmv.link)}">链接</a></p>` : ""}
            </article>
          `).join("")}
        </div>
      </section>
    </main>
  `;
}

function renderPmvIndex() {
  if (!app.pmvIndex) {
    loadPmvIndex();
    return `<main class="page"><section class="panel"><h2>卡组提交指南</h2><p class="muted">加载中...</p></section></main>`;
  }
  const rows = sortRows(filterPmvIndexRows(app.pmvIndex.rows, app.pmvIndexSearch), app.sorts.pmvIndex);
  return `
    <main class="page">
      <section class="panel grid">
        <h2>卡组提交指南</h2>
        <div class="guide-grid">
          <section>
            <h3>卡组由什么组成</h3>
            <p>一个卡组是一套 PMV 截图、一张背面图和一个 manifest.json。截图数量不限；背面图使用 <strong>720 × 1087</strong> 分辨率，文件名固定为 <code>back.png</code>。</p>
            <p><code>manifest.json</code> 保存卡组元数据和每个 PMV 的信息。元数据可以填写卡组名字、整理者、说明、版本、链接；没有内容时留空即可。</p>
          </section>
          <section>
            <h3>文件夹结构</h3>
            <pre><code>卡组文件夹
|_ manifest.json
|_ back.png
|_ cards
   |_ 100a.png
   |_ 100b.png
   |_ 101a.png
   |_ 101b.png
   |_ 101c.png</code></pre>
            <p>截图文件名由 <code>pmv_id + 字母</code> 组成。例子里 id 为 100 的 PMV 有 2 张截图，id 为 101 的 PMV 有 3 张截图。</p>
          </section>
          <section>
            <h3>manifest.json 格式</h3>
            <pre><code>{
  "name": "卡组名字",
  "curator": "整理者",
  "description": "卡组说明",
  "version": null,
  "link": null,
  "pmvs": [
    {
      "pmv_id": 100,
      "name": "PMV名字1",
      "author": "作者1",
      "description": "链接（需要加双引号），为空请填写null（null无双引号）",
      "link": "链接，为空请填写null"
    },
    {
      "pmv_id": 101,
      "name": "PMV名字2",
      "author": "作者2",
      "description": null,
      "link": null
    },
    ...继续加入其他PMV
  ]
}</code></pre>
          </section>
          <section>
            <h3>PMV id 怎么选？</h3>
            <p>请先用下方查询功能确认你的卡组里是否有已经出现过的 PMV。如果有，请沿用对应 PMV id；如果没有，可以选择任意尚未出现过的 id。</p>
            <p>完成后把整个卡组文件夹压缩，发给页面下方的管理员。审核通过后，管理员会手动添加卡包。</p>
          </section>
        </div>
        <div class="pmv-search-row">
          <label>搜索 PMV id / 名字 / 作者 / 卡组
            <input data-pmv-search name="pmvSearch" value="${escapeAttr(app.pmvIndexSearch)}" autocomplete="off" placeholder="输入pmv名称">
          </label>
        </div>
        ${renderTable("pmvIndex", rows, [
          ["pmvId", "id", (row) => row.pmvId],
          ["name", "PMV 名", (row) => row.name],
          ["author", "作者", (row) => row.author || ""],
          ["libraryName", "卡包", (row) => row.libraryName],
        ])}
      </section>
    </main>
  `;
}

function filterPmvIndexRows(rows, query) {
  const normalized = String(query || "").trim().toLocaleLowerCase();
  if (!normalized) return rows.slice();
  return rows.filter((row) => [
    row.pmvId,
    row.name,
    row.author,
    row.libraryName,
  ].some((value) => String(value || "").toLocaleLowerCase().includes(normalized)));
}

function renderTable(sortName, rows, columns) {
  return `
    <div class="table-wrap">
      <table>
        <thead><tr>${columns.map(([key, label]) => `<th><button data-action="sort" data-sort="${sortName}" data-key="${key}">${renderTableHeader(label)}</button></th>`).join("")}</tr></thead>
        <tbody>
          ${rows.length ? rows.map((row) => `<tr>${columns.map(([, , cell]) => `<td>${renderTableCell(cell(row))}</td>`).join("")}</tr>`).join("") : `<tr><td colspan="${columns.length}">暂无数据</td></tr>`}
        </tbody>
      </table>
    </div>
  `;
}

function htmlCell(value) {
  return { __html: String(value ?? "") };
}

function renderTableHeader(value) {
  if (value && typeof value === "object" && Object.prototype.hasOwnProperty.call(value, "__html")) return value.__html;
  return escapeHtml(value ?? "");
}

function renderTableCell(value) {
  if (value && typeof value === "object" && Object.prototype.hasOwnProperty.call(value, "__html")) return value.__html;
  const text = String(value ?? "");
  return escapeHtml(text);
}

async function loadProfile() {
  if (!app.clientId || app.profileLoading) return;
  app.profileLoading = true;
  try {
    const result = await getJson(`/api/profile/${encodeURIComponent(app.clientId)}${authQuery()}`);
    app.profile = result.profile;
    if (app.route.name === "profile") render();
  } catch (error) {
    showToast(humanizeError(error.message));
  } finally {
    app.profileLoading = false;
  }
}

async function loadLeaderboard() {
  if (app.leaderboardLoading) return;
  app.leaderboardLoading = true;
  try {
    const result = await getJson("/api/leaderboard");
    app.leaderboard = result;
    if (app.route.name === "leaderboard") render();
  } catch (error) {
    showToast(humanizeError(error.message));
  } finally {
    app.leaderboardLoading = false;
  }
}

async function loadPmvIndex() {
  if (app.pmvIndexLoading) return;
  app.pmvIndexLoading = true;
  try {
    app.pmvIndex = await getJson("/api/pmv-index");
    if (app.route.name === "pmv-index") render();
  } catch (error) {
    showToast(humanizeError(error.message));
  } finally {
    app.pmvIndexLoading = false;
  }
}

async function beginCardViewerPreload() {
  if (app.cardViewer.loading || app.cardViewer.payload) {
    if (app.cardViewer.payload && app.route.name === "card-loading") setRoute("card-info");
    return;
  }
  const libraryIds = app.route.libraryIds || app.cardViewer.selectedLibraryIds;
  app.cardViewer.selectedLibraryIds = libraryIds;
  app.cardViewer.loading = true;
  try {
    const query = new URLSearchParams();
    libraryIds.forEach((id) => query.append("libraryIds", id));
    const payload = await getJson(`/api/card-viewer?${query.toString()}`);
    const cacheKey = `cof.cardViewer.${payload.key}`;
    app.cardViewer.total = payload.assets.length;
    app.cardViewer.loaded = 0;
    if (localStorage.getItem(cacheKey) !== "1") {
      for (const asset of payload.assets) {
        await preloadAsset(asset);
        app.cardViewer.loaded += 1;
        render();
      }
      localStorage.setItem(cacheKey, "1");
    } else {
      app.cardViewer.loaded = app.cardViewer.total;
    }
    app.cardViewer.payload = payload;
    app.cardViewer.key = payload.key;
    app.cardViewer.loading = false;
    setRoute("card-info");
  } catch (error) {
    app.cardViewer.loading = false;
    app.message = humanizeError(error.message);
    setRoute("card-select");
  }
}

function renderLibraryPicker(libs, selected = null, copies = {}) {
  const selectedSet = new Set(selected || libs.slice(0, 1).map((lib) => lib.id));
  const total = libs.filter((lib) => selectedSet.has(lib.id)).reduce((sum, lib) => sum + lib.cardCount * copyValueForLibrary(lib, copies), 0);
  return `
    <div class="panel">
      <h3>卡牌库 <span class="pill" data-card-total>当前 ${total} 张</span></h3>
      <div class="library-list">
        ${libs.map((lib) => `
          <label class="library-row">
            <span>${escapeHtml(lib.name)} <span class="pill">${lib.cardCount} 张 / ${lib.pmvCount} PMV</span></span>
            <span class="library-controls">
              <input name="libraryIds" value="${escapeAttr(lib.id)}" type="checkbox" ${selectedSet.has(lib.id) ? "checked" : ""}>
              <input class="copy-input" name="libraryCopies.${escapeAttr(lib.id)}" type="number" min="1" max="${libraryCopyLimit(lib)}" value="${copyValueForLibrary(lib, copies)}" aria-label="复制份数">
            </span>
          </label>
        `).join("")}
      </div>
    </div>
  `;
}

function copyValueForLibrary(lib, copies = {}) {
  return Math.max(1, Math.min(libraryCopyLimit(lib), Number.parseInt(copies?.[lib.id], 10) || 1));
}

function libraryCopyLimit(lib) {
  const cardCount = Math.max(1, Number.parseInt(lib?.cardCount, 10) || 1);
  return Math.max(1, Math.floor(120 / cardCount));
}

function renderStartVoteSettings(settings = {}) {
  const mode = settings.startVoteThresholdMode === "manual" ? "manual" : "auto";
  const threshold = settings.startVoteThreshold || "";
  return `
    <div class="panel form-grid">
      <label>投票开始阈值
        <select name="startVoteThresholdMode">
          <option value="auto" ${mode === "auto" ? "selected" : ""}>自动（当前人数 - 2）</option>
          <option value="manual" ${mode === "manual" ? "selected" : ""}>手动</option>
        </select>
      </label>
      <label>手动阈值
        <input name="startVoteThreshold" type="number" min="1" max="8" value="${escapeAttr(threshold)}">
      </label>
    </div>
  `;
}

function renderComputerPicker(computers) {
  if (!computers.length) return "";
  return `
    <div class="panel">
      <h3>初始人机玩家</h3>
      <div class="computer-list">
        ${computers.map((computer) => `
          <label class="computer-row ${isGodComputer(computer) ? "god-computer" : ""}">
            <span>
              <strong>${renderComputerName(computer)}</strong>
              <span class="muted">${escapeHtml(computer.description || "")}</span>
              ${renderComputerStats(computer)}
            </span>
            <input name="computerIds" value="${escapeAttr(computer.id)}" type="checkbox">
          </label>
        `).join("")}
      </div>
    </div>
  `;
}

function renderAdvancedSettings(settings = {}) {
  const value = {
    allowEmptyBell: false,
    randomBacks: false,
    conflictResolution: true,
    disconnectProtection: true,
    ...settings,
  };
  return `
    <div class="panel form-grid">
      ${toggle("allowEmptyBell", "卡牌耗尽依然可拍铃", value.allowEmptyBell)}
      ${toggle("randomBacks", "随机卡背颜色", value.randomBacks)}
      ${toggle("conflictResolution", "解决拍铃与出牌冲突", value.conflictResolution)}
      ${toggle("disconnectProtection", "断线保护", value.disconnectProtection)}
    </div>
  `;
}

function toggle(name, label, checked) {
  return `<label class="toggle-row"><span>${label}</span><input name="${name}" type="checkbox" ${checked ? "checked" : ""}></label>`;
}

async function handleClick(event) {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const action = button.dataset.action;
  if (["home", "create", "join", "rooms", "rules", "profile", "leaderboard", "card-select", "pmv-index"].includes(action)) {
    setRoute(action === "home" ? "home" : action);
    return;
  }
  if (action === "waiting") return setRoute("waiting");
  if (action === "settings") return setRoute("settings");
  if (action === "transfer") return setRoute("settings", { transfer: true });
  if (action === "go-current-room") return routeToCurrent();
  if (action === "leave-room") return leaveRoom();
  if (action === "disband-room") return disbandRoom();
  if (action === "logout") return logout();
  if (action === "join-public-room") return joinRoom(button.dataset.room);
  if (action === "start-game") return startGame();
  if (action === "play-card") return playCard();
  if (action === "ring-bell") return ringBell();
  if (action === "continue-game") return continueGame();
  if (action === "start-vote") return startVote();
  if (action === "cancel-start-vote") return cancelStartVote();
  if (action === "add-computer") return addComputer(button.dataset.computer);
  if (action === "remove-computer") return removeComputer(button.dataset.computer);
  if (action === "dismiss-god-reward") return dismissGodReward(button.dataset.game);
  if (action === "card-tab") {
    app.cardViewer.activeLibraryId = button.dataset.library;
    render();
    return;
  }
  if (action === "sort") return sortBy(button.dataset.sort, button.dataset.key);
}

function handleChange(event) {
  if (event.target.name !== "libraryIds" && !event.target.name?.startsWith("libraryCopies.") && !isRoomSettingsControl(event.target)) return;
  const form = event.target.closest("form");
  if (!form) return;
  if (form.id === "room-settings-form") {
    updateRoomSettingsDraft(form);
    scheduleRoomSettingsAutosave();
  }
  updateLibraryTotal(form);
}

function updateLibraryTotal(form) {
  const data = new FormData(form);
  const selected = new Set(data.getAll("libraryIds"));
  const total = app.snapshot.cardLibraries
    .filter((library) => selected.has(library.id))
    .reduce((sum, library) => sum + library.cardCount * (Number.parseInt(data.get(`libraryCopies.${library.id}`), 10) || 1), 0);
  const badge = form.querySelector("[data-card-total]");
  if (badge) badge.textContent = `当前 ${total} 张`;
}

function handleInput(event) {
  if (isChatInput(event.target)) {
    app.chat.draft = event.target.value || "";
    return;
  }
  if (isPmvSearchInput(event.target)) {
    app.pmvIndexSearch = event.target.value || "";
    render();
    return;
  }
  if (isRoomSettingsControl(event.target)) {
    const form = event.target.closest("form");
    if (!form) return;
    updateRoomSettingsDraft(form);
    updateLibraryTotal(form);
    scheduleRoomSettingsAutosave();
  }
}

function handleFocusIn(event) {
  if (isChatInput(event.target)) {
    app.chat.focused = true;
    app.chat.draft = event.target.value || app.chat.draft || "";
    return;
  }
  if (isPmvSearchInput(event.target)) app.pmvSearchFocused = true;
}

function handleFocusOut(event) {
  if (isChatInput(event.target)) {
    if (app.rendering) return;
    app.chat.focused = false;
    if (event.target.value) app.chat.draft = event.target.value;
    return;
  }
  if (isPmvSearchInput(event.target) && !app.rendering) app.pmvSearchFocused = false;
}

function handleCompositionStart(event) {
  if (!isChatInput(event.target)) return;
  app.chat.composing = true;
  app.chat.focused = true;
}

function handleCompositionEnd(event) {
  if (!isChatInput(event.target)) return;
  app.chat.composing = false;
  app.chat.draft = event.target.value || "";
  if (app.chat.pendingRender) {
    app.chat.pendingRender = false;
    render();
  }
}

async function handleSubmit(event) {
  event.preventDefault();
  const form = event.target;
  if (form.id === "login-form") {
    await submitLogin(form);
    return;
  }
  if (form.id === "register-form") {
    await submitRegister(form);
    return;
  }
  if (form.id === "create-room-form") {
    const data = new FormData(form);
    const room = await postJson("/api/rooms", authPayload({
      settings: settingsFromForm(form, true),
      computerIds: data.getAll("computerIds"),
    }));
    await refresh();
    setRoute("waiting", { roomId: room.room.id });
  }
  if (form.id === "join-room-form") {
    const roomId = new FormData(form).get("roomId").trim();
    await joinRoom(roomId);
  }
  if (form.id === "room-settings-form") {
    const room = currentRoom();
    if (app.roomSettingsAutosaveTimer) {
      window.clearTimeout(app.roomSettingsAutosaveTimer);
      app.roomSettingsAutosaveTimer = null;
    }
    updateRoomSettingsDraft(form);
    await postJson(`/api/rooms/${encodeURIComponent(room.id)}/settings`, authPayload({ settings: roomSettingsPayload(app.roomSettingsDraft) }));
    app.roomSettingsDraft = null;
    await refresh();
    setRoute("waiting", { roomId: room.id });
  }
  if (form.id === "transfer-host-form") {
    const room = currentRoom();
    const newHostId = new FormData(form).get("newHostId");
    await postJson(`/api/rooms/${encodeURIComponent(room.id)}/transfer-host`, authPayload({ newHostId }));
    await refresh();
    setRoute("waiting", { roomId: room.id });
  }
  if (form.id === "chat-form") {
    await sendChat(form);
  }
  if (form.id === "card-view-form") {
    const libraryIds = new FormData(form).getAll("libraryIds");
    if (!libraryIds.length) {
      showToast("请至少选择一个卡组。");
      return;
    }
    app.cardViewer = { loading: false, payload: null, key: "", selectedLibraryIds: libraryIds, loaded: 0, total: 0 };
    setRoute("card-loading", { libraryIds });
  }
}

async function submitLogin(form) {
  const data = new FormData(form);
  try {
    const result = await postJson("/api/login", {
      username: data.get("username").trim(),
      password: data.get("password"),
    });
    await finishAuth(result);
  } catch (error) {
    app.message = humanizeError(error.message);
    render();
  }
}

async function submitRegister(form) {
  const data = new FormData(form);
  const password = data.get("password");
  if (password !== data.get("confirmPassword")) {
    app.message = "两次输入的密码不一致。";
    render();
    return;
  }
  try {
    const result = await postJson("/api/register", {
      username: data.get("username").trim(),
      password,
    });
    await finishAuth(result);
  } catch (error) {
    app.message = humanizeError(error.message);
    render();
  }
}

async function finishAuth(result) {
  app.token = result.token;
  localStorage.setItem("cof.token", app.token);
  app.clientId = result.player.clientId;
  app.profile = null;
  app.leaderboard = null;
  const passwordReset = result.passwordReset;
  await refresh();
  connectEvents();
  setRoute("home");
  if (passwordReset) showToast("管理员重置已生效，刚刚输入的密码已成为新密码。");
}

function authPayload(extra = {}) {
  return { token: app.token, clientId: app.clientId, ...extra };
}

function authQuery(extra = {}) {
  const params = new URLSearchParams({ token: app.token, ...extra });
  return `?${params.toString()}`;
}

function settingsFromForm(form, includeFixed) {
  const data = new FormData(form);
  const libraryIds = data.getAll("libraryIds");
  const settings = {
    libraryIds,
    libraryCopies: Object.fromEntries(libraryIds.map((id) => [id, Number(data.get(`libraryCopies.${id}`)) || 1])),
    startVoteThresholdMode: data.get("startVoteThresholdMode") || "auto",
    startVoteThreshold: Number(data.get("startVoteThreshold")) || null,
    allowEmptyBell: data.has("allowEmptyBell"),
    randomBacks: data.has("randomBacks"),
    conflictResolution: data.has("conflictResolution"),
    disconnectProtection: data.has("disconnectProtection"),
  };
  if (includeFixed) {
    settings.minPlayers = Number(data.get("minPlayers"));
    settings.maxPlayers = Number(data.get("maxPlayers"));
    settings.isPublic = data.has("isPublic");
  }
  return settings;
}

function updateRoomSettingsDraft(form) {
  const room = currentRoom();
  if (!room) return null;
  app.roomSettingsDraft = {
    roomId: room.id,
    ...settingsFromForm(form, false),
  };
  return app.roomSettingsDraft;
}

function roomSettingsPayload(settings) {
  const { roomId, ...payload } = settings || {};
  return payload;
}

function scheduleRoomSettingsAutosave() {
  if (app.roomSettingsAutosaveTimer) window.clearTimeout(app.roomSettingsAutosaveTimer);
  app.roomSettingsAutosaveTimer = window.setTimeout(() => {
    app.roomSettingsAutosaveTimer = null;
    saveRoomSettingsDraft();
  }, 180);
}

async function saveRoomSettingsDraft() {
  const room = currentRoom();
  const draft = app.roomSettingsDraft;
  if (!room || !draft || draft.roomId !== room.id) return;
  const seq = ++app.roomSettingsAutosaveSeq;
  app.roomSettingsSaving = true;
  try {
    await postJson(`/api/rooms/${encodeURIComponent(room.id)}/settings`, authPayload({ settings: roomSettingsPayload(draft) }));
    await refresh();
    if (seq === app.roomSettingsAutosaveSeq && app.route.name === "settings") {
      const latest = currentRoom();
      if (latest) app.roomSettingsDraft = { roomId: latest.id, ...latest.settings };
      app.roomSettingsSaving = false;
      render();
    }
  } catch (error) {
    showToast(humanizeError(error.message));
  } finally {
    if (seq === app.roomSettingsAutosaveSeq) app.roomSettingsSaving = false;
  }
}

async function joinRoom(roomId) {
  try {
    const result = await postJson(`/api/rooms/${encodeURIComponent(roomId)}/join`, authPayload());
    await refresh();
    if (result.game?.status === "playing" || result.game?.status === "finished") setRoute("game");
    else if (result.room.status === "loading") setRoute("loading");
    else setRoute("waiting", { roomId });
  } catch (error) {
    app.message = error.message;
    render();
  }
}

async function startGame() {
  const room = currentRoom();
  try {
    await postJson(`/api/rooms/${encodeURIComponent(room.id)}/start`, authPayload());
    await refresh();
    setRoute("loading");
  } catch (error) {
    app.message = error.message;
    render();
  }
}

async function startVote() {
  const room = currentRoom();
  if (!room) return;
  await safeRoomAction(`/api/rooms/${encodeURIComponent(room.id)}/start-vote`);
}

async function cancelStartVote() {
  const room = currentRoom();
  if (!room) return;
  await safeRoomAction(`/api/rooms/${encodeURIComponent(room.id)}/cancel-start-vote`);
}

async function addComputer(computerId) {
  const room = currentRoom();
  if (!room || !computerId) return;
  await safeRoomAction(`/api/rooms/${encodeURIComponent(room.id)}/add-computer`, { computerId });
}

async function removeComputer(computerId) {
  const room = currentRoom();
  if (!room || !computerId) return;
  await safeRoomAction(`/api/rooms/${encodeURIComponent(room.id)}/remove-computer`, { computerId });
}

function dismissGodReward(gameId) {
  if (app.clientId && gameId) localStorage.setItem(`cof.godReward.${app.clientId}.${gameId}`, "1");
  document.querySelector(".god-reward-modal")?.remove();
}

async function sendChat(form) {
  const room = currentRoom();
  if (!room) return;
  const input = form.querySelector("input[name='message']");
  const message = input?.value || "";
  if (!message.trim()) return;
  await safeRoomAction(`/api/rooms/${encodeURIComponent(room.id)}/chat`, { message });
  app.chat.draft = "";
  if (input) input.value = "";
}

async function safeRoomAction(path, extra = {}) {
  try {
    await postJson(path, authPayload(extra));
    await refresh();
    render();
  } catch (error) {
    app.message = error.message;
    render();
  }
}

async function beginPreload() {
  const room = currentRoom();
  const game = app.snapshot.currentGame;
  if (!room || !game || (app.loading.gameId === game.id && (app.loading.done || app.loading.running))) return;
  if (app.loading.gameId !== game.id) {
    app.loading = { gameId: game.id, loaded: 0, total: 0, done: false, running: false, cached: false, manifestKey: "", lastReportAt: 0 };
  }
  app.loading.running = true;
  try {
    const manifest = await getJson(`/api/rooms/${encodeURIComponent(room.id)}/assets${authQuery()}`);
    const urls = manifest.assets || [];
    const cacheKey = `cof.assets.${manifest.key}`;
    app.loading.manifestKey = manifest.key;
    app.loading.cached = localStorage.getItem(cacheKey) === "1";
    app.loading.total = urls.length;
    app.loading.loaded = 0;
    await reportLoadingProgress(room, false, true);
    render();
    for (let index = 0; index < urls.length; index += 1) {
      const url = urls[index];
      await preloadAsset(url).catch(() => undefined);
      app.loading.loaded += 1;
      await reportLoadingProgress(room, false, index % 5 === 0);
      render();
    }
    localStorage.setItem(cacheKey, "1");
    app.loading.done = true;
    await reportLoadingProgress(room, true, true);
    await refresh();
    if (app.snapshot.currentGame?.status === "playing") setRoute("game");
  } catch (error) {
    showToast(humanizeError(error.message));
  } finally {
    app.loading.running = false;
  }
}

async function reportLoadingProgress(room, done = false, force = false) {
  const now = Date.now();
  if (!force && !done && now - app.loading.lastReportAt < 180) return;
  app.loading.lastReportAt = now;
  await postJson(`/api/rooms/${encodeURIComponent(room.id)}/loading-progress`, authPayload({
    loaded: app.loading.loaded,
    total: app.loading.total,
    cached: app.loading.cached,
    manifestKey: app.loading.manifestKey,
    done,
  }));
}

function preloadAsset(url) {
  if (/\.(?:mp3|wav)$/i.test(url)) {
    return fetch(url, { cache: "force-cache" }).then((response) => {
      if (!response.ok) throw new Error("Audio preload failed.");
      return response.blob();
    });
  }
  return preloadImage(url);
}

function preloadImage(url) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = resolve;
    img.onerror = reject;
    img.src = url;
  });
}

async function playCard() {
  const game = app.snapshot.currentGame;
  if (!game) return;
  await safeAction(`/api/games/${encodeURIComponent(game.id)}/play-card`);
}

const bellAudio = new Audio('/ding.wav');
bellAudio.preload = 'auto';
const sendCardAudio = new Audio('/sendcard.mp3');
sendCardAudio.preload = 'auto';

function handleAudioEvent(data) {
  if (!app.snapshot.currentGame || app.snapshot.currentGame.roomId !== data.roomId) return;
  if (data.type === 'ring-bell') {
    bellAudio.currentTime = 0;
    bellAudio.play().catch(() => {});
  } else if (data.type === 'play-card') {
    sendCardAudio.currentTime = 0;
    sendCardAudio.play().catch(() => {});
  }
}

async function ringBell() {
  const game = app.snapshot.currentGame;
  if (!game) return;
  await safeAction(`/api/games/${encodeURIComponent(game.id)}/ring-bell`);
}

async function continueGame() {
  const game = app.snapshot.currentGame;
  if (!game) return;
  const replayStartedAt = ensureResultReplay(game);
  if (!canContinueAfterResultReplay(game, replayStartedAt)) {
    render();
    return;
  }
  await safeAction(`/api/games/${encodeURIComponent(game.id)}/continue`);
  await refresh();
  routeToCurrent();
}

async function leaveRoom() {
  const room = app.snapshot.currentRoom;
  if (!room) return;
  try {
    await postJson(`/api/rooms/${encodeURIComponent(room.id)}/leave`, authPayload());
    await refresh();
    setRoute("home");
  } catch (error) {
    showToast(humanizeError(error.message));
  }
}

async function disbandRoom() {
  const room = app.snapshot.currentRoom;
  if (!room) return;
  if (!window.confirm("确定要解散这个房间吗？")) return;
  try {
    await postJson(`/api/rooms/${encodeURIComponent(room.id)}/disband`, authPayload());
    await refresh();
    setRoute("home");
  } catch (error) {
    showToast(humanizeError(error.message));
  }
}

async function safeAction(path) {
  try {
    await postJson(path, authPayload());
    await refresh();
    render();
  } catch (error) {
    showToast(humanizeError(error.message));
  }
}

function showToast(message) {
  const id = `${Date.now()}_${Math.random()}`;
  app.toast = { id, message };
  render();
  window.setTimeout(() => {
    if (app.toast?.id === id) {
      app.toast = null;
      render();
    }
  }, 1800);
}

function scheduleCountdownRender() {
  if (app.countdownTimer) {
    window.clearTimeout(app.countdownTimer);
    app.countdownTimer = null;
  }
  const game = app.snapshot?.currentGame;
  const room = app.snapshot?.currentRoom;
  const now = Date.now();
  const animationUntil = game?.lastAnimation ? game.lastAnimation.startedAt + game.lastAnimation.durationMs : 0;
  if (game?.status === "finished" && game.resultInfo?.counts?.length) {
    const replayStartedAt = ensureResultReplay(game, now);
    if (!canContinueAfterResultReplay(game, replayStartedAt, now)) {
      app.countdownTimer = window.setTimeout(() => {
        app.countdownTimer = null;
        render();
      }, 50);
      return;
    }
  }
  if (game?.status === "playing" && game.lockedUntil > Date.now()) {
    const delay = animationUntil > now ? animationUntil - now + 80 : 1000;
    app.countdownTimer = window.setTimeout(() => {
      app.countdownTimer = null;
      render();
    }, delay);
    return;
  }
  const current = game?.players?.[game.turnIndex];
  const turnSecondsLeft = game?.turnDeadlineAt ? Math.max(0, Math.ceil((game.turnDeadlineAt - now) / 1000)) : 0;
  if (game?.status === "playing" && current?.connected !== false && turnSecondsLeft > 0 && turnSecondsLeft <= MAX_VISIBLE_TURN_COUNTDOWN_SECONDS) {
    app.countdownTimer = window.setTimeout(() => {
      app.countdownTimer = null;
      render();
    }, 1000);
    return;
  }
  if (game?.status === "finished" && game.continueReturnAt && now < game.continueReturnAt) {
    app.countdownTimer = window.setTimeout(() => {
      app.countdownTimer = null;
      render();
    }, 1000);
    return;
  }
  if (room?.status === "waiting" && room.startAt && now < room.startAt) {
    app.countdownTimer = window.setTimeout(() => {
      app.countdownTimer = null;
      render();
    }, 1000);
  }
}

function humanizeError(message) {
  if (/resolving an action/i.test(message)) return "动作结算中，请稍等。";
  if (/turn delay/i.test(message)) return "出牌间隔还没到。";
  if (/not this player/i.test(message)) return "还没轮到你出牌。";
  return message || "操作失败。";
}

function routeToCurrent() {
  const room = app.snapshot.currentRoom;
  const game = app.snapshot.currentGame;
  if (!room) return setRoute("home");
  if (room.status === "loading") return setRoute("loading");
  if (room.status === "playing" || game?.status === "finished") return setRoute("game");
  return setRoute("waiting", { roomId: room.id });
}

function sortBy(sortName, key) {
  const current = app.sorts[sortName];
  app.sorts[sortName] = {
    key,
    dir: current.key === key && current.dir === "desc" ? "asc" : "desc",
  };
  render();
}

function sortRows(rows, sort) {
  return rows.slice().sort((a, b) => {
    const av = a[sort.key];
    const bv = b[sort.key];
    const aEmpty = isEmptySortValue(av);
    const bEmpty = isEmptySortValue(bv);
    if (aEmpty && bEmpty) return 0;
    if (aEmpty) return 1;
    if (bEmpty) return -1;
    const result = typeof av === "string" ? String(av).localeCompare(String(bv), "zh-Hans-CN") : Number(av || 0) - Number(bv || 0);
    return sort.dir === "asc" ? result : -result;
  });
}

function isEmptySortValue(value) {
  if (value === null || value === undefined) return true;
  if (typeof value === "number" && Number.isNaN(value)) return true;
  return false;
}

function rotatePlayersForSelf(players) {
  const index = players.findIndex((player) => player.clientId === app.clientId);
  if (index < 0) return players;
  return players.slice(index).concat(players.slice(0, index));
}

function currentRoom() {
  if (app.route.roomId) {
    return app.snapshot.rooms.find((room) => room.id === app.route.roomId)
      || (app.snapshot.currentRoom?.id === app.route.roomId ? app.snapshot.currentRoom : null);
  }
  if (app.snapshot.currentRoom) return app.snapshot.currentRoom;
  return null;
}

function missingRoom() {
  return `<main class="page"><section class="panel"><h2>没有当前房间</h2><button data-action="home">返回主页</button></section></main>`;
}

async function logout() {
  const token = app.token;
  try {
    if (token) await postJson("/api/logout", { token });
  } catch {
    // Logging out should always clear the local session, even if the server is gone.
  }
  clearAuth("已退出登录。");
  await refresh();
  render();
}

function clearAuth(message = "") {
  if (app.eventSource) {
    app.eventSource.close();
    app.eventSource = null;
  }
  if (app.stateRefreshTimer) {
    window.clearTimeout(app.stateRefreshTimer);
    app.stateRefreshTimer = null;
  }
  localStorage.removeItem("cof.token");
  app.token = "";
  app.clientId = "";
  app.profile = null;
  app.leaderboard = null;
  app.route = { name: "auth" };
  app.message = message;
}

async function getJson(path) {
  const response = await fetch(path);
  const data = await response.json();
  if (response.status === 401) clearAuth(data.error || "请重新登录。");
  if (!response.ok) throw new Error(data.error || "请求失败");
  return data;
}

async function postJson(path, body) {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await response.json();
  if (response.status === 401) clearAuth(data.error || "请重新登录。");
  if (!response.ok) throw new Error(data.error || "请求失败");
  return data;
}

function statusText(status) {
  return {
    waiting: "等待中",
    loading: "加载中",
    playing: "游戏中",
    finished: "已结束",
  }[status] || status;
}

function fmtPct(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return "-";
  const percent = Number(value || 0) * 100;
  const rounded = Math.round(percent * 100) / 100;
  return `${String(rounded).replace(/\.?0+$/, "")}%`;
}

function fmtNum(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return "-";
  return Number(value || 0).toFixed(2);
}

function formatDate(value) {
  if (!value) return "";
  return new Date(value).toLocaleString();
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeAttr(value) {
  return escapeHtml(value);
}
