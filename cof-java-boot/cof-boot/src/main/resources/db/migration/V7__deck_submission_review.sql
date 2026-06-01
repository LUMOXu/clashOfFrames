SET search_path TO public;

ALTER TABLE cof_deck
    ADD COLUMN IF NOT EXISTS submitter_client_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(16) NOT NULL DEFAULT 'approved';

ALTER TABLE cof_deck_pmv
    ADD COLUMN IF NOT EXISTS submitter_client_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(16) NOT NULL DEFAULT 'approved';

ALTER TABLE cof_card
    ADD COLUMN IF NOT EXISTS submitter_client_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(16) NOT NULL DEFAULT 'approved';

UPDATE cof_deck SET review_status = 'approved' WHERE review_status IS NULL;
UPDATE cof_deck_pmv SET review_status = 'approved' WHERE review_status IS NULL;
UPDATE cof_card SET review_status = 'approved' WHERE review_status IS NULL;

CREATE INDEX IF NOT EXISTS idx_cof_deck_review ON cof_deck (review_status);
CREATE INDEX IF NOT EXISTS idx_cof_deck_pmv_review ON cof_deck_pmv (review_status);
CREATE INDEX IF NOT EXISTS idx_cof_card_review ON cof_card (review_status);
