-- After V10 rebuild / bootstrap imports with explicit ids, BIGSERIAL sequences can lag behind MAX(id).
-- Without this, user submissions fail with duplicate key on cof_pmv_pkey (and deck/card).

SELECT setval(
    pg_get_serial_sequence('cof_deck', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM cof_deck), 1)
);

SELECT setval(
    pg_get_serial_sequence('cof_pmv', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM cof_pmv), 1)
);

SELECT setval(
    pg_get_serial_sequence('cof_card', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM cof_card), 1)
);
