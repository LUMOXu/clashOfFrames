-- Visual replay timeline (PublicGame snapshots)
ALTER TABLE cof_match_history
    ADD COLUMN IF NOT EXISTS replay_json TEXT;
