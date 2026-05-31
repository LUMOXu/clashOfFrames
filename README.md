# Clash of Frames

重构版：Vue 3 前端 + Spring Boot 后端。原 Node 单体见 [old/](old/)。

## 新目录

| 目录 | 说明 | 端口 |
|------|------|------|
| [cof-web-vue3](cof-web-vue3/) | Vue 3 + Vite 前端 | **9001** |
| [cof-java-boot](cof-java-boot/) | Spring Boot 3 + Java 17 后端 | **9002** |
| [cof-resource](cof-resource/) | 卡牌与静态资源 | 由后端托管 |
| [cof-sql-version](cof-sql-version/) | Flyway SQL（`cof_db` / `public`） | — |
| [old](old/) | 原 `server.js` + `public/` 等（对照用） | 3000 |

## 快速启动

### 依赖服务

- PostgreSQL 17：数据库 `cof_db`，schema `public`
- Redis 7+

### 后端

```powershell
cd cof-java-boot\cof-boot
$env:COF_RESOURCE_ROOT="D:\Pony\clashOfFrame\cof-resource"
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

首次导入牌组/人机可加：`--import-decks --import-computers`（在 `spring-boot:run` 参数中）。

### 前端

```powershell
cd cof-web-vue3
npm install
npm run dev
```

浏览器打开 http://localhost:9001

## 测试

```powershell
cd cof-java-boot
mvn -pl cof-boot -am verify

cd cof-web-vue3
npm run test:unit
```

## 老项目

```powershell
cd old
node server.js
```

详见 [REFACTORING.md](REFACTORING.md) 与 [cof-java-boot/README.md](cof-java-boot/README.md)。
