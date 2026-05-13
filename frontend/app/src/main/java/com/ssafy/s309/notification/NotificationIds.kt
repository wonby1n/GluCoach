package com.ssafy.s309.notification

import java.util.concurrent.atomic.AtomicInteger

/**
 * 시스템 전반에서 사용하는 알림/PendingIntent ID 발급 정책.
 *
 * 과거에 `System.currentTimeMillis().toInt()` 를 ID 로 쓰던 코드가 있었는데,
 * Long → Int 절단 + 같은 ms 에 두 알림 도착 시 ID 동일 → 두 번째 알림이
 * 첫 번째를 조용히 덮어쓰는 (`알림이 먹히는`) 버그가 있었다.
 *
 * 통일된 규칙:
 *  - 동일 사용자 액션 단위로 고정해야 하는 알림 (식사 리마인더 등): 슬롯별 상수 ID 직접 지정
 *  - 일회성 이벤트 단위 알림 (FCM, BLE alert): [nextEphemeral] — 카운터 (1천만~ 대역)
 *  - 같은 chat_message 의 상태 변화로 갱신해야 할 알림: [forChatMessage] — 의도적 덮어쓰기
 *
 * PendingIntent 의 requestCode 도 같은 카운터를 쓰면 동일 충돌 위험이 있으므로
 * 알림 ID 와 별도 카운터 ([nextRequestCode]) 를 둔다.
 */
object NotificationIds {
    // 식사 리마인더 (3001~3003), GlucoseAlertManager (2000~) 와 겹치지 않도록 1천만 이상 고대역
    private val ephemeralCounter = AtomicInteger(10_000_000)
    private val requestCodeCounter = AtomicInteger(20_000_000)

    /** 일회성 이벤트 알림 ID. 호출마다 새 값 반환. */
    fun nextEphemeral(): Int = ephemeralCounter.incrementAndGet()

    /** PendingIntent requestCode. 호출마다 새 값. */
    fun nextRequestCode(): Int = requestCodeCounter.incrementAndGet()

    /** 같은 chat_message 의 알림을 갱신할 때 사용 — 의도적 덮어쓰기. */
    fun forChatMessage(chatMessageId: Long): Int = (chatMessageId and 0x7FFFFFFF).toInt()
}
