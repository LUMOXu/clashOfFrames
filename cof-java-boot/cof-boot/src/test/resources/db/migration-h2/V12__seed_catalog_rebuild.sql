INSERT INTO cof_deck (id, name, description, back_url, enabled, review_status, created_at, updated_at)
VALUES (1, 'Test Deck', 'Integration test deck', '/cards/backs/1.jpg', TRUE, 'approved', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO cof_pmv (id, name, author, description, review_status, created_at, updated_at)
VALUES (1, 'Test PMV', 'Tester', 'Seed PMV', 'approved', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO cof_card (id, deck_id, pmv_id, name, image_url, review_status, created_at, updated_at)
VALUES
  (1, 1, 1, 'Card A', '/cards/test-a.jpg', 'approved', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 1, 1, 'Card B', '/cards/test-b.jpg', 'approved', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Explicit ids above do not advance H2 identity; restart so submissions get id > 2.
ALTER TABLE cof_deck ALTER COLUMN id RESTART WITH 100;
ALTER TABLE cof_pmv ALTER COLUMN id RESTART WITH 100;
ALTER TABLE cof_card ALTER COLUMN id RESTART WITH 100;
