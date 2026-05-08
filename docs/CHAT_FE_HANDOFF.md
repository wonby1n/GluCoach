# 채팅 도메인 FE 통합 명세 (T16~T18)

대상: FE Android 담당
스토리: S14P31S309-1137 (chat 도메인 단일화 + 양방향 채팅)
관련 Jira: 1153(T16) / 1154(T17) / 1155(T18)
브랜치: `be/feature-chat-domain-migration-S14P31S309-1144` (이 브랜치에서 작업 시작)

---

## 1. 한 줄 요약

기존 알림(`/api/v1/alerts`) 도메인이 폐기되고 **채팅(`/api/chat/messages`)**으로 흡수됨.
사용자가 agent에게 명령을 발화할 수 있고, agent 메시지에는 응답 옵션 3개 + 추론 카드가 붙음.
FCM 푸시 클릭 → 채팅 화면 진입 + 자동 일괄 읽음.

---

## 2. API 변경사항

### 2.1 폐기 (404 반환)

| 메서드 | 경로 | 비고 |
|---|---|---|
| GET | `/api/v1/alerts` | `getAlerts()` 호출 위치 정리 필요 |
| PATCH | `/api/v1/alerts/{id}/read` | `markAlertRead()` 호출 위치 정리 필요 |

> 현재 `HealthRepository`에서 `runCatching` + mock fallback으로 silent fail 중 → T18에서 chat API로 교체.

### 2.2 신규 endpoint (모두 JWT Bearer 필요)

| 메서드 | 경로 | 용도 | 응답 |
|---|---|---|---|
| GET | `/api/chat/messages?page=&size=` | 본인 메시지 페이징 | `ChatMessageListResponse` |
| GET | `/api/chat/messages/unread-count` | 안 읽음 카운트 | `{ "unreadCount": N }` |
| POST | `/api/chat/messages/reply` | 옵션 선택 응답 | `ChatMessageItem` (201) |
| POST | `/api/chat/messages/command` | 사용자 명령 발화 | `ChatMessageItem` (201) |
| PATCH | `/api/chat/messages/{id}/read` | 단건 읽음 | 204 |
| POST | `/api/chat/messages/mark-all-read` | 일괄 읽음 | 204 |

### 2.3 FCM data payload 변경

| 키 | 타입 | 비고 |
|---|---|---|
| `alertType` | String | 기존 유지. 값은 `messageType` (`AGENT_MEAL_FOLLOWUP` / `HIGH` / `SOS` 등) |
| `chatMessageId` | String (숫자 문자열) | **신규**. FE가 단건 read 호출 시 사용 |

---

## 3. 데이터 모델

### 3.1 `ChatMessageItem` (GET /messages, POST /reply, POST /command 응답 항목)

```json
{
  "id": 10,
  "sender": "agent",
  "message": "산책 어때요?",
  "messageType": "AGENT_MEAL_FOLLOWUP",
  "commandType": null,
  "displayTrace": {
    "summary": "식후 60분, 활동량 적음",
    "cards": [
      { "type": "glucose", "title": "혈당", "description": "상승 추세입니다" },
      { "type": "steps",   "title": "활동량", "description": "최근 활동량이 적은 상태" }
    ],
    "decision": { "reason": "식후 활동 권유 적절한 시점" }
  },
  "payload": null,
  "options": [
    { "id": "walk_now", "label": "산책 갈게요" },
    { "id": "later_30", "label": "30분 뒤" },
    { "id": "skip",     "label": "패스" }
  ],
  "parentId": null,
  "selectedOptionId": null,
  "isRead": false,
  "resolvedAt": null,
  "createdAt": "2026-05-07T13:00:00"
}
```

| 필드 | 타입 | 의미 | sender별 NULL 여부 |
|---|---|---|---|
| `id` | Long | PK | 항상 |
| `sender` | String | `agent` / `system` / `user` | 항상 |
| `message` | String | 본문 텍스트 | user-command 외 항상 |
| `messageType` | String? | `AGENT_*` / `HIGH` / `LOW` / `SOS` / `WEEKLY_REPORT` | user는 NULL |
| `commandType` | String? | `USER_REQUEST_*` (음식추천 등) | user 명령 발화에서만 |
| `displayTrace` | Object? | AI 추론 카드 | agent에서만 |
| `payload` | Object? | 풍부한 응답 콘텐츠 (음식 카드 등) | agent 응답에서 |
| `options` | Array? | 응답 선택지 0~10개 | agent에서만 |
| `parentId` | Long? | 부모 메시지 id (응답 사이클) | 응답 메시지에만 |
| `selectedOptionId` | String? | user가 선택한 option id | user 옵션 응답에만 |
| `isRead` | Boolean | 읽음 여부 | 항상 |
| `resolvedAt` | DateTime? | 룰 메시지 정상복귀 시점 | 미해결이면 NULL |
| `createdAt` | DateTime | 생성 시각 | 항상 |

### 3.2 `ChatMessageListResponse` (GET /messages)

