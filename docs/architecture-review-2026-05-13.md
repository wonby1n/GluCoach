# Hawon 아키텍처 리뷰 — AI Agent · 알림 · 동시성

작성일: 2026-05-13
리뷰 범위: backend (Spring Boot) · ai (FastAPI) · frontend (Android Compose)
작성 목적: 시연 코드 → 운영 코드 전환 시 우선 손봐야 할 구조적 결함과 그 해결 방향 정리

---

## TL;DR

| 영역 | 평가 | 가장 큰 위험 |
|------|------|-------------|
| AI Agent | 시연 OK / 운영 X | Fire-and-forget HTTP + 비식별 idempotency = **알림 유실·중복 동시 발생** |
| 알림 시스템 | 동작은 하지만 새는 곳 多 | `System.currentTimeMillis().toInt()` ID 충돌 → 알림 덮어쓰기 |
| 동시성 | 인프라는 있는데 안 씀 | 동기 HTTP가 스케줄러·웹 스레드 점유 → 부하 시 카스케이드 |

> 모든 영역에서 **공통 패턴**: "잘 짜둔 구조(AsyncConfig, AlertChannelResolver, agent_pending_triggers)는 있는데, 호출부에서 그 구조를 우회하거나 안전망 없이 쓴다." 인프라 추가보다 **기존 인프라를 일관되게 쓰는** 게 1순위.

---

## 1. AI Agent 시스템

### 1.1 현재 구조

```
[Meal POST]
   ↓ MealRecordService.create()
   ↓ agent_pending_triggers INSERT (scheduled_at = recorded_at + 1min)
   ↓
[AgentTriggerScheduler @Scheduled fixedDelay=60s]
   ↓ findPendingTriggers(now)
   ↓ AgentTriggerDispatcher.dispatch() → HTTP POST /agent/trigger
   ↓ trigger.markDispatched() → save()
   ↓
[FastAPI /agent/trigger]
   ↓ background_tasks.add_task(_run_postmeal_background)
   ↓ 즉시 202 응답
   ↓
[Claude 4.5 agentic loop, max 10 turn]
   ↓ tools: get_glucose, get_meals, get_steps, send_notification, schedule_followup
   ↓
[send_notification 도구]
   ↓ POST /api/agent/notifications → chat_messages INSERT
   ↓ ChatFcmDispatcher.dispatch() → FCM 전송
```

### 1.2 핵심 결함

#### A. **트랜잭션 경계 잘못 그어짐** — `AgentTriggerScheduler:33-35`

```java
dispatcher.dispatch(trigger);    // ① HTTP POST (이미 외부 시스템 변경)
trigger.markDispatched();
triggerRepository.save(trigger); // ② DB 저장
```

①이 성공하고 ②가 실패하면 → 다음 폴링에서 **같은 trigger 재발화** → 알림 중복.
①이 실패하면 ②는 실행 안 됨 → 재시도(좋음). 근데 ①의 부분 성공(HTTP는 2xx인데 백그라운드 태스크 실패)을 알 길이 없음.

#### B. **Idempotency 키 자체가 없음** — `agent_pending_triggers`

`(user_id, trigger_type, reference_id, scheduled_at)`에 UNIQUE 제약 없음. 클라이언트가 `/api/meals` 재시도하면 trigger가 두 개 생긴다.

#### C. **FastAPI BackgroundTasks는 영속성 없음** — `ai/app/api/agent.py:106`

```python
background_tasks.add_task(_run_postmeal_background, ...)
return {"status": "accepted"}
```

컨테이너가 죽으면 큐는 그냥 사라진다. 블루/그린 배포 중에도 in-flight 태스크 유실 가능.

#### D. **시연 잔재 코드가 운영에 박힘**

| 위치 | 코드 | 영향 |
|------|------|------|
| `tools.py:197` | `delay_minutes = 1`로 하드코딩 | Agent가 30분 뒤로 예약해도 1분 뒤에 발화 |
| `AgentNotificationService.java:30` | `DEDUP_WINDOW = Duration.ofMinutes(1)` | 의도는 30분, 댓글에 "시연용" |
| `AgentTriggerScheduler.java:22` | `fixedDelay = 60_000` | 1분 폴링인데 trigger도 1분 뒤 예약 → 최악의 경우 2분 지연 |

#### E. **타임존 명시 누락** — 다른 스케줄러는 `ZoneId.of("Asia/Seoul")` 명시, `AgentTriggerScheduler:23`만 bare `LocalDateTime.now()` → DB·앱 서버 TZ 다르면 9시간 어긋남.

