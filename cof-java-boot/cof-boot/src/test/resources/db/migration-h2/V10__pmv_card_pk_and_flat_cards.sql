ALTER TABLE cof_deck_pmv DROP CONSTRAINT IF EXISTS uk_cof_deck_pmv;
ALTER TABLE cof_deck_pmv RENAME COLUMN pmv_id TO match_id;
ALTER TABLE cof_deck_pmv RENAME COLUMN id TO pmv_id;
ALTER TABLE cof_deck_pmv ADD CONSTRAINT uk_cof_deck_pmv_deck_match UNIQUE (deck_id, match_id);
CREATE INDEX IF NOT EXISTS idx_cof_deck_pmv_deck_match ON cof_deck_pmv (deck_id, match_id);

ALTER TABLE cof_card ADD COLUMN IF NOT EXISTS pmv_ref BIGINT;
UPDATE cof_card
SET pmv_ref = (
    SELECT pmv_id
    FROM cof_deck_pmv
    WHERE cof_deck_pmv.deck_id = cof_card.deck_id
      AND cof_deck_pmv.match_id = cof_card.pmv_id
);

ALTER TABLE cof_card DROP CONSTRAINT IF EXISTS uk_cof_card;
ALTER TABLE cof_card DROP COLUMN IF EXISTS pmv_id;
ALTER TABLE cof_card DROP COLUMN IF EXISTS card_id;
ALTER TABLE cof_card RENAME COLUMN id TO card_id;
ALTER TABLE cof_card RENAME COLUMN pmv_ref TO pmv_id;
ALTER TABLE cof_card ADD CONSTRAINT uk_cof_card_pmv_shot UNIQUE (pmv_id, shot);
CREATE INDEX IF NOT EXISTS idx_cof_card_deck ON cof_card (deck_id);
CREATE INDEX IF NOT EXISTS idx_cof_card_pmv ON cof_card (pmv_id);
