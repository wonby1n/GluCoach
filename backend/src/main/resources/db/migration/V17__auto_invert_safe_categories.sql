-- foods.display_name 자동 도치 — V15 base 추출 + V16 명시 보정 이후 일괄 정제.
-- 화이트리스트 카테고리에 한해 modifier 토큰을 base 앞으로 도치(modifier 안 '_' 제거).
-- V16 에서 명시 보정한 row 는 display_name 이 더 이상 base 와 같지 않으므로 자동 SKIP.
--
-- 예: 김밥_참치_김치 → 참치김치김밥, 초밥_연어 → 연어초밥, 죽_전복 → 전복죽
--
-- 어색 도치 위험이 있는 카테고리(라면/국수/국밥/탕/찌개 등)는 본 마이그레이션에서
-- 제외하고 후속 LLM 정제(V18) 로 처리.

UPDATE foods
SET display_name = REPLACE(SUBSTRING(name FROM POSITION('_' IN name) + 1), '_', '')
                   || SPLIT_PART(name, '_', 1)
WHERE name ~ '^(김밥|초밥|덮밥|만두|비빔밥|볶음밥|죽|떡|튀김|무침|조림|찜|구이|볶음|전)_'
  AND display_name = SPLIT_PART(name, '_', 1);
