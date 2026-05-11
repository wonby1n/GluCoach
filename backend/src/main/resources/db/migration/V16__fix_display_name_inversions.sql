-- foods.display_name 도치형 보정 — V15 base 추출에서 누락된 의미 보강.
-- name 컬럼(식약처 API 매칭 키)은 raw 유지, display_name 만 사용자 표시용으로 정정.
--
-- 대상: search_count 상위 도치형(<base>_<modifier>) row.
-- 같은 name 의 중복 row 도 함께 보정되도록 name 기준 UPDATE.
-- 도치 부자연스러운 라면 변종(라면_국물, 라면_면, 라면_라면만, 라면_용기면 계열)은
-- V15 의 '라면' 기본값 그대로 유지.

-- 초밥
UPDATE foods SET display_name = '연어초밥'     WHERE name = '초밥_연어';
UPDATE foods SET display_name = '광어초밥'     WHERE name = '초밥_광어';
UPDATE foods SET display_name = '농어초밥'     WHERE name = '초밥_농어';
UPDATE foods SET display_name = '모듬초밥'     WHERE name = '초밥_모듬';
UPDATE foods SET display_name = '문어초밥'     WHERE name = '초밥_문어';
UPDATE foods SET display_name = '새우초밥'     WHERE name = '초밥_새우';
UPDATE foods SET display_name = '유부초밥'     WHERE name = '초밥_유부초밥';
UPDATE foods SET display_name = '장어초밥'     WHERE name = '초밥_장어';
UPDATE foods SET display_name = '한치초밥'     WHERE name = '초밥_한치';

-- 국밥
UPDATE foods SET display_name = '돼지고기국밥' WHERE name = '국밥_돼지고기';
UPDATE foods SET display_name = '콩나물국밥'   WHERE name = '국밥_콩나물';
UPDATE foods SET display_name = '순대국밥'     WHERE name = '국밥_순대국밥';
UPDATE foods SET display_name = '굴국밥'       WHERE name = '국밥_굴';
UPDATE foods SET display_name = '돼지머리국밥' WHERE name = '국밥_돼지머리';
UPDATE foods SET display_name = '소고기국밥'   WHERE name = '국밥_소고기';

-- 국수
UPDATE foods SET display_name = '김치말이국수' WHERE name = '국수_김치말이국수';
UPDATE foods SET display_name = '열무국수'     WHERE name = '국수_열무김치';
UPDATE foods SET display_name = '막국수'       WHERE name = '국수_막국수';
UPDATE foods SET display_name = '비빔국수'     WHERE name = '국수_비빔국수';
UPDATE foods SET display_name = '잔치국수'     WHERE name = '국수_잔치국수';
UPDATE foods SET display_name = '쟁반막국수'   WHERE name = '국수_쟁반막국수';

-- 김밥
UPDATE foods SET display_name = '소고기김밥'   WHERE name = '김밥_소고기';
UPDATE foods SET display_name = '샐러드김밥'   WHERE name = '김밥_샐러드';
UPDATE foods SET display_name = '계란김밥'     WHERE name = '김밥_계란';
UPDATE foods SET display_name = '고추김밥'     WHERE name = '김밥_고추';
UPDATE foods SET display_name = '김치김밥'     WHERE name = '김밥_김치';
UPDATE foods SET display_name = '날치알김밥'   WHERE name = '김밥_날치알';
UPDATE foods SET display_name = '돈가스김밥'   WHERE name = '김밥_돈가스';
UPDATE foods SET display_name = '멸치고추김밥' WHERE name = '김밥_멸치_고추';
UPDATE foods SET display_name = '샐러리김밥'   WHERE name = '김밥_샐러리';
UPDATE foods SET display_name = '채소김밥'     WHERE name = '김밥_채소';
UPDATE foods SET display_name = '치즈김밥'     WHERE name = '김밥_치즈';
UPDATE foods SET display_name = '풋고추김밥'   WHERE name = '김밥_풋고추';
UPDATE foods SET display_name = '참치김밥'     WHERE name = '김밥_참치';
UPDATE foods SET display_name = '참치고추김밥' WHERE name = '김밥_참치_고추';
UPDATE foods SET display_name = '참치김치김밥' WHERE name = '김밥_참치_김치';

-- 덮밥
UPDATE foods SET display_name = '제육덮밥'     WHERE name = '덮밥_돼지고기(제육)';
UPDATE foods SET display_name = '해물덮밥'     WHERE name = '덮밥_해물';
UPDATE foods SET display_name = '낙지덮밥'     WHERE name = '덮밥_낙지';
UPDATE foods SET display_name = '닭고기덮밥'   WHERE name = '덮밥_닭고기';
UPDATE foods SET display_name = '송이버섯덮밥' WHERE name = '덮밥_송이버섯';
UPDATE foods SET display_name = '유산슬덮밥'   WHERE name = '덮밥_유산슬';
UPDATE foods SET display_name = '장어덮밥'     WHERE name = '덮밥_장어';
UPDATE foods SET display_name = '참치덮밥'     WHERE name = '덮밥_참치';
UPDATE foods SET display_name = '불고기덮밥'   WHERE name = '덮밥_불고기';
UPDATE foods SET display_name = '오징어덮밥'   WHERE name = '덮밥_오징어';

-- 라면 (자연스러운 도치만 — 라면_국물/면/라면만/용기면 계열은 V15 기본값 유지)
UPDATE foods SET display_name = '김치라면'     WHERE name = '라면_김치';
UPDATE foods SET display_name = '계란라면'     WHERE name = '라면_달걀';
UPDATE foods SET display_name = '떡라면'       WHERE name = '라면_떡';
UPDATE foods SET display_name = '만두라면'     WHERE name = '라면_만두';
UPDATE foods SET display_name = '모듬라면'     WHERE name = '라면_모듬';
UPDATE foods SET display_name = '비빔라면'     WHERE name = '라면_비빔라면';
UPDATE foods SET display_name = '어묵라면'     WHERE name = '라면_어묵';
UPDATE foods SET display_name = '짜장라면'     WHERE name = '라면_짜장라면';
UPDATE foods SET display_name = '짬뽕라면'     WHERE name = '라면_짬뽕라면';
UPDATE foods SET display_name = '치즈라면'     WHERE name = '라면_치즈';
UPDATE foods SET display_name = '콩나물라면'   WHERE name = '라면_콩나물';

-- 만두
UPDATE foods SET display_name = '고기만두'     WHERE name = '만두_고기만두';
UPDATE foods SET display_name = '군만두'       WHERE name = '만두_군만두';
UPDATE foods SET display_name = '김치만두'     WHERE name = '만두_김치만두';
UPDATE foods SET display_name = '물만두'       WHERE name = '만두_물만두';
