INSERT INTO cof_computer_player (
    computer_id, name, description,
    play_delay_mean_seconds, play_delay_std_seconds,
    reaction_mean_seconds, reaction_std_seconds,
    match_detection_probability, false_ring_probability, updated_at
) VALUES (
    'computer_easy', 'Test Bot', 'Test computer player',
    2, 1, 2, 1, 0.5, 0.1, 0
);

-- deck_id is still VARCHAR before V5 migration
INSERT INTO cof_deck (
    deck_id, folder_name, name, curator, description, version, link,
    back_url, card_count, pmv_count, enabled, updated_at
) VALUES (
    'test-deck', 'test-deck', 'Test Deck', 'Test', '', '', '',
    '/cards/1/back.png', 1, 1, TRUE, 0
);

INSERT INTO cof_deck_pmv (deck_id, pmv_id, name, author, description, link)
VALUES ('test-deck', 1, 'PMV 1', 'Author', NULL, '');

INSERT INTO cof_card (deck_id, pmv_id, card_id, shot, file_name, image_url, card_uid)
VALUES (
    'test-deck', 1, 'a', 'a', '1a.jpg',
    '/cards/1/1/a.jpg', '1/1/a'
);
