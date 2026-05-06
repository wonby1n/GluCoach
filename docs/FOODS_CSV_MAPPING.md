# 공공데이터포털 CSV ↔ `foods` 컬럼 매핑 명세

> 관련 이슈: [S14P31S309-1064], [S14P31S309-1065], [S14P31S309-1066]
> 최종 수정: 2026-05-05
> 적용 범위: BE — 식약처 식품영양성분 표준데이터 적재

## 배경

식약처(공공데이터포털 제공기관코드 `1471000`) 의 **전국통합식품영양성분정보_음식_표준데이터** CSV를 운영 DB의 `foods` 테이블에 1회 적재한다. CSV는 52개 컬럼 한글 헤더로 제공되고, `foods` 스키마는 19개 컬럼이라 **명시적 매핑이 필요**하다. 본 문서는 그 매핑 규칙과 변환 로직을 기록한다.

## CSV 출처 정보

| 항목 | 값 |
|---|---|
| 데이터셋명 | 전국통합식품영양성분정보_음식_표준데이터 |
| 제공기관 | 식품의약품안전처 (제공기관코드 1471000) |
| 다운로드 시점 기준일 | 2026-05-04 |
| 파일 인코딩 | UTF-8 (BOM 없음) |
| 총 row 수 | 19,495 (헤더 제외) |
| 영양 기준량 | 100g (CSV `영양성분함량기준량` 컬럼, 사실상 전 row 동일) |

## 컬럼 매핑

### 직접 매핑

CSV 컬럼 값을 `foods`의 컬럼으로 그대로 옮긴다.

| CSV 컬럼 (한글) | `foods` 컬럼 | 타입 | 비고 |
|---|---|---|---|
| 식품코드 | `food_api_id` | VARCHAR(64) UNIQUE | 예: `D302-053000000-0001`. 외부 API 캐시 키로 사용 |
| 식품명 | `name` | VARCHAR(100) NOT NULL | |
| 식품대분류명 | `category` | VARCHAR(50) | 예: `빵 및 과자류`, `찜류` |
| 에너지(kcal) | `kcal` | NUMERIC(6,2) | |
| 탄수화물(g) | `carbs_g` | NUMERIC(5,2) | |
| 당류(g) | `sugar_g` | NUMERIC(5,2) | |
| 단백질(g) | `protein_g` | NUMERIC(5,2) | |
| 지방(g) | `fat_g` | NUMERIC(5,2) | |
| 식이섬유(g) | `fiber_g` | NUMERIC(5,2) | |
| 포화지방산(g) | `saturated_fat_g` | NUMERIC(5,2) | |
| 트랜스지방산(g) | `trans_fat_g` | NUMERIC(5,2) | |
| 콜레스테롤(mg) | `cholesterol_mg` | NUMERIC(7,2) | V7로 widen ([HEALTH_SCHEMA_CHANGE 참고 패턴](HEALTH_SCHEMA_CHANGE.md)) |
| 나트륨(mg) | `sodium_mg` | NUMERIC(7,2) | V7로 widen |

### 변환 매핑

CSV 값을 가공해서 넣는다.

| CSV 컬럼 | `foods` 컬럼 | 타입 | 변환 로직 |
|---|---|---|---|
| 영양성분함량기준량 | `serving_size` | NUMERIC(7,2) | `regexp_replace(value, '[^0-9.]', '', 'g')::numeric`. 모든 row가 `100g` 또는 `100ml` → 100. 단위(g/ml) 보존은 ENUM 미도입 합의에 따라 생략, 숫자만 보존 |

### 빈 문자열 처리

식약처 CSV는 결측값을 빈 문자열(`,,`)로 표기한다. 모든 NUMERIC 컬럼은 적재 시 `NULLIF(value, '')`로 NULL 변환 후 캐스팅한다.

```sql
NULLIF(carbs_g_txt, '')::numeric
```

### 기본값 / 자동 채움

CSV에 대응 컬럼이 없는 항목은 DEFAULT 또는 INSERT 시 명시.

| `foods` 컬럼 | 값 | 사유 |
|---|---|---|
| `id` | IDENTITY 자동 | 시퀀스 |
| `is_customized` | `false` | 식약처 표준 데이터는 사용자 커스텀이 아님 |
| `search_count` | `0` | 검색 누적 카운터 초기값 |
| `cached_at` | `CURRENT_TIMESTAMP` | 적재 시점 |
| `created_at` | DEFAULT CURRENT_TIMESTAMP | |
| `updated_at` | DEFAULT CURRENT_TIMESTAMP | |

## 미사용 CSV 컬럼

매핑하지 않는 39개 컬럼. 향후 확장 시 참고용.

| 컬럼 | 미사용 사유 |
|---|---|
| 데이터구분코드/명 | `D=음식` 외 가공식품/원재료가 섞여 있으나 본 적재는 음식만 사용 (필터 조건) |
| 식품기원코드/명 | `외식(분석함량)` 등 출처 분류, UI 표시 불필요 |
| 식품대분류코드 | 한글명만 사용 |
| 대표식품코드/명 | 검색 단위가 식품명이라 대표명 불필요 |
| 식품중분류/소분류/세분류 코드/명 | 8개 컬럼. 향후 카테고리 트리 기능 추가 시 도입 검토 |
| 수분, 회분 | 혈당 예측에 영향 미미, AI 입력 미사용 |
| 칼슘, 철, 인, 칼륨 (mg) | 미네랄 4종. 1차 출시 범위 외 |
| 비타민 A, 레티놀, 베타카로틴, 티아민, 리보플라빈, 니아신, 비타민 C, 비타민 D | 비타민 8종. 1차 출시 범위 외 |
| 출처코드/명 | 모두 식품의약품안전처. 적재 시점에 메타로 보존하지 않음 |
| 1인(회)분량 참고량 | 90% 이상 빈 값. 1차 출시는 사용 안 함 |
| 식품중량 | 포장/판매 단위 중량 (예: 컵라면 1봉지 80g, 음료 캔 591ml). 1회 섭취 분량과 의미가 달라 매핑 제외. 1회 분량은 프론트 사용자 입력으로 받는 방향 |
| 업체명 | 외식/가공품 제조사 정보, UI 미노출 |
| 데이터생성방법코드/명, 데이터생성일자, 데이터기준일자 | 메타데이터, 운영 무관 |
| 제공기관코드/명 | 모두 식약처 (1471000), 단일값 |

