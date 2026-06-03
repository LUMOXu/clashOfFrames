SET search_path TO public;

-- Backup legacy catalog tables (production rollback: rename back + drop new tables).
ALTER TABLE IF EXISTS cof_deck RENAME TO old_cof_deck;
ALTER TABLE IF EXISTS cof_deck_pmv RENAME TO old_cof_deck_pmv;
ALTER TABLE IF EXISTS cof_card RENAME TO old_cof_card;

CREATE TABLE cof_deck (
    id                      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(255) NOT NULL,
    description             TEXT,
    back_url                VARCHAR(512) NOT NULL DEFAULT '',
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    review_status           VARCHAR(16) NOT NULL DEFAULT 'pending',
    pending_review_status   VARCHAR(16),
    pending_name            VARCHAR(255),
    pending_description     TEXT,
    pending_back_url        VARCHAR(512),
    submitter_client_id     VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_cof_deck_name ON cof_deck (name) WHERE deleted_at IS NULL;
CREATE INDEX idx_cof_deck_submitter ON cof_deck (submitter_client_id) WHERE deleted_at IS NULL;

CREATE TABLE cof_pmv (
    id                      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(512) NOT NULL,
    author                  VARCHAR(255),
    description             TEXT,
    link                    VARCHAR(512),
    review_status           VARCHAR(16) NOT NULL DEFAULT 'pending',
    pending_review_status   VARCHAR(16),
    pending_name            VARCHAR(512),
    pending_author          VARCHAR(255),
    pending_description     TEXT,
    pending_link            VARCHAR(512),
    submitter_client_id     VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_cof_pmv_name ON cof_pmv (name) WHERE deleted_at IS NULL;
CREATE INDEX idx_cof_pmv_submitter ON cof_pmv (submitter_client_id) WHERE deleted_at IS NULL;

CREATE TABLE cof_card (
    id                      BIGSERIAL PRIMARY KEY,
    deck_id                 BIGINT NOT NULL,
    pmv_id                  BIGINT NOT NULL,
    name                    VARCHAR(512),
    description             TEXT,
    image_url               VARCHAR(512) NOT NULL,
    review_status           VARCHAR(16) NOT NULL DEFAULT 'pending',
    pending_review_status   VARCHAR(16),
    pending_name            VARCHAR(512),
    pending_description     TEXT,
    pending_image_url       VARCHAR(512),
    submitter_client_id     VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_cof_card_image_url ON cof_card (image_url) WHERE deleted_at IS NULL;
CREATE INDEX idx_cof_card_deck ON cof_card (deck_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_cof_card_pmv ON cof_card (pmv_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_cof_card_deck_pmv ON cof_card (deck_id, pmv_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_cof_card_submitter ON cof_card (submitter_client_id) WHERE deleted_at IS NULL;
