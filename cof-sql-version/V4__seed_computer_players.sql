-- 人机参数种子数据（与 cof-resource/config/computerPlayers.json 一致，启动时也会 upsert）
SET search_path TO public;

INSERT INTO cof_computer_player (
    computer_id, name, description,
    play_delay_mean_seconds, play_delay_std_seconds,
    reaction_mean_seconds, reaction_std_seconds,
    match_detection_probability, false_ring_probability, updated_at
) VALUES
    ('computer_easy', '一般通过', '“大家做的pmv视频，不知道是否pxxn music video？”', 2, 1, 2, 2, 0.1, 0.05, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('computer_casual', '巴甫洛夫的狗', '很喜欢按铃的音效，不过也就这样了。', 2, 0.5, 1, 0.2, 0.5, 0.25, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('computer_normal', '分心玩家', '一只眼看着纸质的对应表，一只眼看着屏幕，所以反应慢了点，但是正确率肯定有保障。', 2, 1, 3.5, 1, 0.99, 0.01, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('computer_hard', '背图狂人', '花了一阵把卡组的图背下来了。', 1.8, 0.5, 2, 1, 0.85, 0.01, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('computer_master', 'Master', '游戏的设计者。', 1.5, 0, 0.8, 0.5, 0.97, 0.005, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000),
    ('computer_god', 'GOD', '传说中的存在。', 1.2, 0, 0.5, 0.3, 0.995, 0.002, EXTRACT(EPOCH FROM NOW())::BIGINT * 1000)
ON CONFLICT (computer_id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    play_delay_mean_seconds = EXCLUDED.play_delay_mean_seconds,
    play_delay_std_seconds = EXCLUDED.play_delay_std_seconds,
    reaction_mean_seconds = EXCLUDED.reaction_mean_seconds,
    reaction_std_seconds = EXCLUDED.reaction_std_seconds,
    match_detection_probability = EXCLUDED.match_detection_probability,
    false_ring_probability = EXCLUDED.false_ring_probability,
    updated_at = EXCLUDED.updated_at;
