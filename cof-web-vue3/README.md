# cof-web-vue3

Vue 3 + TypeScript 前端，对接 `cof-java-boot`（默认 `http://localhost:9002`）。

## 要求

- Node.js 20+
- npm 9+

## 安装

```bash
cd cof-web-vue3
npm install
```

## 开发

先启动后端（端口 9002），再启动前端：

```bash
npm run dev
```

开发服务器监听 **9001**，并将 `/api`、`/ws` 代理到 `http://localhost:9002`。

## 构建

```bash
npm run build
```

产物在 `dist/`。

## 测试

```bash
npm run test:unit
npm run test:coverage
```

## 路由

与旧版 `app.route.name` 对齐：`auth`、`home`、`create-room`、`join-room`、`rooms`、`rules`、`waiting`、`settings`、`loading`、`game`、`profile`、`leaderboard`、`card-select`、`card-loading`、`card-info`、`pmv-index`。

## 结构

- `src/api/` — REST 客户端（`/api/v1`）
- `src/ws/` — WebSocket（`/ws/v1/game`）
- `src/stores/` — Pinia（auth、lobby、room、game）
- `src/views/` — 页面壳
- `src/assets/styles.css` — 自 `old/public/styles.css` 复制的全局样式
