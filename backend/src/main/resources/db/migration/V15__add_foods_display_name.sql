-- foods 테이블에 사용자 노출용 display_name 컬럼 추가.
-- name 은 식약처 API 매칭 키로 raw 보존, display_name 은 정리된 사용자 표시명.
--
-- 초기 채움: `_` 포함 row 는 첫 토큰(base)만 추출.
-- 예: 갈비탕_소금제외 → 갈비탕, 김밥_샐러리 → 김밥, 가지볶음_가지 → 가지볶음
-- 도치형 의미 보정(김밥_샐러리 → 샐러리김밥 등)은 별도 수기 UPDATE 로 처리.
-- `_` 없는 row 는 NULL 유지 → FE 에서 name fallback.

ALTER TABLE foods ADD COLUMN display_name VARCHAR(100);

UPDATE foods
SET display_name = SPLIT_PART(name, '_', 1)
WHERE name LIKE '%\_%' ESCAPE '\';
