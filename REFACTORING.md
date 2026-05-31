# Clash of Frames — 重构版说明

原 Node 单体代码已移至 [old/](old/)（`server.js`、`public/`、`src/` 等），供对照参考。本地运行：`cd old && node server.js`（默认 http://localhost:3000）。

## 新目录

| 目录 | 说明 | 端口 |
|------|------|------|
| [cof-web-vue3](cof-web-vue3/) | Vue 3 + Vite 前端 | **9001** |
| [cof-java-boot](cof-java-boot/) | Spring Boot 3 + Java 17 后端 | **9002** |
| [cof-resource](cof-resource/) | 卡牌与静态资源 | 由后端托管 |
| [cof-sql-version](cof-sql-version/) | Flyway SQL（`cof_db` / `public`） | — |

## 快速启动

### 依赖服务

- PostgreSQL 17：数据库 `cof_db`，schema `public`
- Redis 7+

### 后端

```powershell
cd cof-java-boot
mvn -pl cof-boot -am verify
$env:COF_RESOURCE_ROOT="D:\Pony\clashOfFrame\cof-resource"
mvn -pl cof-boot spring-boot:run
```

### 前端

```powershell
cd cof-web-vue3
npm install
npm run dev
```

浏览器打开 http://localhost:9001

## 测试门禁

```powershell
cd cof-java-boot
mvn -pl cof-boot -am verify

cd cof-web-vue3
npm run test:unit
```

## API 变更摘要

- REST 前缀：`/api/v1/*`（替代旧 `/api/bootstrap` 全量快照）
- 会话：`GET /api/v1/session/bootstrap`
- 对局实时：`ws://localhost:9002/ws/v1/game?token=...`（`PLAY` / `RING` / `SYNC`）

详见 [cof-java-boot/README.md](cof-java-boot/README.md)。