### 1.3 혁신적 해결안

#### **해결안 1: Transactional Outbox로 신뢰성 보장**

현재 fire-and-forget HTTP를 outbox 테이블 기반으로 전환:

```sql
CREATE TABLE agent_dispatch_outbox (
    id BIGINT PRIMARY KEY,
    trigger_id INT NOT NULL,
    idempotency_key UUID NOT NULL UNIQUE,  -- (user_id, trigger_type, reference_id) hash
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,           -- PENDING / DISPATCHED / FAILED / ACK
    attempts INT DEFAULT 0,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    UNIQUE (idempotency_key)
);
```

- **MealRecordService**: meal INSERT + outbox INSERT를 **단일 트랜잭션**으로
- **AgentTriggerScheduler**: outbox에서 PENDING 행을 SELECT FOR UPDATE SKIP LOCKED로 가져와 dispatch, ACK 받으면 DISPATCHED
- **AI 서비스**: 응답에 dispatch_id 반영, 처리 완료 시 콜백 또는 폴링으로 ACK
- **재시도**: exponential backoff (`next_retry_at` 컬럼)

```
시연용 단순 버전: 위 outbox 컬럼만 추가 + SchedulerLoop에서 next_retry_at 체크
운영용 완전 버전: Debezium CDC → Kafka topic으로 outbox 변경 스트리밍
```

#### **해결안 2: AI 서비스에 durable job queue 도입**

`BackgroundTasks` → **APScheduler + Redis** 또는 **Celery + Redis** 또는 (가벼운 선택지로) **arq**:

```python
# 현재 (loss risk)
background_tasks.add_task(_run_postmeal_background, trigger, user_id)

# 개선 (durability)
await arq_redis.enqueue_job("run_postmeal_agent", trigger, user_id,
                            _job_id=f"postmeal:{trigger.reference_id}")  # idempotent
```

`_job_id`를 idempotency key로 쓰면 같은 reference_id의 잡은 중복 큐잉 안 됨.

#### **해결안 3: Idempotency Token 도입**

클라이언트가 `/api/meals` 호출 시 `Idempotency-Key` 헤더(UUID) 강제:

- 첫 요청: 정상 처리 + 키 캐싱 (Redis 5분 TTL)
- 재요청: 캐시 응답 그대로 반환, DB 건드리지 않음

또한 `agent_pending_triggers`에 `idempotency_key UUID UNIQUE` 컬럼 추가.

#### **해결안 4: 시연 잔재 코드 일괄 제거**

운영 전환 체크리스트:

- [ ] `tools.py:197` `delay_minutes = 1` 하드코딩 제거 → agent 인자 그대로 사용
- [ ] `AgentNotificationService.java:30` `Duration.ofMinutes(1)` → `Duration.ofMinutes(30)`, `@Value`로 외부화
- [ ] `AgentTriggerScheduler` fixedDelay → cron `*/15 * * * * *` (15초마다 폴링)로 응답성 ↑
- [ ] 모든 `LocalDateTime.now()` → `LocalDateTime.now(ZoneId.of("Asia/Seoul"))`

---

## 2. 알림 시스템

### 2.1 알림 발화 경로

알림은 **6개 경로**에서 발화 가능:

| # | 경로 | 채널 | 발화 위치 |
|---|------|------|----------|
| ① | 백엔드 → FCM → 클라 표시 | glucose_critical / coaching / report | `FcmService.kt:120/157` |
| ② | 클라이언트 로컬 (BLE 트렌드) | glucoach_alert / glucoach_coach | `GlucoseAlertManager.kt:168` |
| ③ | 클라이언트 식사 알림 (AlarmManager) | meal_reminder | `MealReminderManager.kt:54` (방금 추가) |
| ④ | FCM 답글 액션 | (조용한 응답) | `NotificationActionReceiver.kt` |
| ⑤ | 인앱 스트림 (UI 배너) | (OS 알림 아님) | `_alertStream.tryEmit` |
| ⑥ | TTS 음성 출력 | (OS 알림 아님) | `TtsManager` |

### 2.2 핵심 결함

#### A. **🔴 CRITICAL: 알림 ID 충돌** — `FcmService.kt:89, 149, 157`

```kotlin
val notifId = System.currentTimeMillis().toInt()  // ← ms를 Int로 truncate
```

