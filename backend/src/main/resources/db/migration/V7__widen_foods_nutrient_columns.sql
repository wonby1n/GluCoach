-- foods.sodium_mg, cholesterol_mg, serving_size 컬럼 정밀도 확장
-- 사유:
--  - 나트륨/콜레스테롤: 100g당 1000mg 초과 항목 존재 (라면, 김치, 내장류 등)
--  - 서빙사이즈: 식약처 식품중량 컬럼이 포장 단위(1.1kg 국류 등)라 1000g 초과 다수
-- 기존 NUMERIC(5,2)는 max 999.99 로 모두 부족.
ALTER TABLE foods
    ALTER COLUMN sodium_mg      TYPE NUMERIC(7, 2),
    ALTER COLUMN cholesterol_mg TYPE NUMERIC(7, 2),
    ALTER COLUMN serving_size   TYPE NUMERIC(7, 2);