```json
{
  "content": [ ChatMessageItem, ... ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "unreadCount": 3
}
```

### 3.3 `ChatReplyRequest` (POST /reply)

```json
{ "parentId": 10, "optionId": "walk_now" }
```

- `parentId`: agent 메시지 id (사용자가 응답하는 대상)
- `optionId`: parent의 `options[].id` 중 하나
- 서버가 label을 lookup해서 user 메시지로 INSERT

오류:
- 400: `optionId`가 parent.options에 없음
- 403: parent가 본인 메시지가 아님

### 3.4 `ChatCommandRequest` (POST /command)

```json
{
  "commandType": "USER_REQUEST_FOOD_RECOMMEND",
  "message": "음식 추천해줘",
  "payload": { "category": "한식" }
}
```

- `commandType`: 미리 정의된 명령 식별자 (필수)
- `message`: 화면에 보일 라벨 텍스트 (선택, 빈 값이면 commandType이 그대로)
- `payload`: 명령 파라미터 (선택)

### 3.5 명령 카탈로그 (현재)

| commandType | 의미 | payload 예시 |
|---|---|---|
| `USER_REQUEST_FOOD_RECOMMEND` | 음식 추천 요청 | `{ "category": "한식" }` |
| `USER_REQUEST_GLUCOSE_CHECK` | 혈당 상태 요약 | `{}` |
| `USER_REQUEST_ACTIVITY_TIP` | 운동 팁 | `{}` |

> 새 명령 추가 시 `docs/AGENT_API_SPEC.md` 부록 A에 등재.

---

## 4. T16 — 채팅 화면 UI

### 4.1 말풍선 3종

| sender | 위치 | 스타일 |
|---|---|---|
| `agent` | 좌측 | AI 아바타, 시간, 본문, displayTrace 펼치기, options 버튼 N개 |
| `system` | 중앙 | 시스템 메시지 스타일 (HIGH/LOW/SOS/WEEKLY_REPORT) |
| `user` | 우측 | 단순 텍스트, 보낸 시각 |

### 4.2 agent 메시지 펼치기 (displayTrace)

기본: 본문만 표시.
탭하면 displayTrace가 펼쳐짐:
- summary 1줄
- cards N개 — 각 카드는 type별 아이콘 + title + description
- decision.reason 표시

### 4.3 options 버튼 N개

agent 메시지에 `options`가 있으면 말풍선 아래 가로 배열로 N개(보통 3개) 버튼.
- 클릭 → POST /reply 호출 → 클릭한 라벨이 user 말풍선으로 추가
- `selectedOptionId`가 채워진 메시지는 버튼 비활성화 (이미 응답함)

### 4.4 payload 카드 (선택)

agent 응답에 `payload`가 있으면 별도 카드 UI:
- `payload.items` 등 구조에 맞춰 음식/추천 카드 리스트 표시
- 구조는 명령별로 달라짐 (T15 단계에서 LLM이 자유 형식 생성, FE는 알려진 구조만 그리고 나머지는 무시)

### 4.5 페이징

무한 스크롤(위로 당기기) 또는 페이지 단위.
- 최신순(`createdAt DESC`) 기본
- 첫 진입 시 `page=0, size=20`
- 위로 당기면 `page=1, 2, ...`
- `totalElements` 도달하면 더 이상 fetch 안 함

---

## 5. T17 — 응답 입력 UX

### 5.1 옵션 버튼 클릭

```kotlin
fun onOptionClick(parent: ChatMessage, option: ChatOption) {
    // 1. 낙관적 UI: user 말풍선 즉시 추가
    val optimistic = ChatMessage(sender = "user", message = option.label, parentId = parent.id, ...)
    appendLocal(optimistic)
    
    // 2. POST /reply
    runCatching { api.reply(parent.id, option.id) }
        .onSuccess { saved -> replaceLocal(optimistic, saved) }
        .onFailure { rollbackLocal(optimistic); showToast("응답 실패") }
}
```

### 5.2 명령 버튼 (사용자 자발 발화)

채팅창 하단에 명령 단축 버튼 또는 `+` 메뉴:

```
[ 🍽 음식 추천 ]  [ 📈 혈당 상태 ]  [ 🏃 운동 팁 ]
```

클릭 시:
```kotlin
fun onCommandClick(commandType: String, label: String, payload: Map<String, Any> = emptyMap()) {
    // 1. 낙관적 UI: user 말풍선 즉시 추가
    appendLocal(ChatMessage(sender = "user", message = label, commandType = commandType))
    
    // 2. POST /command
    api.command(ChatCommandRequest(commandType, label, payload))
    
    // 3. agent 응답은 곧 FCM으로 도착 (LLM 호출 2~10초 걸림)
    //    그동안 "응답 생성 중..." 로딩 인디케이터 띄우면 좋음
}
```

### 5.3 자유 텍스트 입력