`System.currentTimeMillis()`는 Long. `.toInt()`로 잘리면 약 **24일마다 ID 0으로 리셋**. 더 심한 건 같은 ms에 두 알림이 도착하면 ID가 동일해 두번째가 첫번째를 **조용히 덮어쓴다**. FLAG_UPDATE_CURRENT까지 같이 쓰니 PendingIntent extras까지 재사용됨 → 알림 탭하면 엉뚱한 메시지 ID로 이동.

**이게 "알림이 자꾸 먹히는" 1순위 원인일 가능성이 매우 높음.**

#### B. **🟠 HIGH: FCM 토큰 로그아웃 시 백엔드 deregister 안 함** — `TokenManager.kt:71-80`

```kotlin
fun clearTokens() {
    val fcmToken = prefs.getString(KEY_FCM_TOKEN, null)
    prefs.edit().clear().apply()
    if (fcmToken != null) prefs.edit().putString(KEY_FCM_TOKEN, fcmToken).apply()
}
```

토큰을 보존만 하고, 백엔드의 `notification_tokens.user_id`는 그대로 User A에 묶여 있음. 같은 기기에서 User B 로그인 → 같은 토큰이 User B에 upsert. 그 후 User A의 알림(이전 trigger)이 발화하면 → **User B 기기로 가서 User A 정보 노출**. 개인정보 사고급 결함.

#### C. **🟠 HIGH: `lastAlertMs` 레이스 컨디션** — `GlucoseAlertManager.kt:153`

```kotlin
lastAlertMs[type] = now    // MutableMap, lock 없음
```

BLE collect가 단일 코루틴이라 현재 안전하지만, FCM emit (`emitFcmAlert`) 등에서 같은 매니저의 다른 상태에 접근하면 깨질 수 있음. 방어적으로 `ConcurrentHashMap`으로 바꿀 것.

#### D. **🟡 MEDIUM: 채널 fragmentation**

| 출처 | 채널 ID |
|------|---------|
| Backend FcmService | `glucose_critical`, `glucose_coaching`, `report_notification` |
| Frontend GlucoseAlertManager | `glucoach_alert`, `glucoach_coach` |
| Frontend FcmService | `glucose_coaching` (백엔드와 같음, 좋음) |
| Frontend MealReminderManager | `meal_reminder` |

`glucoach_*` 두 개는 백엔드에서 아무도 안 씀 → 사용자 알림 설정에 dead 채널이 노출됨. **백엔드 채널 ID를 단일 소스 오브 트루스로 통일**해야 함.

#### E. **🟡 MEDIUM: Doze 모드 안전성** — `MealReminderScheduler.kt:51`

`setExactAndAllowWhileIdle`는 Xiaomi/OPPO 등 일부 OEM에서 **추가 배터리 최적화가 덮는다.** 매일 같은 시각 알림이 시연에선 동작해도 실사용자 폰에서 침묵할 수 있음.

#### F. **🟡 MEDIUM: SharedFlow 버퍼 silent drop**

```kotlin
private val _alertStream = MutableSharedFlow<NotificationItem>(replay = 0, extraBufferCapacity = 16)
```

`tryEmit`는 버퍼 가득 차면 false를 반환하지만 코드에서 확인 안 함. 16칸이 채워질 시나리오 (alert burst, UI 화면 전환 중 collect 멈춤)에서 silent loss.

### 2.3 혁신적 해결안

#### **해결안 1: 알림 ID 생성 정책 통일** — 사실상 최우선

```kotlin
// utils/NotificationIds.kt
object NotificationIds {
    private val counter = AtomicInteger(10_000_000)  // 충돌 회피용 고대역
    fun next(): Int = counter.incrementAndGet()

    // 동일 chat_message에 대한 알림은 같은 ID로 갱신 (덮어쓰기 의도)
    fun forChatMessage(id: Long): Int = (id and 0x7FFFFFFF).toInt()
}
```

원칙:
- **사용자 액션 단위 알림** (식사 리마인더): 고정 ID (이미 잘 되어 있음 — 3001/3002/3003)
- **이벤트 단위 알림** (FCM, BLE alert): `NotificationIds.next()` (충돌 없는 카운터)
- **갱신 가능 알림** (같은 채팅의 상태 변화): `forChatMessage(id)` (의도적 덮어쓰기)

#### **해결안 2: FCM 토큰 라이프사이클 정리**

