-- cof_db.public schema (connect to cof_db before running Flyway)
SET search_path TO public;

CREATE TABLE IF NOT EXISTS cof_user (
    client_id       UUID PRIMARY KEY,
    username        VARCHAR(24) NOT NULL,
    password_hash   VARCHAR(128) NOT NULL,
    password_salt   VARCHAR(64),
    password_iterations INTEGER NOT NULL DEFAULT 210000,
    password_digest VARCHAR(16) NOT NULL DEFAULT 'sha256',
    created_at      BIGINT NOT NULL,
    last_login_at   BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cof_user_username ON cof_user (LOWER(username));

CREATE TABLE IF NOT EXISTS cof_user_stats (
    stats_id        VARCHAR(64) PRIMARY KEY,
    username        VARCHAR(24) NOT NULL,
    games_played    INTEGER NOT NULL DEFAULT 0,
    wins            INTEGER NOT NULL DEFAULT 0,
    rings           INTEGER NOT NULL DEFAULT 0,
    correct_rings   INTEGER NOT NULL DEFAULT 0,
    wrong_rings     INTEGER NOT NULL DEFAULT 0,
    won_cards       INTEGER NOT NULL DEFAULT 0,
    total_rank      INTEGER NOT NULL DEFAULT 0,
    is_computer     BOOLEAN NOT NULL DEFAULT FALSE,
    computer_id     VARCHAR(64),
    defeated_computers JSONB NOT NULL DEFAULT '{}',
    god_reward_game_id VARCHAR(64),
    god_defeated_at BIGINT,
    history         JSONB NOT NULL DEFAULT '[]',
    updated_at      BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS cof_match_history (
    id              BIGSERIAL PRIMARY KEY,
    game_id         VARCHAR(64) NOT NULL,
    room_id         VARCHAR(16) NOT NULL,
    played_at       BIGINT NOT NULL,
    summary         JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cof_match_history_played_at ON cof_match_history (played_at DESC);

CREATE TABLE IF NOT EXISTS cof_match_player (
    id              BIGSERIAL PRIMARY KEY,
    match_id        BIGINT NOT NULL REFERENCES cof_match_history(id) ON DELETE CASCADE,
    stats_id        VARCHAR(64) NOT NULL,
    client_id       VARCHAR(64) NOT NULL,
    username        VARCHAR(24) NOT NULL,
    rank            INTEGER,
    is_computer     BOOLEAN NOT NULL DEFAULT FALSE,
    stats_snapshot  JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_cof_match_player_stats ON cof_match_player (stats_id);