이번 단계 **불필요**. 모든 사용자 발화는 옵션 또는 명령 버튼.
(자유 텍스트는 추후 확장 시 BE의 user 메시지 CHECK 제약 (b)~(c) 케이스로 흡수 가능, 지금은 활용 X)

---

## 6. T18 — FCM + 안 읽음 배지

### 6.1 채팅 화면 visibility 추적

```kotlin
class ChatActivity {
    companion object { var isVisible: Boolean = false }
    
    override fun onResume() {
        super.onResume()
        isVisible = true
        chatRepo.markAllRead()  // POST /mark-all-read (204)
        chatRepo.refresh()      // GET /messages
    }
    
    override fun onPause() {
        super.onPause()
        isVisible = false
    }
}
```

### 6.2 FCM 핸들러 분기

```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val chatMessageId = remoteMessage.data["chatMessageId"]?.toLongOrNull()
        val alertType = remoteMessage.data["alertType"]
        
        when {
            ChatActivity.isVisible && chatMessageId != null -> {
                // 채팅 화면 보고 있음 → 알림 표시 X, 단건 read + 화면 갱신
                chatRepo.markRead(chatMessageId)
                chatRepo.refreshOrAppend()
            }
            else -> {
                // 백그라운드 / 다른 화면 → 시스템 알림 표시
                showSystemNotification(
                    title = remoteMessage.notification?.title ?: "키키",
                    body = remoteMessage.notification?.body,
                    intent = chatScreenIntent(chatMessageId),  // 클릭 시 채팅으로 이동
                )
            }
        }
    }
}
```

### 6.3 안 읽음 배지

BottomNavBar 또는 채팅 아이콘에 빨간 dot 또는 숫자:

```kotlin
// 데이터 소스
val unreadCount: StateFlow<Long> = ...

// 갱신 시점
- 앱 부팅 시: GET /unread-count
- FCM 수신 시: +1 또는 GET /unread-count 재조회
- mark-all-read 후: 0으로 즉시 갱신
- markRead 단건 후: -1 또는 재조회
```

### 6.4 푸시 클릭 → 채팅 진입

기본 동작: ChatActivity 띄우면 `onResume`에서 mark-all-read 자동 호출되므로 **별도 처리 불필요**.

`chatMessageId` 같이 받았다면 그 메시지 위치로 스크롤하는 옵션 추가 가능:
```kotlin
intent.getLongExtra("chatMessageId", -1).takeIf { it > 0 }?.let { id ->
    viewModel.scrollToMessage(id)
}
```

---

## 7. 정리 (T18 끝나고)

`HealthApi.kt`에서 제거:
```kotlin
// 삭제
@GET("api/v1/alerts")
suspend fun getAlerts(...)

@PATCH("api/v1/alerts/{id}/read")
suspend fun markAlertRead(...)
```

`HealthRepository.kt`에서 제거:
- `getNotifications()` (chat API로 대체)
- `markAlertRead()` (PATCH /api/chat/messages/{id}/read로 대체)

`MainViewModel.kt`의 알림 클릭 핸들러도 `chatRepo`로 갈아끼기.

---

## 8. 참고: FCM channel id (기존 유지)

| messageType | channelId | 비고 |
|---|---|---|
| `HIGH`, `LOW`, `SOS`, `AGENT_GLUCOSE_HIGH`, `AGENT_GLUCOSE_LOW` | `glucose_critical` | 강한 알림 |
| `WEEKLY_REPORT` | `report_notification` | 리포트 |
| 그 외 (`AGENT_MEAL_FOLLOWUP` 등) | `glucose_coaching` | 부드러운 알림 |

기존 NotificationChannel 정의 그대로 사용.

---

## 9. 머지/배포 시 주의

- 이 브랜치에 올라간 V11 마이그레이션이 **이미 머지된 V11 1차와 다름** (Flyway checksum mismatch).
- 머지 후 각 환경 `docker compose down -v` 로 DB 한 번 wipe 필요.
- 시연 데이터 손실 무방.

---

## 10. 작업 순서 권장

1. **T16 먼저** — UI만 구성 (목 데이터로 화면 그리기)
2. **T17** — 옵션/명령 버튼 → 실제 API 호출 wire-up
3. **T18** — FCM + 일괄/단건 읽음 + 배지
4. 마지막으로 legacy alerts 호출 코드 정리

각 태스크 끝날 때마다 이 브랜치에 커밋 (`be/feature-chat-domain-migration-S14P31S309-1144`).
모든 태스크(BE/AI/FE) 완료 후 develop으로 단일 MR 머지.

---

## 11. 막히면 보면 좋은 파일

- `backend/src/main/java/com/ssafy/s309/domain/chat/controller/ChatMessageController.java` — endpoint 시그니처 + Swagger
- `backend/src/main/java/com/ssafy/s309/domain/chat/dto/` — 요청/응답 DTO
- `backend/src/main/java/com/ssafy/s309/common/service/FcmService.java` — FCM data 키 정의
- `docs/AGENT_API_SPEC.md` 부록 A — message_type / command_type 카탈로그 + sender 매트릭스
