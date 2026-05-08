-- user_id=6 — user5와 정반대 패턴으로 시드 (비교용)
-- ============================================
-- user5: 채소·생선 S/A, 정제탄수 C/D, 식후 3시간 안정(138)
-- user6: 정제탄수 S/A, 채소 C/D, 방금 식사(라면) 후 고혈당(220)
--   → "최근 라면 먹어서 혈당 높음, 안정 음식 추천" 모드 예상

\set demo_user_id 6

INSERT INTO users (id, email, password, provider, name, age, gender, phone,
                   height, weight, diabetes_type, is_medicated, target_low, target_high)
VALUES (:demo_user_id, 'demo6@glucocoach.local', NULL, 'email', '시연유저6',
        62, 'F', '010-0000-0006', 158.0, 65.0, 'T1D', false, 80.0, 180.0)
ON CONFLICT (id) DO UPDATE
   SET age = EXCLUDED.age, gender = EXCLUDED.gender,
       diabetes_type = EXCLUDED.diabetes_type, is_medicated = EXCLUDED.is_medicated,
       target_low = EXCLUDED.target_low, target_high = EXCLUDED.target_high,
       height = EXCLUDED.height, weight = EXCLUDED.weight;

SELECT setval(pg_get_serial_sequence('users', 'id'),
              GREATEST((SELECT MAX(id) FROM users), 1));

-- 등급: 정반대로
INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 1.1, 'S', 9 FROM foods f WHERE f.name = '쌀밥' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE SET avg_slope=EXCLUDED.avg_slope, grade=EXCLUDED.grade, meal_count=EXCLUDED.meal_count, updated_at=CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 1.5, 'S', 7 FROM foods f WHERE f.name = '잡곡밥' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE SET avg_slope=EXCLUDED.avg_slope, grade=EXCLUDED.grade, meal_count=EXCLUDED.meal_count, updated_at=CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 1.9, 'A', 5 FROM foods f WHERE f.name = '고등어구이' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE SET avg_slope=EXCLUDED.avg_slope, grade=EXCLUDED.grade, meal_count=EXCLUDED.meal_count, updated_at=CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 2.6, 'B', 4 FROM foods f WHERE f.name = '두부조림' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE SET avg_slope=EXCLUDED.avg_slope, grade=EXCLUDED.grade, meal_count=EXCLUDED.meal_count, updated_at=CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 3.8, 'C', 6 FROM foods f WHERE f.name = '닭가슴살 샐러드' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE SET avg_slope=EXCLUDED.avg_slope, grade=EXCLUDED.grade, meal_count=EXCLUDED.meal_count, updated_at=CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 4.5, 'D', 4 FROM foods f WHERE f.name = '현미밥' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE SET avg_slope=EXCLUDED.avg_slope, grade=EXCLUDED.grade, meal_count=EXCLUDED.meal_count, updated_at=CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 5.5, 'D', 3 FROM foods f WHERE f.name = '라면' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE SET avg_slope=EXCLUDED.avg_slope, grade=EXCLUDED.grade, meal_count=EXCLUDED.meal_count, updated_at=CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 5.8, 'D', 2 FROM foods f WHERE f.name = '떡볶이' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE SET avg_slope=EXCLUDED.avg_slope, grade=EXCLUDED.grade, meal_count=EXCLUDED.meal_count, updated_at=CURRENT_TIMESTAMP;

-- 최근 식사: 30분 전 라면 (방금 먹음 → 식후 고혈당 케이스)
INSERT INTO meal_records (user_id, food_id, image_origin_name, image_storage_key, is_processed, memo, recorded_at)
SELECT :demo_user_id, f.id, NULL, NULL, true, '시연 시드', CURRENT_TIMESTAMP - INTERVAL '30 minutes'
FROM foods f WHERE f.name = '라면'
  AND NOT EXISTS (SELECT 1 FROM meal_records m WHERE m.user_id = :demo_user_id AND m.recorded_at = CURRENT_TIMESTAMP - INTERVAL '30 minutes')
LIMIT 1;

INSERT INTO meal_records (user_id, food_id, image_origin_name, image_storage_key, is_processed, memo, recorded_at)
SELECT :demo_user_id, f.id, NULL, NULL, true, '시연 시드', CURRENT_TIMESTAMP - INTERVAL '14 hours'
FROM foods f WHERE f.name = '떡볶이'
  AND NOT EXISTS (SELECT 1 FROM meal_records m WHERE m.user_id = :demo_user_id AND m.recorded_at = CURRENT_TIMESTAMP - INTERVAL '14 hours')
LIMIT 1;

INSERT INTO meal_records (user_id, food_id, image_origin_name, image_storage_key, is_processed, memo, recorded_at)
SELECT :demo_user_id, f.id, NULL, NULL, true, '시연 시드', CURRENT_TIMESTAMP - INTERVAL '24 hours'
FROM foods f WHERE f.name = '쌀밥'
  AND NOT EXISTS (SELECT 1 FROM meal_records m WHERE m.user_id = :demo_user_id AND m.recorded_at = CURRENT_TIMESTAMP - INTERVAL '24 hours')
LIMIT 1;

-- 혈당: 식후 30분 — 220mg/dL (스파이크 직후)
INSERT INTO glucose_records (user_id, value, measured_at)
SELECT :demo_user_id, 220.0, CURRENT_TIMESTAMP - INTERVAL '5 minutes'
WHERE NOT EXISTS (SELECT 1 FROM glucose_records g WHERE g.user_id = :demo_user_id AND g.measured_at = CURRENT_TIMESTAMP - INTERVAL '5 minutes');

INSERT INTO glucose_records (user_id, value, measured_at)
SELECT :demo_user_id, 195.0, CURRENT_TIMESTAMP - INTERVAL '20 minutes'
WHERE NOT EXISTS (SELECT 1 FROM glucose_records g WHERE g.user_id = :demo_user_id AND g.measured_at = CURRENT_TIMESTAMP - INTERVAL '20 minutes');

INSERT INTO glucose_records (user_id, value, measured_at)
SELECT :demo_user_id, 145.0, CURRENT_TIMESTAMP - INTERVAL '40 minutes'
WHERE NOT EXISTS (SELECT 1 FROM glucose_records g WHERE g.user_id = :demo_user_id AND g.measured_at = CURRENT_TIMESTAMP - INTERVAL '40 minutes');

-- parent user command 미리 INSERT
INSERT INTO chat_messages (user_id, sender, message, command_type)
VALUES (:demo_user_id, 'user', '뭐 먹을까요?', 'recommend_food');

SELECT 'user6_grades' AS tbl, grade, COUNT(*) FROM user_food_grades WHERE user_id = :demo_user_id GROUP BY grade ORDER BY grade;
SELECT 'user6_chat_id' AS tbl, MAX(id) FROM chat_messages WHERE user_id = :demo_user_id;
