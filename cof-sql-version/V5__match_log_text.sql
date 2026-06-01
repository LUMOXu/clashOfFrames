-- Match replay log (plain text, relative timestamps from game start)
ALTER TABLE cof_match_history
    ADD COLUMN IF NOT EXISTS log_text TEXT;
