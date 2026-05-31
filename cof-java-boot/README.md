# Clash of Frames — Java Backend (`cof-java-boot`)

Maven multi-module Spring Boot backend for Clash of Frames.

## Modules

| Module | Description |
|--------|-------------|
| `cof-common` | API envelopes, errors, auth/ws DTOs, `PasswordUtil` |
| `cof-domain` | MyBatis-Plus entities & mappers (`cof_db` / `public`) |
| `cof-game-engine` | Pure game rules (`GameCore`) |
| `cof-service` | Redis-backed sessions/rooms/games, meta, migration |
| `cof-api` | REST `/api/v1/*`, WebSocket `/ws/v1/game` |
| `cof-boot` | Runnable app (port **9002**) |

## Prerequisites

- JDK 17+
- Maven 3.9+
- PostgreSQL database `cof_db` (for local run)
- Redis (for local run)
- Optional: `COF_RESOURCE_ROOT` pointing at `../cof-resource` (card assets)

## Verify (all modules + tests)

From `cof-java-boot`:

```bash
mvn -pl cof-boot -am verify
```

Unit tests mock Redis (`JsonRedisOps` / `@MockBean`); `cof-boot` tests use H2 + in-memory Redis helper.

## Run locally

```bash
# Terminal 1 — Postgres & Redis must be up
export COF_RESOURCE_ROOT=../cof-resource   # or set in application.yml

mvn -pl cof-boot spring-boot:run
```

Server: `http://localhost:9002`  
CORS allows `http://localhost:9001`.

## Configuration

`cof-boot/src/main/resources/application.yml`:

- JDBC → `cof_db`
- Flyway → `classpath:db/migration` (`V1__init.sql` from `cof-sql-version`)
- Redis keys: `cof:session:{token}`, `cof:game:{id}`, `cof:room:{id}`, caches

Test profile: `application-test.yml` (H2 in-memory DB).

## Password migration

Legacy magic reset hash `123456` is accepted once; login re-hashes with PBKDF2-SHA256 (210000 iterations).  
Import old `data/state.json` via `StateJsonMigrationService`.
