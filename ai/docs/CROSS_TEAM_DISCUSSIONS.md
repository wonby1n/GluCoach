# 타 파트 논의 필요 사항

> AI 서버 단독으로 결정할 수 없어 백엔드/프론트와 합의가 필요한 항목.

---

## 1. 개인화 rejected 후 재시도 정책

**내용**: rejected 시 기존 개인화 모델 유지 + 동일 기간(14일) 후 재시도

- AI 서버: rejected 시 기존 개인화 모델 삭제하지 않음 (이미 구현됨)
- 백엔드: rejected 응답 수신 시 14일 후 `/personalize` 재호출 스케줄 등록

---

### 백엔드 구현 가이드

#### 현재 상태
- `GlucosePredictClientImpl.java` — `/inference/glucose` 호출만 구현, `/personalize` 미구현
- `PredictionService.java:92` — `modelType` 하드코딩 `"generic"`
- DB: `cgm_patterns.personalized_at` 컬럼 존재, 스케줄러 미구현

#### 추가 구현 필요 항목

**① `/personalize` 클라이언트 메서드 추가**

`GlucosePredictClientImpl.java`에 아래 메서드 추가:

```java
public PersonalizeResponse personalize(String userId, UserProfile profile, List<MealHistory> history) {
    return restClient.post()
        .uri("/api/predict/glucose/personalize")
        .body(new PersonalizeRequest(userId, profile, history))
        .retrieve()
        .body(PersonalizeResponse.class);
}
```

요청 바디 구조는 `ai/glucose_docs/INTERFACE.md` 참고.

**② 14일 경과 감지 스케줄러 추가**

```java
@EnableScheduling  // Application 클래스에 추가
```

```java
@Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시
public void triggerPersonalization() {
    LocalDateTime threshold = LocalDateTime.now().minusDays(14);
    // personalized_at IS NULL OR personalized_at < threshold 인 유저 조회
    List<User> targets = userRepository.findPersonalizationTargets(threshold);
    for (User user : targets) {
        List<MealHistory> history = mealRepository.findByUserId(user.getId());
        PersonalizeResponse res = glucosePredictClient.personalize(user.getId(), user.getProfile(), history);
        if ("personalized".equals(res.getStatus()) || "rejected".equals(res.getStatus())) {
            user.getCgmPattern().setPersonalizedAt(LocalDateTime.now());
            // rejected여도 타임스탬프 갱신 → 14일 후 재시도
        }
    }
}
```

**③ `personalized_at` 업데이트 쿼리 추가**

```sql
-- personalized_at이 null이거나 14일 이상 지난 유저 조회
SELECT u.* FROM users u
JOIN cgm_patterns cp ON cp.user_id = u.id
WHERE cp.personalized_at IS NULL
   OR cp.personalized_at < NOW() - INTERVAL 14 DAY;
```

**④ 예측 호출 시 mode 반영 (선택)**

`PredictionService.java:92`의 `"generic"` 하드코딩을, personalized 모델 존재 여부에 따라 동적으로 변경하려면 `/meal` 응답의 `mode` 필드를 활용:
- AI 서버가 `mode: "personalized"` 반환 시 → 이미 개인화 모델로 예측된 것
- 별도 분기 처리 불필요 (AI 서버가 자동으로 개인화 모델 선택)

---

## 2. 개인화 트리거 기준 통일

**상태**: ❌ 미논의

**배경**:
- Jira 614 태스크에는 "14일 이상 → 개인화" 기준
- INTERFACE.md에는 "식사+실측 30+ 페어 쌓이면" 호출 권장
- AI 서버 코드 최솟값은 10개 (권장 30개+)

**정리**:
- 14일 기준: 백엔드가 `/personalize` 호출할 시점 결정 (시간 기반 프록시)
- 30+ 페어: AI 서버가 의미 있는 fine-tuning을 위해 권장하는 데이터 양

**잠재적 문제**:
14일이 지났어도 30개 미만이면 rejected 가능성 높음 → "14일 후 개인화됩니다" 같은 UI 문구가 실제와 다를 수 있음.

**논의 필요 대상**: 백엔드 팀, 프론트 팀

---

## 3. 노이즈 필터링 레이어 분담

**상태**: ❌ 미논의

**현재 구조**:
- 프론트(Android)에서 BLE 데이터 수신 시 범위 체크로 비정상값 제거
- 백엔드 수신 시 별도 검증 없음
- AI 서버는 BG mg/dL 값을 그대로 받아 사용

**문제**:
프론트 버그 또는 변조된 요청으로 이상값이 DB에 저장되면 AI 학습 데이터까지 오염됨.

**권장**:
백엔드 수신 레이어에서 최소한 범위 체크(40~400 mg/dL) 추가 권장.

**논의 필요 대상**: 백엔드 팀

---

## 4. 개인화 상태 프론트 노출 방식

**상태**: ❌ 미논의

**배경**:
`/meal` 응답의 `mode` 필드가 `"base"` 또는 `"personalized"` 를 반환함.

**논의 필요 사항**:
- 프론트에서 개인화 모드를 사용자에게 어떻게 보여줄지
- rejected 됐을 때 사용자에게 알림을 줄지 말지
- 개인화 진행 중(학습 중) 상태를 표시할 수 있는지 (현재 API는 완료 후 응답)

**논의 필요 대상**: 프론트 팀, 백엔드 팀
