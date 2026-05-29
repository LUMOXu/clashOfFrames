"use strict";

const app = {
  token: localStorage.getItem("cof.token") || "",
  clientId: "",
  snapshot: null,
  route: { name: "home" },
  message: "",
  toast: null,
  loading: { gameId: null, loaded: 0, total: 0, done: false, running: false },
  sorts: {
    profile: { key: "at", dir: "desc" },
    players: { key: "wins", dir: "desc" },
    matches: { key: "playCount", dir: "desc" },
  },
  eventSource: null,
  countdownTimer: null,
};

document.addEventListener("DOMContentLoaded", init);
document.addEventListener("click", handleClick);
document.addEventListener("submit", handleSubmit);
document.addEventListener("change", handleChange);

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
  const query = app.token ? `?token=${encodeURIComponent(app.token)}` : "";
  app.snapshot = await getJson(`/api/bootstrap${query}`);
  if (app.snapshot.player) {
    app.clientId = app.snapshot.player.clientId;
  } else {
    app.clientId = "";
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
  app.eventSource.addEventListener("state", async () => {
    await refresh();
    autoRouteFromState();
    render();
  });
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
    if (room.status === "waiting" && (!game || game.status !== "playing")) app.route = { name: "waiting", roomId: room.id };
  }
}

function setRoute(name, params = {}) {
  app.route = { name, ...params };
  app.message = "";
  render();
}

function render() {
  const root = document.querySelector("#app");
  if (!app.snapshot) {
    root.innerHTML = `<main class="page"><div class="panel">加载中...</div></main>`;
    return;
  }

  const content = app.route.name === "auth" || !app.snapshot.player ? renderAuth() : renderShell();
  root.innerHTML = content;
  if (app.route.name === "loading") beginPreload();
  scheduleCountdownRender();
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
          <span class="pill">${escapeHtml(app.snapshot.player?.username || "未命名")}</span>
          <button data-action="home">主页</button>
          <button data-action="profile">个人信息</button>
          <button data-action="logout">退出登录</button>
        </div>
      </header>
      ${app.message ? `<main class="page"><div class="message">${escapeHtml(app.message)}</div></main>` : ""}
      ${app.toast ? `<div class="toast">${escapeHtml(app.toast.message)}</div>` : ""}
      ${renderRoute()}
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
        <div class="menu-grid">
          <button class="primary" data-action="create">创建房间</button>
          <button data-action="join">加入房间</button>
          <button data-action="rooms">查看房间</button>
          <button data-action="rules">游戏规则</button>
          <button data-action="profile">个人信息</button>
          <button data-action="leaderboard">排行榜</button>
          ${room ? `<button data-action="go-current-room">返回当前房间</button><button class="danger" data-action="leave-room">退出房间</button>` : ""}
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
              <input name="minPlayers" type="number" min="3" max="8" value="3">
            </label>
            <label>房间最大人数
              <input name="maxPlayers" type="number" min="3" max="8" value="8">
            </label>
          </div>
          <label class="toggle-row">
            <span>公开房间</span>
            <input name="isPublic" type="checkbox" checked>
          </label>
          ${renderLibraryPicker(libs)}
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
  const names = room.players.map((player) => player.username).join("、") || "无";
  const full = room.players.length >= room.settings.maxPlayers && room.status === "waiting";
  return `
    <div class="card room-row">
      <div>
        <strong>#${escapeHtml(room.id)}</strong>
        <span class="pill">${statusText(room.status)}</span>
        <p class="muted">${escapeHtml(names)}</p>
      </div>
      <button data-action="join-public-room" data-room="${escapeAttr(room.id)}" ${full ? "disabled" : ""}>加入</button>
    </div>
  `;
}

