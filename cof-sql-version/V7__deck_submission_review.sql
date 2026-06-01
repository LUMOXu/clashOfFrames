-- User submissions: pending until reviewed via deploy/review_submissions.py
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