## 변환 한계

### `serving_size` 단위 정보 손실
영양성분함량기준량은 `100g` 또는 `100ml` 두 형태로 들어오는데, `serving_size`는 NUMERIC 단일 컬럼이라 단위(g/ml) 정보가 사라진다. 팀 합의로 ENUM/별도 unit 컬럼은 도입하지 않았고, 영양소가 100g≈100ml 기준이라는 점을 사용자에게 UI 텍스트로 안내하는 방향.

→ 영양 계산이 g/ml 차이에 민감해지는 시점에 `serving_unit VARCHAR(8)` 컬럼 추가를 검토.

### 동일 식품명, 다른 식품코드
식약처는 출처/지역/조리법별로 같은 음식을 별개 row로 관리한다. 예:
- `D302-053000000-0001 | 가래떡 | 210kcal`
- `D102-053000000-0001 | 가래떡 | 195kcal`

→ `food_api_id` UNIQUE 제약으로 다 들어가고, `name` 검색 시 여러 결과가 나온다 ([FoodRepository.findTop20ByNameContainingIgnoreCase...](../backend/src/main/java/com/ssafy/s309/domain/food/repository/FoodRepository.java) 가 영양소 평균값을 보여주지 않고 row 단위로 노출). 정상 동작.

### 정규식 가정
정규식 `[^0-9.]` 제거 방식은 단순 형식 (`100g`, `100ml`)에는 안전하지만 복합 형식 (`1개(150g)` → `1150`) 이 들어오면 잘못된 값이 된다. `영양성분함량기준량`은 현재 데이터셋에서 100% `100g`/`100ml` 형식이라 안전하지만, 향후 데이터셋 갱신 시 형식 분포를 재검증 필요.

## 스키마 영향 — V7 마이그레이션

CSV 적재 과정에서 기존 `NUMERIC(5,2)`가 식약처 실데이터 범위를 못 담는 컬럼 3개를 발견하여 [V7__widen_foods_nutrient_columns.sql](../backend/src/main/resources/db/migration/V7__widen_foods_nutrient_columns.sql) 로 widen.

| 컬럼 | Before | After | 실측 max | 비고 |
|---|---|---|---|---|
| `sodium_mg` | NUMERIC(5,2) | NUMERIC(7,2) | 7,518 mg | 라면, 김치 등 |
| `cholesterol_mg` | NUMERIC(5,2) | NUMERIC(7,2) | 870 mg | 5,2 안에 들어오나 안전 마진 확보 |
| `serving_size` | NUMERIC(5,2) | NUMERIC(7,2) | 100 (현재 매핑 기준) | 매핑 시정 후엔 기존 5,2로도 충분하나, 향후 1회 분량 컬럼 도입 시 큰 값이 들어올 수 있어 그대로 유지 |

대응되는 `Food` 엔티티의 `@Column(precision = ...)` 도 함께 7로 변경.

## 적재 절차 (운영 EC2 기준)

상세 명령은 본 문서 범위 외 (이슈 #1066에 정리). 요약만:

1. CSV 업로드 (`scp` → EC2 → `docker cp` → 컨테이너 `/tmp/foods.csv`)
2. staging 테이블 생성 (52개 TEXT 컬럼)
3. `\copy` 로 CSV → staging
4. `INSERT ... SELECT` 로 staging → `foods` (본 문서 매핑 규칙 적용)
5. 검증 — row 수, `food_api_id` 매핑률 100%, 백엔드 검색 API 동작 확인
6. staging 테이블 DROP

## 검증 결과 (2026-05-05 적재 기준)

| 항목 | 값 |
|---|---|
| 적재 row 수 | 19,495 |
| `food_api_id` 채워진 row | 19,495 (100%) |
| `food_api_id` UNIQUE 충돌 (ON CONFLICT 스킵) | 0 |
| `kcal` NULL row | 0 |
| `sodium_mg` NULL row | 61 (0.3%) — 식약처 결측 |
| `cholesterol_mg` NULL row | 13,461 (69.0%) — 식물성/가공식품은 콜레스테롤 미측정이 표준 |
| `serving_size` 분포 | min=100, max=100 (영양성분함량기준량 매핑 결과) |
| 검색 API 동작 | ✅ `GET /api/foods/search?q=가래떡` → 3건 응답 |

## 관련 문서 / 코드

- [Food 엔티티](../backend/src/main/java/com/ssafy/s309/domain/food/entity/Food.java)
- [V1 초기 스키마](../backend/src/main/resources/db/migration/V1__init_schema.sql) — `foods` 테이블 정의
- [V7 widen 마이그레이션](../backend/src/main/resources/db/migration/V7__widen_foods_nutrient_columns.sql)
- [FoodApiClientImpl](../backend/src/main/java/com/ssafy/s309/domain/food/client/FoodApiClientImpl.java) — 외부 API 캐시 시 `food_api_id` 사용처
- 이슈 [S14P31S309-1064] — 본 매핑 명세 작성
- 이슈 [S14P31S309-1066] — 운영 1회 적재 실행 및 검증
