SET search_path TO public;

ALTER TABLE cof_deck_pmv DROP CONSTRAINT IF EXISTS cof_deck_pmv_deck_id_fkey;
ALTER TABLE cof_card DROP CONSTRAINT IF EXISTS cof_card_deck_id_fkey;

ALTER TABLE cof_deck ADD COLUMN IF NOT EXISTS id BIGSERIAL;

UPDATE cof_deck SET folder_name = deck_id WHERE folder_name IS NULL OR folder_name = '';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_cof_deck_folder'
    ) THEN
        ALTER TABLE cof_deck ADD CONSTRAINT uk_cof_deck_folder UNIQUE (folder_name);
    END IF;
END $$;

ALTER TABLE cof_deck_pmv ADD COLUMN IF NOT EXISTS deck_ref BIGINT;
UPDATE cof_deck_pmv p SET deck_ref = d.id FROM cof_deck d WHERE p.deck_id::text = d.deck_id::text OR p.deck_id = d.deck_id;

ALTER TABLE cof_card ADD COLUMN IF NOT EXISTS deck_ref BIGINT;
UPDATE cof_card c SET deck_ref = d.id FROM cof_deck d WHERE c.deck_id::text = d.deck_id::text OR c.deck_id = d.deck_id;

ALTER TABLE cof_deck_pmv DROP CONSTRAINT IF EXISTS uk_cof_deck_pmv;
DROP INDEX IF EXISTS idx_cof_deck_pmv_deck;
ALTER TABLE cof_deck_pmv DROP COLUMN deck_id;
ALTER TABLE cof_deck_pmv RENAME COLUMN deck_ref TO deck_id;
ALTER TABLE cof_deck_pmv ADD CONSTRAINT uk_cof_deck_pmv UNIQUE (deck_id, pmv_id);

ALTER TABLE cof_card DROP CONSTRAINT IF EXISTS uk_cof_card;
DROP INDEX IF EXISTS idx_cof_card_deck;
ALTER TABLE cof_card DROP COLUMN deck_id;
ALTER TABLE cof_card RENAME COLUMN deck_ref TO deck_id;
ALTER TABLE cof_card ADD CONSTRAINT uk_cof_card UNIQUE (deck_id, pmv_id, card_id);

ALTER TABLE cof_deck DROP CONSTRAINT IF EXISTS cof_deck_pkey;
ALTER TABLE cof_deck DROP COLUMN deck_id;
ALTER TABLE cof_deck ADD PRIMARY KEY (id);

ALTER TABLE cof_deck_pmv
    ADD CONSTRAINT cof_deck_pmv_deck_id_fkey FOREIGN KEY (deck_id) REFERENCES cof_deck (id) ON DELETE CASCADE;
ALTER TABLE cof_card
    ADD CONSTRAINT cof_card_deck_id_fkey FOREIGN KEY (deck_id) REFERENCES cof_deck (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_cof_deck_pmv_deck ON cof_deck_pmv (deck_id, pmv_id);
CREATE INDEX IF NOT EXISTS idx_cof_card_deck ON cof_card (deck_id);
