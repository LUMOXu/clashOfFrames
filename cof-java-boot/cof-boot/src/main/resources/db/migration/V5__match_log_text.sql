ALTER TABLE cof_match_history
    ADD COLUMN IF NOT EXISTS log_text TEXT;
