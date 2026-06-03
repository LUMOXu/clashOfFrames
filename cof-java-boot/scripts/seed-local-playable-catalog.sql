-- Optional local dev seed after V10 when new catalog tables are empty.
-- Safe to re-run: uses fixed ids 1–4 on deck/pmv/cards.

INSERT INTO cof_deck (id, name, description, back_url, enabled, review_status, created_at, updated_at)
VALUES (1, '本地测试牌组', 'Dev playable deck', '/cards/backs/1.jpg', TRUE, 'approved', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    enabled = TRUE,
    review_status = 'approved',
    deleted_at = NULL;

INSERT INTO cof_pmv (id, name, author, description, review_status, created_at, updated_at)
VALUES (1, '本地测试 PMV', 'Dev', 'Seed PMV for local matches', 'approved', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    review_status = 'approved',
    deleted_at = NULL;

INSERT INTO cof_card (id, deck_id, pmv_id, name, image_url, review_status, created_at, updated_at)
VALUES
  (1, 1, 1, 'Card A', '/cards/test-a.jpg', 'approved', NOW(), NOW()),
  (2, 1, 1, 'Card B', '/cards/test-b.jpg', 'approved', NOW(), NOW()),
  (3, 1, 1, 'Card C', '/cards/test-c.jpg', 'approved', NOW(), NOW()),
  (4, 1, 1, 'Card D', '/cards/test-d.jpg', 'approved', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    review_status = 'approved',
    deleted_at = NULL;

SELECT setval(pg_get_serial_sequence('cof_deck', 'id'), GREATEST((SELECT MAX(id) FROM cof_deck), 100));
SELECT setval(pg_get_serial_sequence('cof_pmv', 'id'), GREATEST((SELECT MAX(id) FROM cof_pmv), 100));
SELECT setval(pg_get_serial_sequence('cof_card', 'id'), GREATEST((SELECT MAX(id) FROM cof_card), 100));