```kotlin
// AuthRepository.logout()
suspend fun logout() {
    val token = tokenManager.getFcmToken()
    if (token != null) {
        runCatching { userApi.deactivateFcmToken(token) }  // 새 BE 엔드포인트
    }
    tokenManager.clearTokens()
}
```

백엔드: `notification_tokens` 테이블에 `is_active` 컬럼 활용. 로그아웃 시 false. `FcmService`는 활성 토큰만 select. 또 **FCM 전송 시 `UNREGISTERED` / `INVALID_ARGUMENT` 응답 받으면 자동 deactivate**.

#### **해결안 3: 알림 통일 게이트웨이 도입** — 진짜 혁신적 제안

현재 BE → FCM, Client BLE → 로컬 알림 두 경로가 **서로 모름**. 정합성 보장 어려움.

→ **"Notification Source of Truth"를 백엔드의 `chat_messages` 테이블로 통일**:

```
[BLE 측정] → BleManager → GlucoseAlertManager (판단만)
                              ↓
                       POST /api/agent/notifications  ← 백엔드에 등록 요청
                              ↓
                       chat_messages INSERT (dedup 적용)
                              ↓
                       FCM 전송 → 클라가 받아서 표시
```

→ 클라가 **자기 자신에게 보내는 알림도** 백엔드를 경유. 단점은 오프라인 시 지연. 장점은:
- Cross-device 일관성 (사용자가 다른 기기에서도 같은 알림 봄)
- 단일 dedup 윈도우
- 단일 채널 정책

**시연 단계라면**: 클라 GlucoseAlertManager는 그대로 두고 FCM과의 **dedup 키만** 백엔드 chat_message_id로 통일. 클라가 FCM 받으면 "이미 로컬에서 알림 띄운 chat_message_id인지" 캐시 확인.

---

## 3. 동시성 / 병렬 처리

### 3.1 핵심 결함

#### A. **🔴 CRITICAL: WeeklyReportService가 스케줄러 스레드를 180초 점유** — `WeeklyReportService.java:74`

```java
@Scheduled(...)
@Transactional
public void generateForUser(...) {
    var result = aiClient.generate(...);  // readTimeout=180000ms, 동기
    ...
}
```

스케줄러 풀이 작아서 (디폴트 1개) 보고서 1건 처리 중 다른 스케줄러 잡(예: agent trigger 폴링)이 **밀린다**. 게다가 `@Transactional`을 180초 들고 있으면 connection 점유.

#### B. **🔴 CRITICAL: FCM 전송이 직렬 for-loop** — `FcmService.java:52-72`

```java
for (String token : tokens) {
    FirebaseMessaging.getInstance().send(message);  // 동기 RPC, ~500ms
}
```

토큰 3개면 1.5초 블록. 트랜잭션 안에 있으면 connection 1.5초 점유. 100명 동시 발화 시 catastrophic.

#### C. **🟠 HIGH: AI Agent 동기 HTTP** — `AiAgentCommandClient.java`

`@Async`만 붙이고 `@Async("asyncExecutor")` 지정 안 함 → 디폴트 `SimpleAsyncTaskExecutor` (무제한 스레드 생성). 부하 시 메모리 폭발.

#### D. **🟠 HIGH: FastAPI food/detect 엔드포인트가 event loop 점유** — `ai/app/api/food.py:51`

```python
async def detect_food(file):
    detections = get_predictor().predict(image, top_k=...)  # YOLO + EfficientNet, 동기
```

PyTorch inference는 GIL 안 푸는 동안 이벤트 루프 정지. 두번째 요청은 첫번째 완료까지 대기. 이미지 인식이 동시 1개씩만 처리됨.

#### E. **🟠 HIGH: `MainActivity.startSamsungHealthPolling` 60초 루프** — `MainActivity.kt:198`

`lifecycleScope.launch { while (isActive) { ... delay(60s) ... } }` — `repeatOnLifecycle(STARTED)`로 감쌌지만 네트워크 호출이 `Dispatchers.Main`에서 돌고 있음. UI 멈출 위험. WorkManager로 빼야 함.

#### F. **🟡 MEDIUM: `GlucoseAlertManager` SupervisorJob 누수** — `GlucoseAlertManager.kt:42`

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

`@Singleton`인데 cancellation 없음. ProcessLifecycleOwner 기반으로 바꿔야 함.

#### G. **🟡 MEDIUM: HikariCP 디폴트 풀 크기 (10)**

