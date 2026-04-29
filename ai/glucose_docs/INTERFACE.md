# 혈당 예측 API — 백엔드 통합 가이드

> AI 서버 (`{AI_SERVICE_URL}`) 의 혈당 예측 엔드포인트.
> Jira: S14P31S309-278

## 엔드포인트 요약

| Method | Path | 역할 | 호출 시점 |
|---|---|---|---|
| POST | `/api/predict/glucose/meal` | Model 1 — 식사 시점 → 식후 120분 BG | 사용자가 음식 선택 시 |
| POST | `/api/predict/glucose/now`  | Model 2 — 현재 시점 → 향후 120분 BG | 식사 외 시점 (한계 있음, 아래 참고) |
| GET  | `/api/predict/glucose/health` | 모델 로드 상태 + GPU 가용성 | 헬스체크 |

**공통**: 응답의 `predicted` 는 항상 24개 (5분 간격, 5~120분). 곡선 시각화용.

---

## 1) `POST /api/predict/glucose/meal`

### Request

```json
{
  "user_id": "sim_0042",
  "recent_values": [98.5, 102.1, 105.3],
  "meal": {
    "carbs": 80,
    "time_iso": "2026-04-29T12:30:00"
  },
  "user_profile": {
    "fasting_bg": 100.5,
    "weight_kg": 65.0,
    "activity": "low",
    "diabetes_type": "T1D",
    "meal_pattern": "regular_3"
  }
}
```

### 필드

| 필드 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `user_id` | string | required | 사용자 식별자 (개인화 모델 dispatch 용) |
| `recent_values` | float[] | min 1 | 최근 BG 측정값. 가장 마지막 값이 식사 직전 BG (current_glucose) 로 사용됨 |
| `meal.carbs` | float | 0~300 | 탄수화물 g |
| `meal.time_iso` | string | ISO 8601 | 식사 시각 |
| `user_profile.fasting_bg` | float | - | 공복 혈당 mg/dL |
| `user_profile.weight_kg` | float | 0~300 | 체중 |
| `user_profile.activity` | enum | `"low" \| "medium" \| "high"` | 활동 강도 |
| `user_profile.diabetes_type` | enum | `"T1D" \| "T2D" \| "Normal"` | |
| `user_profile.meal_pattern` | enum | 11가지 (아래) | 식사 패턴 |

`meal_pattern` 허용 값:
`regular_3, skip_breakfast, skip_dinner, skip_lunch, skip_breakfast_dinner, skip_breakfast_lunch, skip_lunch_dinner, frequent_small, irregular, late_dinner, fasting_day`

### Response (200)

```json
{
  "horizons_min": [5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, 105, 110, 115, 120],
  "predicted": [108.3, 115.7, 124.9, 138.1, 152.4, 165.0, 172.8, 175.3, 173.0, 168.2, 161.5, 155.0, 149.4, 144.6, 140.5, 137.0, 134.1, 131.7, 129.6, 127.8, 126.3, 125.0, 123.9, 123.0],
  "confidence": 0.85,
  "mode": "base"
}
```

| 필드 | 설명 |
|---|---|
| `horizons_min` | 예측 시점 (분). 항상 `[5, 10, ..., 120]` 고정 |
| `predicted` | 24개 BG 값 mg/dL (raw). `horizons_min[i]` 분 후 예측값 |
| `confidence` | 임시 0.85 고정 (추후 model uncertainty) |
| `mode` | `"base"` 또는 `"personalized"` (해당 user_id 의 개인화 모델 존재 시) |

---

## 2) `POST /api/predict/glucose/now`

> ⚠️ **한계 명시**: 시뮬레이터의 식사 사이 BG 동역학이 단순(basal 평형 + 인슐린 잔효)해서 본 모델 정확도는 식사 시점 모델보다 낮습니다. 실제 환자의 운동/스트레스/dawn phenomenon 등은 반영되지 않습니다.

