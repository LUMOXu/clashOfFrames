# cof-sql-version

PostgreSQL migrations for **cof_db** database, **public** schema.

Flyway scripts are copied into `cof-java-boot/cof-boot/src/main/resources/db/migration` at build time,
or referenced via `spring.flyway.locations=filesystem:../cof-sql-version`.

## Local database

```sql
CREATE DATABASE cof_db;
\c cof_db
SET search_path TO public;
```

## Naming

- `V1__init.sql` — core tables (users, stats, match history)
- `V2__card_catalog.sql` — `cof_deck`, `cof_deck_pmv`, `cof_card`
- `V3__computer_players.sql` — `cof_computer_player`
- `V4__deck_numeric_id.sql` — 牌组主键改为自增数字 `id`，`folder_name` 保留资源目录名

## Local run (Java)

```bash
mvn -pl cof-boot -am spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--import-decks --import-computers"
```

Uses `application-local.yml` (PostgreSQL `postgres/123123@127.0.0.1:5432/cof_db`, Redis db `2` with password `123456`).
