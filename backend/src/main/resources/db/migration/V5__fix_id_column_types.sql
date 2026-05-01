-- V5: meal_records, exercise_records, sleep_records id 컬럼 INT → BIGINT 변환
-- V4에서 잘못 적용된 INT 타입을 JPA 엔티티(Long)에 맞게 BIGINT로 수정

ALTER TABLE meal_records ALTER COLUMN id TYPE BIGINT;
ALTER TABLE exercise_records ALTER COLUMN id TYPE BIGINT;
ALTER TABLE sleep_records ALTER COLUMN id TYPE BIGINT;