### Request

```json
{
  "user_id": "sim_0042",
  "recent_values": [98.5, 99.1, 100.3, 100.8, 101.5, 102.1, 102.4, 102.0, 101.7, 101.2, 100.9, 100.5],
  "user_profile": {
    "fasting_bg": 100.5,
    "weight_kg": 65.0,
    "activity": "low",
    "diabetes_type": "T1D"
  }
}
```

### 필드 차이

| 필드 | 차이 |
|---|---|
| `recent_values` | **최소 12개** (60분치, 5분 간격). 12개보다 많으면 마지막 12개 사용 |
| `user_profile` | `meal_pattern` **없음** (식사 안 함 가정) |
| 그 외 | `meal` 객체 없음 |

### Response (200)

`/meal` 과 **동일한 schema**.

---

## 3) `GET /api/predict/glucose/health`

### Response (200)

```json
{
  "status": "UP",
  "meal_model_loaded": true,
  "now_model_loaded": true,
  "scaler_loaded": true,
  "cuda_available": false
}
```

| 필드 | 의미 |
|---|---|
| `status` | `"UP"` (모두 OK) / `"DEGRADED"` (일부 미로드, dummy 응답 동작) / `"DOWN"` |
| `meal_model_loaded` | Model 1 가중치 로드 가능? |
| `now_model_loaded` | Model 2 가중치 로드 가능? |
| `scaler_loaded` | `models/scaler.pkl` 존재? |
| `cuda_available` | GPU 사용 가능? |

> 학습 전 단계에서는 `status: "DEGRADED"` 가 정상. 더미 응답으로 동작.

---

## 에러 응답

| HTTP | 상황 | body |
|---|---|---|
| 400 | 입력값 정합성 위반 (예: `recent_values` 가 12개 미만인데 `/now` 호출) | `{"detail": "<message>"}` |
| 422 | Pydantic 검증 실패 (잘못된 enum, 음수 carbs 등) | FastAPI 기본 형식 |
| 500 | 내부 에러 (모델 로드 실패 등) | `{"detail": "internal error"}` (안전한 메시지만) |

---

## cURL 예시

### Model 1
```bash
curl -X POST http://localhost:8000/api/predict/glucose/meal \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "test",
    "recent_values": [105.0],
    "meal": {"carbs": 60, "time_iso": "2026-04-29T12:30:00"},
    "user_profile": {
      "fasting_bg": 100, "weight_kg": 65,
      "activity": "low", "diabetes_type": "T1D", "meal_pattern": "regular_3"
    }
  }'
```

### Model 2
```bash
curl -X POST http://localhost:8000/api/predict/glucose/now \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "test",
    "recent_values": [98,99,100,101,102,102,103,103,102,101,100,99],
    "user_profile": {
      "fasting_bg": 100, "weight_kg": 65, "activity": "low", "diabetes_type": "T1D"
    }
  }'
```

### Health
```bash
curl http://localhost:8000/api/predict/glucose/health
```

---

## 통합 체크리스트 (백엔드 팀)

- [ ] `recent_values` 가 시계열로 정렬되어 있나? (오래된 → 최근 순)
- [ ] `time_iso` 가 timezone-aware? (서버는 naive 로 처리하므로 KST 가정)
- [ ] `meal_pattern` 값이 11가지 enum 안에 포함되는지 클라이언트 측에서 검증
- [ ] `recent_values` 길이 부족 시 `/now` 호출 안 하기 (또는 `/meal` 로 polyfill)
- [ ] AI 서버가 `DEGRADED` 인 경우 응답이 dummy 값임을 인지 (학습 완료 전까지)

---

## 변경 이력

| 날짜 | 변경 |
|---|---|
| 2026-04-29 | 초기 작성. prefix `/api/predict/glucose/{meal,now}` 분리 (백엔드 팀 표 `/api/predict/glucose` 단일 → 합의 후 분리) |
