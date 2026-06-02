DELETE FROM cof_match_history a
    USING cof_match_history b
WHERE a.id > b.id
  AND a.game_id = b.game_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_cof_match_history_game_id ON cof_match_history (game_id);