function renderRules() {
  return `
    <main class="page">
      <section class="panel">
        <h2>游戏规则</h2>
        <div class="grid">
          <p>所有玩家获得等量背面朝上的卡牌，轮流翻开牌放到自己的展示区最上方。</p>
          <p>任意未淘汰玩家发现桌面顶部展示牌中有两张来自同一 PMV 时，可以立刻按铃。</p>
          <p>按铃正确时，按铃者获得所有玩家已出展示牌并洗入自己牌堆底部；按铃错误时，需要按顺时针顺序给其他玩家各一张牌。</p>
          <p>默认牌堆耗尽即淘汰，最后未被淘汰的玩家获胜。</p>
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
              <span>${escapeHtml(player.username)} ${player.clientId === room.hostId ? `<span class="pill ok">房主</span>` : ""}</span>
              <span class="pill ${player.connected ? "ok" : "warn"}">${player.connected ? "在线" : "掉线"}</span>
            </div>
          `).join("")}
        </div>
        <div class="actions">
          ${isHost ? `<button data-action="settings">房间设置</button><button data-action="transfer">转让房主</button><button class="primary" data-action="start-game" ${room.players.length < room.settings.minPlayers ? "disabled" : ""}>开始游戏</button>` : ""}
          <button data-action="home">回主菜单</button>
        </div>
      </section>
    </main>
  `;
}

function renderRoomSettings() {
  const room = currentRoom();
  if (!room) return missingRoom();
  if (app.route.transfer) return renderTransferHost(room);
  return `
    <main class="page">
      <section class="panel">
        <h2>房间设置</h2>
        <form id="room-settings-form" class="grid">
          ${renderLibraryPicker(app.snapshot.cardLibraries, room.settings.libraryIds)}
          ${renderAdvancedSettings(room.settings)}
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

function renderLoading() {
  const room = currentRoom();
  const game = app.snapshot.currentGame;
  if (!room || !game) return missingRoom();
  const percent = app.loading.total ? Math.round(app.loading.loaded / app.loading.total * 100) : 0;
  const cacheText = app.loading.cached ? "，浏览器缓存可复用" : "";
  return `
    <main class="page narrow">
      <section class="panel">
        <h2>游戏加载</h2>
        <div class="loading-bar"><div class="loading-fill" style="width:${percent}%"></div></div>
        <p class="status-line">${app.loading.loaded}/${app.loading.total || 0} 个资源，${percent}%${cacheText}</p>
        <div class="grid">
          ${room.players.map((player) => `<div class="player-row"><span>${escapeHtml(player.username)}</span><span class="pill ${player.ready ? "ok" : ""}">${player.ready ? "已完成" : "加载中"}</span></div>`).join("")}
        </div>
      </section>
    </main>
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
  const canPlay = self && game.status === "playing" && current?.clientId === app.clientId && !self.eliminated && self.drawPile.length > 0 && !locked;
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
      <section class="log-area">
        ${game.logs.map((log) => `<div>${escapeHtml(log.text)}</div>`).join("")}
      </section>
    </main>
  `;
}

function renderGameAlert(game) {
  const last = game.lastMatch;
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
  if (game.status !== "playing" || !current) return "";
  const label = current.clientId === self?.clientId ? "轮到你出牌" : `轮到 ${current.username} 出牌`;
  let detail = "";
  if (locked) detail = `结算倒计时 ${Math.ceil((game.lockedUntil - Date.now()) / 1000)} 秒`;
  else if (current.connected === false) detail = "玩家掉线，等待自动出牌";
  else if (current.clientId === self?.clientId) detail = "点击高亮牌堆出牌";
  return `<div class="turn-banner"><strong>${escapeHtml(label)}</strong>${detail ? `<span>${escapeHtml(detail)}</span>` : ""}</div>`;
}

function renderStations(game, current, canPlay) {
  return playerLayouts(game.players).map(({ player, x, y }) => {
    const own = player.clientId === app.clientId;
    const isCurrent = current?.clientId === player.clientId;
    return `
      <div class="station ${isCurrent ? "current" : ""} ${player.eliminated ? "eliminated" : ""}" style="left:${x}%;top:${y}%">
        <div class="station-name">${escapeHtml(player.username)} ${player.connected ? "" : "（退出）"} ${player.eliminated ? "（淘汰）" : ""}</div>
        <div class="piles">
          <button class="draw-stack" data-action="play-card" ${own && canPlay ? "" : "disabled"} title="出牌">
            ${renderBackStack(player.drawPile)}
          </button>
          <div class="display-stack">
            ${renderDisplayStack(player.displayPile)}
          </div>
        </div>
        <div class="count">未出 ${player.drawPile.length} | 已出 ${player.displayPile.length}</div>
        ${isCurrent ? renderTurnBanner(game, current, game.players.find((item) => item.clientId === app.clientId), game.lockedUntil > Date.now()) : ""}
      </div>
    `;
  }).join("");
}