`application.yml`에 `spring.datasource.hikari.maximum-pool-size` 미설정 → 디폴트 10. Tomcat 스레드 200 vs DB 커넥션 10 = 20:1 비율. 부하 시 카스케이드.

#### H. **🟡 MEDIUM: Python `time.sleep` in async context** — `fallback.py:54`, `llm_caller.py:54,62`

`async def` 안에서 `time.sleep(1)` → 이벤트 루프 1초 정지. `await asyncio.sleep(1)`로.

### 3.2 혁신적 해결안

#### **해결안 1: Java 21 Virtual Threads ON** — 한 줄로 가장 큰 효과

`application.yml`:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Tomcat이 가상 스레드를 쓰면 동기 I/O가 더 이상 OS 스레드를 점유하지 않음. WeeklyReportService의 180초 블록도 가상 스레드라 비용 없음 (단 connection 점유 문제는 여전히 남음). 마이그레이션 비용 거의 0에 가까운 큰 개선.

> 단 `synchronized` 블록 안에서 blocking I/O 호출하면 **pinning** 발생해 효과 무력화. 코드베이스에서 `synchronized` 안에 I/O 있는지 점검 필요.

#### **해결안 2: FCM 멀티캐스트 + 비동기**

```java
@Async("fcmExecutor")
public CompletableFuture<List<SendResult>> sendToTokensAsync(...) {
    MulticastMessage message = MulticastMessage.builder()
        .addAllTokens(tokens)  // ← 한 번의 API call로 최대 500개
        .setNotification(...)
        .build();
    return CompletableFuture.completedFuture(
        FirebaseMessaging.getInstance().sendEachForMulticast(message)
            .getResponses()
    );
}
```

`sendEachForMulticast`는 Firebase가 자체적으로 병렬 처리. 3개 토큰 1.5초 → 200ms 수준. `fcmExecutor`는 별도 ThreadPool로 격리.

#### **해결안 3: FastAPI 모델 inference offload**

```python
import asyncio

@router.post("/detect")
async def detect_food(file: UploadFile):
    contents = await file.read()
    image = Image.open(io.BytesIO(contents)).convert("RGB")
    detections = await asyncio.to_thread(
        get_predictor().predict, image, top_k=settings.model_top_k
    )
    return DetectResponse(count=len(detections), detections=detections)
```

추가로 `Semaphore`로 동시 inference 수 제한 (메모리 보호):

```python
_inference_sem = asyncio.Semaphore(2)  # GPU/메모리 보호

async def _predict_guarded(image):
    async with _inference_sem:
        return await asyncio.to_thread(predictor.predict, image)
```

#### **해결안 4: HikariCP 풀 명시 + connection leak 감지**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30
      minimum-idle: 10
      connection-timeout: 5000
      idle-timeout: 300000
      max-lifetime: 1800000
      leak-detection-threshold: 60000  # 60초 이상 잡고 있으면 경고
