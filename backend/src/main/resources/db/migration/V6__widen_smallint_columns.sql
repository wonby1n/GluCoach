git commit -m "feat: V6 마이그레이션 - SMALLINT 컬럼을 INT로 확장 (Short cascading 정리)"-- 자바 Integer 매핑 정합을 위해 SMALLINT 컬럼들을 INT로 확장
ALTER TABLE users ALTER COLUMN age TYPE INT;
ALTER TABLE users ALTER COLUMN week_start_day TYPE INT;
ALTER TABLE ward_guardian ALTER COLUMN priority TYPE INT;
