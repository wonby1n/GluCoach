-- =============================================================
-- V4: alerts 도메인을 agent #6/#7 입구 + dedup 공용 인프라로 확장
--
-- V1 init_schema에 alerts 테이블이 이미 정의되어 있어 (id INT, alert_type VARCHAR(20),
-- check (HIGH/LOW/SOS/WEEKLY_REPORT)) 그대로는 AGENT_* prefix와 메시지 길이를 못 받음.
--
-- V4는 ALTER로 다음 5가지 변경:
--
-- ┌─[1] alert_type VARCHAR(20) → VARCHAR(50)
-- │   왜: 룰 type 4종(HIGH/LOW/SOS/WEEKLY_REPORT)은 짧지만 agent type은 prefix가 김
-- │   예: "AGENT_GLUCOSE_HIGH"(18자), "AGENT_SLEEP_INSIGHT"(19자), "AGENT_MEAL_FOLLOWUP"(19자)
-- │       → 20자로는 부족. 50자 여유 두면 미래 새 type(AGENT_*) 추가도 안전
-- │
-- ├─[2] message VARCHAR(100) → VARCHAR(500)
-- │   왜: 룰 알림은 짧지만 agent는 컨텍스트 코칭 메시지를 LLM이 생성 → 100자로 자주 부족
-- │   예: "기상 후 30분이에요. 어제 수면이 6시간 30분으로 짧았는데, 오늘은 점심 후 가벼운 산책으로 혈당 안정 도와봐요."
-- │       (78자 — 한국어는 1자=3바이트라 VARCHAR 길이 여유 필요)
-- │
-- ├─[3] source VARCHAR(20) NOT NULL DEFAULT 'be' 컬럼 신설 + CHECK(be|agent)
-- │   왜: 같은 alerts 테이블에 BE가 만든 알림(룰 트리거 + SOS + WEEKLY_REPORT 스케줄러 등)과
-- │       AI Agent가 send_notification으로 만든 알림이 섞임. 출처 구분이 필요
-- │       (룰만이 아니라 SOS/스케줄러도 BE가 INSERT하므로 'rule'보다 'be'가 정확)
-- │   값:
-- │     'be'    — Spring BE가 INSERT (룰 트리거 / SOS / WEEKLY_REPORT 스케줄러 등 모두 포함)
-- │     'agent' — 외부 Agent가 POST /api/agent/notifications 호출로 INSERT
-- │   예: SELECT alert_type, source, COUNT(*) FROM alerts GROUP BY 1, 2;  -- 출처별 통계
-- │       SELECT * FROM alerts WHERE source='agent' AND created_at > NOW()-INTERVAL '1 day';
-- │   기존 row는 모두 BE 룰 발송이라 DEFAULT 'be'로 자연스럽게 채워짐 (ALTER 안전)
-- │
-- ├─[4] 기존 ck_alerts_type (CHECK alert_type IN ('HIGH','LOW','SOS','WEEKLY_REPORT')) 제거
-- │   왜: AGENT_* 무한 종류(AGENT_GLUCOSE_HIGH, AGENT_MEAL_FOLLOWUP, AGENT_WAKE_UP, AGENT_SLEEP_INSIGHT,
-- │       AGENT_GENERIC, ...)를 enum/CHECK로 못 막음. 미래 추가 자유 위해 prefix 컨벤션으로 대체
-- │   대신 AGENT_ prefix 검증은 BE 서비스 레이어(AgentNotificationService)에서:
-- │       agent endpoint 호출 시 alertType이 "AGENT_"로 시작 안 하면 400 거부 → 룰 type 도용 방지
-- │
-- └─[5] 인덱스 2개 신설
--     idx_alerts_user_unread (user_id, is_read, created_at DESC)
--       → 대시보드 "안 읽은 알림 N개" 빠른 조회. 예: WHERE user_id=42 AND is_read=false
--     idx_alerts_dedup_window (user_id, alert_type, resolved_at, created_at)
--       → AlertCreationService의 30분 dedup 검사용. 매 알림 INSERT 직전 풀스캔 방지
--       예: WHERE user_id=42 AND alert_type='AGENT_GLUCOSE_HIGH'
--             AND resolved_at IS NULL AND created_at > NOW()-INTERVAL '30 minutes'
--
-- guardian_notifications / weekly_reports / meal_glucose_responses 등도 V1에 이미 있음.
-- agent_pending_triggers / sleep_sessions는 M2에서 별도 신설.
-- =============================================================

-- 1. 기존 길이 제약 풀기
ALTER TABLE alerts
  ALTER COLUMN alert_type TYPE VARCHAR(50);

ALTER TABLE alerts
  ALTER COLUMN message TYPE VARCHAR(500);

-- 2. AGENT_* prefix 거부하던 CHECK 제거
ALTER TABLE alerts
  DROP CONSTRAINT IF EXISTS ck_alerts_type;

-- 3. source 컬럼 추가 (default 'be', NOT NULL, be|agent CHECK)
ALTER TABLE alerts
  ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'be';

ALTER TABLE alerts
  ADD CONSTRAINT chk_alerts_source CHECK (source IN ('be', 'agent'));

-- 4. 인덱스 신설 — 대시보드 unread + 30분 dedup 윈도우
CREATE INDEX IF NOT EXISTS idx_alerts_user_unread
  ON alerts (user_id, is_read, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_alerts_dedup_window
  ON alerts (user_id, alert_type, resolved_at, created_at);

COMMENT ON COLUMN alerts.source IS 'be=Spring BE INSERT(룰/SOS/스케줄러), agent=Agent send_notification 호출';