function playerLayouts(players) {
  const rotated = rotatePlayersForSelf(players);
  const n = rotated.length || 1;
  return rotated.map((player, index) => {
    const angle = Math.PI / 2 + (Math.PI * 2 * index / n);
    const x = 50 + Math.cos(angle) * 38;
    const y = 49 + Math.sin(angle) * 36;
    return {
      player,
      x,
      y,
      drawX: x - 5.6,
      drawY: y + 1.2,
      displayX: x + 6.8,
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
    <img class="mini-card" src="${escapeAttr(card.imageUrl)}" alt="${escapeAttr(card.pmvName)}" style="${stackStyle(card.id, index, "display", false)}">
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

function renderResult(game) {
  const winner = game.players.find((player) => player.clientId === game.winnerId);
  const average = game.successBellCount ? (game.playCount / game.successBellCount).toFixed(2) : game.playCount;
  const connectedTotal = game.players.filter((player) => player.connected !== false && !player.exited).length;
  const validVotes = game.continueVotes.filter((clientId) => game.players.some((player) => player.clientId === clientId && player.connected !== false && !player.exited)).length;
  const countdown = game.continueReturnAt ? Math.max(0, Math.ceil((game.continueReturnAt - Date.now()) / 1000)) : null;
  return `
    <div class="result-modal">
      <h2>祝贺 ${escapeHtml(winner?.username || "无人")} 胜利</h2>
      <p>总出牌数：${game.playCount}</p>
      <p>抢铃：${game.bellCount} 次，成功 ${game.successBellCount}，失败 ${game.failBellCount}</p>
      <p>平均回合长度：${average}</p>
      <p>继续确认：${validVotes}/${connectedTotal} 人</p>
      ${countdown === null ? "" : `<p>返回等待区倒计时：${countdown} 秒</p>`}
      <button class="primary" data-action="continue-game">继续</button>
    </div>
  `;
}

function renderProfile() {
  const profile = app.snapshot.profile;
  const history = sortRows(profile.history || [], app.sorts.profile);
  return `
    <main class="page">
      <section class="panel">
        <h2>个人信息</h2>
        <p class="status-line">当前账号：${escapeHtml(profile.username || app.snapshot.player?.username || "")}</p>
        <div class="menu-grid">
          <div class="card">参与 ${profile.gamesPlayed}</div>
          <div class="card">胜场 ${profile.wins}</div>
          <div class="card">胜率 ${fmtPct(profile.winRate)}</div>
          <div class="card">拍铃 ${profile.rings} / 正确 ${profile.correctRings} / 错误 ${profile.wrongRings}</div>
          <div class="card">赢牌 ${profile.wonCards}</div>
          <div class="card">平均排名 ${fmtNum(profile.averageRank)}</div>
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
  const players = sortRows(app.snapshot.leaderboard.players, app.sorts.players);
  const matches = sortRows(app.snapshot.leaderboard.matches, app.sorts.matches);
  return `
    <main class="page">
      <section class="panel grid">
        <h2>排行榜</h2>
        ${renderTable("players", players, [
          ["username", "玩家", (row) => row.username],
          ["gamesPlayed", "对局", (row) => row.gamesPlayed],
          ["wins", "胜场", (row) => row.wins],
          ["winRate", "胜率", (row) => fmtPct(row.winRate)],
          ["correctRate", "按铃正确率", (row) => fmtPct(row.correctRate)],
          ["wonCards", "赢牌", (row) => row.wonCards],
          ["averageRank", "平均排名", (row) => fmtNum(row.averageRank)],
        ])}
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

function renderTable(sortName, rows, columns) {
  return `
    <div class="table-wrap">
      <table>
        <thead><tr>${columns.map(([key, label]) => `<th><button data-action="sort" data-sort="${sortName}" data-key="${key}">${label}</button></th>`).join("")}</tr></thead>
        <tbody>
          ${rows.length ? rows.map((row) => `<tr>${columns.map(([, , cell]) => `<td>${escapeHtml(String(cell(row) ?? ""))}</td>`).join("")}</tr>`).join("") : `<tr><td colspan="${columns.length}">暂无数据</td></tr>`}
        </tbody>
      </table>
    </div>
  `;
}

function renderLibraryPicker(libs, selected = null) {
  const selectedSet = new Set(selected || libs.slice(0, 1).map((lib) => lib.id));
  const total = libs.filter((lib) => selectedSet.has(lib.id)).reduce((sum, lib) => sum + lib.cardCount, 0);
  return `
    <div class="panel">
      <h3>卡牌库 <span class="pill" data-card-total>当前 ${total} 张</span></h3>
      <div class="library-list">
        ${libs.map((lib) => `
          <label class="library-row">
            <span>${escapeHtml(lib.name)} <span class="pill">${lib.cardCount} 张 / ${lib.pmvCount} PMV</span></span>
            <input name="libraryIds" value="${escapeAttr(lib.id)}" type="checkbox" ${selectedSet.has(lib.id) ? "checked" : ""}>
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
  if (["home", "create", "join", "rooms", "rules", "profile", "leaderboard"].includes(action)) {
    setRoute(action === "home" ? "home" : action);
    return;
  }
  if (action === "waiting") return setRoute("waiting");
  if (action === "settings") return setRoute("settings");
  if (action === "transfer") return setRoute("settings", { transfer: true });
  if (action === "go-current-room") return routeToCurrent();
  if (action === "leave-room") return leaveRoom();
  if (action === "logout") return logout();
  if (action === "join-public-room") return joinRoom(button.dataset.room);
  if (action === "start-game") return startGame();
  if (action === "play-card") return playCard();
  if (action === "ring-bell") return ringBell();
  if (action === "continue-game") return continueGame();
  if (action === "sort") return sortBy(button.dataset.sort, button.dataset.key);
}

function handleChange(event) {
  if (event.target.name !== "libraryIds") return;
  const form = event.target.closest("form");
  if (!form) return;
  const selected = new Set(new FormData(form).getAll("libraryIds"));
  const total = app.snapshot.cardLibraries
    .filter((library) => selected.has(library.id))
    .reduce((sum, library) => sum + library.cardCount, 0);
  const badge = form.querySelector("[data-card-total]");
  if (badge) badge.textContent = `当前 ${total} 张`;
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
    const room = await postJson("/api/rooms", authPayload({ settings: settingsFromForm(form, true) }));
    await refresh();
    setRoute("waiting", { roomId: room.room.id });
  }
  if (form.id === "join-room-form") {
    const roomId = new FormData(form).get("roomId").trim();
    await joinRoom(roomId);
  }
  if (form.id === "room-settings-form") {
    const room = currentRoom();
    await postJson(`/api/rooms/${encodeURIComponent(room.id)}/settings`, authPayload({ settings: settingsFromForm(form, false) }));
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

async function beginPreload() {
  const room = currentRoom();
  const game = app.snapshot.currentGame;
  if (!room || !game || (app.loading.gameId === game.id && (app.loading.done || app.loading.running))) return;
  if (app.loading.gameId !== game.id) {
    app.loading = { gameId: game.id, loaded: 0, total: 0, done: false, running: false, cached: false, manifestKey: "" };
  }
  app.loading.running = true;
  try {
    const manifest = await getJson(`/api/rooms/${encodeURIComponent(room.id)}/assets${authQuery()}`);
    const urls = manifest.assets || [];
    const cacheKey = `cof.assets.${manifest.key}`;
    app.loading.manifestKey = manifest.key;
    app.loading.cached = localStorage.getItem(cacheKey) === "1";
    app.loading.total = urls.length;
    render();
    for (const url of urls) {
      await preloadImage(url).catch(() => undefined);
      app.loading.loaded += 1;
      render();
    }
    localStorage.setItem(cacheKey, "1");
    app.loading.done = true;
    await postJson(`/api/rooms/${encodeURIComponent(room.id)}/loading-ready`, authPayload());
    await refresh();
    if (app.snapshot.currentGame?.status === "playing") setRoute("game");
  } catch (error) {
    showToast(humanizeError(error.message));
  } finally {
    app.loading.running = false;
  }
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

async function ringBell() {
  const game = app.snapshot.currentGame;
  if (!game) return;
  const button = document.querySelector('button[data-action="ring-bell"]');
  const canRing = button && !button.disabled;
  if (canRing) {
    bellAudio.currentTime = 0;
    bellAudio.play().catch(() => {});
  }
  await safeAction(`/api/games/${encodeURIComponent(game.id)}/ring-bell`);
}

async function continueGame() {
  const game = app.snapshot.currentGame;
  if (!game) return;
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
  if (game?.status === "playing" && game.lockedUntil > Date.now()) {
    app.countdownTimer = window.setTimeout(() => {
      app.countdownTimer = null;
      render();
    }, 1000);
    return;
  }
  if (game?.status === "finished" && game.continueReturnAt && Date.now() < game.continueReturnAt) {
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
    const result = typeof av === "string" ? String(av).localeCompare(String(bv), "zh-Hans-CN") : Number(av || 0) - Number(bv || 0);
    return sort.dir === "asc" ? result : -result;
  });
}

function rotatePlayersForSelf(players) {
  const index = players.findIndex((player) => player.clientId === app.clientId);
  if (index < 0) return players;
  return players.slice(index).concat(players.slice(0, index));
}

function currentRoom() {
  if (app.route.roomId) return app.snapshot.rooms.find((room) => room.id === app.route.roomId);
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
  localStorage.removeItem("cof.token");
  app.token = "";
  app.clientId = "";
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
  return `${Math.round((value || 0) * 100)}%`;
}

function fmtNum(value) {
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
