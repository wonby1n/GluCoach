-- 음식 추천 agent 시연용 데이터 시딩
-- ============================================
-- 사용법:
--   docker compose exec postgres psql -U postgres -d glucocoach -f /tmp/seed_food_recommend_demo.sql
--   또는 로컬 PG에 직접:
--     psql "$DATABASE_URL" -f ai/scripts/seed_food_recommend_demo.sql
--
-- 멱등 안전: ON CONFLICT DO UPDATE 또는 NOT EXISTS 가드 사용.
-- 대상 사용자: id=5 (시연 고정).
-- 음식 id는 foods.name으로 lookup하여 환경 차이 흡수.

\set demo_user_id 5

-- ── 1. user_food_grades : 사용자 음식 등급 ────────────
-- (이름이 foods 테이블에 없으면 해당 row는 skip)

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 1.2, 'S', 8 FROM foods f WHERE f.name = '현미밥' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE
   SET avg_slope = EXCLUDED.avg_slope, grade = EXCLUDED.grade, meal_count = EXCLUDED.meal_count, updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 1.4, 'S', 6 FROM foods f WHERE f.name LIKE '%닭가슴살%' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE
   SET avg_slope = EXCLUDED.avg_slope, grade = EXCLUDED.grade, meal_count = EXCLUDED.meal_count, updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 1.8, 'A', 5 FROM foods f WHERE f.name LIKE '%두부조림%' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE
   SET avg_slope = EXCLUDED.avg_slope, grade = EXCLUDED.grade, meal_count = EXCLUDED.meal_count, updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 2.0, 'A', 4 FROM foods f WHERE f.name = '고등어구이' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE
   SET avg_slope = EXCLUDED.avg_slope, grade = EXCLUDED.grade, meal_count = EXCLUDED.meal_count, updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 2.5, 'B', 5 FROM foods f WHERE f.name LIKE '%잡곡밥%' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE
   SET avg_slope = EXCLUDED.avg_slope, grade = EXCLUDED.grade, meal_count = EXCLUDED.meal_count, updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 3.5, 'C', 7 FROM foods f WHERE f.name = '쌀밥' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE
   SET avg_slope = EXCLUDED.avg_slope, grade = EXCLUDED.grade, meal_count = EXCLUDED.meal_count, updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 4.8, 'D', 3 FROM foods f WHERE f.name = '라면' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE
   SET avg_slope = EXCLUDED.avg_slope, grade = EXCLUDED.grade, meal_count = EXCLUDED.meal_count, updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_food_grades (user_id, food_id, avg_slope, grade, meal_count)
SELECT :demo_user_id, f.id, 5.1, 'D', 2 FROM foods f WHERE f.name = '떡볶이' LIMIT 1
ON CONFLICT (user_id, food_id) DO UPDATE
   SET avg_slope = EXCLUDED.avg_slope, grade = EXCLUDED.grade, meal_count = EXCLUDED.meal_count, updated_at = CURRENT_TIMESTAMP;

-- ── 2. meal_records : 최근 식사 3건 (현재 시각 기준) ────
-- 멱등성 위해 동일 user_id+recorded_at이 이미 있으면 skip.

INSERT INTO meal_records (user_id, food_id, image_origin_name, image_storage_key, is_processed, memo, recorded_at)
SELECT :demo_user_id, f.id, NULL, NULL, true, '시연 시드', CURRENT_TIMESTAMP - INTERVAL '3 hours'
FROM foods f WHERE f.name = '현미밥'
  AND NOT EXISTS (SELECT 1 FROM meal_records m WHERE m.user_id = :demo_user_id AND m.recorded_at = CURRENT_TIMESTAMP - INTERVAL '3 hours')
LIMIT 1;

INSERT INTO meal_records (user_id, food_id, image_origin_name, image_storage_key, is_processed, memo, recorded_at)
SELECT :demo_user_id, f.id, NULL, NULL, true, '시연 시드', CURRENT_TIMESTAMP - INTERVAL '20 hours'
FROM foods f WHERE f.name LIKE '%닭가슴살%'
  AND NOT EXISTS (SELECT 1 FROM meal_records m WHERE m.user_id = :demo_user_id AND m.recorded_at = CURRENT_TIMESTAMP - INTERVAL '20 hours')
LIMIT 1;

INSERT INTO meal_records (user_id, food_id, image_origin_name, image_storage_key, is_processed, memo, recorded_at)
SELECT :demo_user_id, f.id, NULL, NULL, true, '시연 시드', CURRENT_TIMESTAMP - INTERVAL '1 day 4 hours'
FROM foods f WHERE f.name = '쌀밥'
  AND NOT EXISTS (SELECT 1 FROM meal_records m WHERE m.user_id = :demo_user_id AND m.recorded_at = CURRENT_TIMESTAMP - INTERVAL '1 day 4 hours')
LIMIT 1;

-- ── 3. glucose_records : 최근 혈당 측정 ────────────────
-- 식후 3시간 = stable 영역. 130~140 mg/dL 안정 패턴.

INSERT INTO glucose_records (user_id, value, measured_at)
SELECT :demo_user_id, 138.0, CURRENT_TIMESTAMP - INTERVAL '5 minutes'
WHERE NOT EXISTS (SELECT 1 FROM glucose_records g WHERE g.user_id = :demo_user_id AND g.measured_at = CURRENT_TIMESTAMP - INTERVAL '5 minutes');

INSERT INTO glucose_records (user_id, value, measured_at)
SELECT :demo_user_id, 135.0, CURRENT_TIMESTAMP - INTERVAL '20 minutes'
WHERE NOT EXISTS (SELECT 1 FROM glucose_records g WHERE g.user_id = :demo_user_id AND g.measured_at = CURRENT_TIMESTAMP - INTERVAL '20 minutes');

INSERT INTO glucose_records (user_id, value, measured_at)
SELECT :demo_user_id, 142.0, CURRENT_TIMESTAMP - INTERVAL '40 minutes'
WHERE NOT EXISTS (SELECT 1 FROM glucose_records g WHERE g.user_id = :demo_user_id AND g.measured_at = CURRENT_TIMESTAMP - INTERVAL '40 minutes');

-- ── 4. users 프로필 업데이트 (id=5가 이미 존재한다고 가정) ──
-- diabetes_type / target 영역 셋팅. 사용자가 없으면 NOOP.

UPDATE users
   SET age = COALESCE(age, 45),
       gender = COALESCE(gender, 'M'),
       diabetes_type = COALESCE(diabetes_type, 'T2D'),
       target_low = COALESCE(target_low, 70.0),
       target_high = COALESCE(target_high, 180.0),
       is_medicated = COALESCE(is_medicated, true)
 WHERE id = :demo_user_id;

-- ── 검증 ─────────────────────────────────────────────
SELECT 'food_grades' AS table_name, COUNT(*) AS rows FROM user_food_grades WHERE user_id = :demo_user_id
UNION ALL
SELECT 'meal_records', COUNT(*) FROM meal_records WHERE user_id = :demo_user_id AND recorded_at > CURRENT_TIMESTAMP - INTERVAL '2 days'
UNION ALL
SELECT 'glucose_records', COUNT(*) FROM glucose_records WHERE user_id = :demo_user_id AND measured_at > CURRENT_TIMESTAMP - INTERVAL '1 hour';