```

#### **해결안 5: AsyncConfig 강제 적용**

```java
// AsyncConfig.java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        throw new IllegalStateException("Use named executor — never default SimpleAsyncTaskExecutor");
    }
}
```

→ `@Async` 단독 사용 시 빌드 안 됨. 강제로 `@Async("asyncExecutor")` 같이 명시하게 만듦.

---

## 4. 우선순위 로드맵

리뷰 결과를 P0/P1/P2로 정리:

### P0 — 1~2일 안에 (운영 전 필수)

| # | 작업 | 영역 | 효과 |
|---|------|------|------|
| 1 | `FcmService.kt`의 `currentTimeMillis().toInt()` 알림 ID 통일 | 알림 | 알림 유실 즉시 차단 |
| 2 | FCM 토큰 로그아웃 시 백엔드 deactivate API 추가 | 알림 | 개인정보 사고 방지 |
| 3 | 시연용 1분 dedup → 30분 복원 (`AgentNotificationService:30`) | Agent | 중복 알림 감소 |
| 4 | `tools.py:197` 하드코딩 `delay_minutes=1` 제거 | Agent | followup 스케줄 정상화 |
| 5 | `AgentTriggerScheduler` `LocalDateTime.now()` → KST 명시 | Agent | TZ 어긋남 방지 |
| 6 | `AiAgentCommandClient` `@Async` → `@Async("asyncExecutor")` | 동시성 | 스레드 폭발 차단 |

### P1 — 1주 안에

| # | 작업 | 영역 |
|---|------|------|
| 7 | `agent_pending_triggers` UNIQUE 제약 + idempotency_key 컬럼 추가 | Agent |
| 8 | Java 21 virtual threads 활성화 | 동시성 |
| 9 | FastAPI `/food/detect`를 `asyncio.to_thread`로 offload | 동시성 |
| 10 | `FcmService` 멀티캐스트 + `@Async` 분리 | 동시성 |
| 11 | HikariCP 풀 명시 + leak 감지 | 동시성 |
| 12 | 채널 통일 (백엔드 3개 채널을 단일 소스로) | 알림 |
| 13 | `GlucoseAlertManager` ProcessLifecycleOwner 기반으로 리팩터 | 동시성 |

### P2 — 운영 안정화 단계

| # | 작업 | 영역 |
|---|------|------|
| 14 | Transactional Outbox 도입 | Agent |
| 15 | AI 서비스에 arq/Celery durable queue | Agent |
| 16 | Idempotency-Key 헤더 강제 (POST /api/meals 등) | Agent + API |
| 17 | 알림 single-source-of-truth 게이트웨이 (`chat_messages` 통일) | 알림 |
| 18 | `MainActivity.startSamsungHealthPolling` → WorkManager 이전 | 동시성 |

---

## 5. 마이그레이션 노트

### Outbox 도입 시 단계적 전환

1. **Phase 1**: 새 `agent_dispatch_outbox` 테이블만 추가. 기존 코드 변경 없음.
2. **Phase 2**: MealRecordService에서 outbox INSERT를 trigger INSERT와 같은 트랜잭션에 추가 (dual-write).
3. **Phase 3**: 새 Scheduler가 outbox를 폴링. 기존 Scheduler는 trigger 폴링 그대로.
4. **Phase 4**: 검증 끝나면 기존 Scheduler 제거 + `agent_pending_triggers` deprecate.

### Virtual Threads 도입 시 점검할 곳

`synchronized` 블록 안에 I/O 있는 곳 찾아서 `ReentrantLock`으로 치환:

```bash
# 검색 명령 (참고용)
grep -rn "synchronized" backend/src/main --include="*.java"
```

특히 `GlucoseAlertManager.kt:87`의 `synchronized(recentReadings)` 같은 패턴이 백엔드에도 있을 수 있음.

---

## 부록 A. 발견된 시연 잔재 코드 전체 목록

| 위치 | 코드 | 이유 |
|------|------|------|
| `tools.py:197` | `delay_minutes = 1` | 시연용 |
| `AgentNotificationService.java:30` | `Duration.ofMinutes(1)` (코멘트: "시연용") | 시연용 |
| `AgentTriggerScheduler.java:22` | `fixedDelay = 60_000` | 짧을수록 시연 즉시성↑ |
| `MainActivity.kt:251` (걸음수) | `1000 + currentTime % 5000` fake 값 | 워치 파이프 검증용 |

---

## 부록 B. 참고한 코드 위치 (빠른 점프용)

**Agent**:
- `backend/src/main/java/com/ssafy/s309/domain/agent/scheduler/AgentTriggerScheduler.java`
- `backend/src/main/java/com/ssafy/s309/domain/agent/service/AgentTriggerDispatcher.java`
- `backend/src/main/java/com/ssafy/s309/domain/agent/service/AgentNotificationService.java`
- `backend/src/main/java/com/ssafy/s309/domain/meal/service/MealRecordService.java`
- `ai/app/api/agent.py`
- `ai/app/agent/postmeal_agent_run.py`
- `ai/app/agent/tools.py`
- `ai/app/agent/fallback.py`

**알림**:
- `frontend/app/src/main/java/com/ssafy/s309/fcm/FcmService.kt`
- `frontend/app/src/main/java/com/ssafy/s309/notification/GlucoseAlertManager.kt`
- `frontend/app/src/main/java/com/ssafy/s309/data/local/TokenManager.kt`
- `backend/src/main/java/com/ssafy/s309/common/service/FcmService.java`
- `backend/src/main/java/com/ssafy/s309/domain/chat/service/ChatFcmDispatcher.java`

**동시성**:
- `backend/src/main/java/com/ssafy/s309/config/AsyncConfig.java`
- `backend/src/main/java/com/ssafy/s309/config/AiClientConfig.java`
- `backend/src/main/java/com/ssafy/s309/domain/weekly_report/service/WeeklyReportService.java`
- `ai/app/api/food.py`
- `frontend/app/src/main/java/com/ssafy/s309/MainActivity.kt`
