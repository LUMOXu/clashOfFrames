SET search_path TO public;

-- PMV: drop surrogate id; pmv_id becomes PK; old per-deck pmv_id becomes match_id (bell matching).
ALTER TABLE cof_deck_pmv DROP CONSTRAINT IF EXISTS uk_cof_deck_pmv;
ALTER TABLE cof_deck_pmv RENAME COLUMN pmv_id TO match_id;
ALTER TABLE cof_deck_pmv DROP CONSTRAINT IF EXISTS cof_deck_pmv_pkey;
ALTER TABLE cof_deck_pmv RENAME COLUMN id TO pmv_id;
ALTER TABLE cof_deck_pmv ADD PRIMARY KEY (pmv_id);
ALTER TABLE cof_deck_pmv ADD CONSTRAINT uk_cof_deck_pmv_deck_match UNIQUE (deck_id, match_id);
CREATE INDEX IF NOT EXISTS idx_cof_deck_pmv_deck_match ON cof_deck_pmv (deck_id, match_id);

-- Card: link to PMV row by pmv_id FK; card_id becomes PK; drop legacy varchar card_id.
ALTER TABLE cof_card ADD COLUMN IF NOT EXISTS pmv_ref BIGINT;
UPDATE cof_card c
SET pmv_ref = p.pmv_id
FROM cof_deck_pmv p
WHERE c.deck_id = p.deck_id
  AND c.pmv_id = p.match_id;

ALTER TABLE cof_card DROP CONSTRAINT IF EXISTS uk_cof_card;
ALTER TABLE cof_card DROP CONSTRAINT IF EXISTS cof_card_pkey;
ALTER TABLE cof_card DROP COLUMN IF EXISTS pmv_id;
ALTER TABLE cof_card DROP COLUMN IF EXISTS card_id;
ALTER TABLE cof_card RENAME COLUMN id TO card_id;
ALTER TABLE cof_card RENAME COLUMN pmv_ref TO pmv_id;
ALTER TABLE cof_card ADD PRIMARY KEY (card_id);
ALTER TABLE cof_card
    ADD CONSTRAINT cof_card_pmv_id_fkey FOREIGN KEY (pmv_id) REFERENCES cof_deck_pmv (pmv_id) ON DELETE CASCADE;
ALTER TABLE cof_card ADD CONSTRAINT uk_cof_card_pmv_shot UNIQUE (pmv_id, shot);
CREATE INDEX IF NOT EXISTS idx_cof_card_deck ON cof_card (deck_id);
CREATE INDEX IF NOT EXISTS idx_cof_card_pmv ON cof_card (pmv_id);
