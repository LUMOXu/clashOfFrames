SET search_path TO public;

CREATE TABLE IF NOT EXISTS cof_computer_player (
    computer_id                 VARCHAR(64) PRIMARY KEY,
    name                        VARCHAR(64) NOT NULL,
    description                 TEXT,
    play_delay_mean_seconds     DOUBLE PRECISION NOT NULL DEFAULT 2,
    play_delay_std_seconds      DOUBLE PRECISION NOT NULL DEFAULT 1,
    reaction_mean_seconds       DOUBLE PRECISION NOT NULL DEFAULT 2,
    reaction_std_seconds        DOUBLE PRECISION NOT NULL DEFAULT 1,
    match_detection_probability DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    false_ring_probability      DOUBLE PRECISION NOT NULL DEFAULT 0.1,
    updated_at                  BIGINT NOT NULL
);
