# AGENTS.md

## Cursor Cloud specific instructions

Monorepo-style layout (single git repo, multiple apps):

| Path | Role | Port |
|------|------|------|
| `cof-web-vue3/` | Vue 3 + Vite frontend | **9001** |
| `cof-java-boot/` | Spring Boot 3 (Java 17) backend | **9002** |
| `cof-resource/` | Card assets (`COF_RESOURCE_ROOT`) | served by backend |
| `cof-sql-version/` | Flyway SQL source (copied into boot resources) |

### Required services (local profile)

- **PostgreSQL 16+**: database `cof_db`, user `postgres` / password `123123` (see `application-local.yml`)
- **Redis 7**: `127.0.0.1:6379`, password `123456`, database `2`

Create DB once: `CREATE DATABASE cof_db;`

### Build

```bash
# Backend (skip tests if cof-service tests fail on branch)
cd cof-java-boot
export COF_RESOURCE_ROOT=/workspace/cof-resource
mvn -pl cof-boot -am package -DskipTests

# Frontend
cd cof-web-vue3
npm install
npm run build
```

Full verify (may fail on `feature-dyu`): `mvn -pl cof-boot -am verify`

### Run (development)

```bash
# Backend — use -am when using spring-boot:run from repo root
export COF_RESOURCE_ROOT=/workspace/cof-resource
java -jar cof-java-boot/cof-boot/target/cof-boot-1.0.0-SNAPSHOT.jar --spring.profiles.active=local

# Import decks/computers (local profile only)
curl -X POST http://127.0.0.1:9002/api/v1/admin/import-decks
curl -X POST http://127.0.0.1:9002/api/v1/admin/import-computers

# Frontend (proxies /api, /ws, /cards to 9002)
cd cof-web-vue3 && npm run dev
```

Open http://localhost:9001

### Gotchas

- `spring-boot:run` from `cof-boot` alone fails without `-am`; prefer the packaged JAR or `mvn -pl cof-boot -am spring-boot:run`.
- Deck import on first insert requires `back_url` NOT NULL — fixed in `DeckCatalogImportService` (placeholder then update after id).
- `feature-dyu` may have failing unit tests and incomplete game logic (see latest commit message).
