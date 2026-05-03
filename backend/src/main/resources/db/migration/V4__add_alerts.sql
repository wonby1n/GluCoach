-- =============================================================
-- V4: alerts 도메인을 agent #6/#7 입구 + dedup 공용 인프라로 확장
--
-- V1 init_schema에 alerts 테이블이 이미 정의되어 있어 (id INT, alert_type VARCHAR(20),
-- check (HIGH/LOW/SOS/WEEKLY_REPORT)) 그대로는 AGENT_* prefix와 메시지 길이를 못 받음.
-- V4는 ALTER로:
--   - alert_type VARCHAR(20) → VARCHAR(50) (AGENT_GLUCOSE_HIGH 등 수용)
--   - message VARCHAR(100) → VARCHAR(500) (agent 메시지 길게)
--   - source VARCHAR(20) DEFAULT 'rule' 컬럼 추가 (rule|agent 추적)
--   - 기존 ck_alerts_type (HIGH/LOW/SOS/WEEKLY_REPORT 한정) 제거
--   - 30분 dedup 윈도우 인덱스 + unread 조회 인덱스 신설
--
-- guardian_notifications/weekly_reports/meal_glucose_responses 등도 V1에 이미 있음.
-- agent_pending_triggers/sleep_sessions는 M2에서 별도 신설.
-- =============================================================

-- 1. 기존 길이 제약 풀기
ALTER TABLE alerts
  ALTER COLUMN alert_type TYPE VARCHAR(50);

ALTER TABLE alerts
  ALTER COLUMN message TYPE VARCHAR(500);

-- 2. AGENT_* prefix 거부하던 CHECK 제거
ALTER TABLE alerts
  DROP CONSTRAINT IF EXISTS ck_alerts_type;

-- 3. source 컬럼 추가 (default 'rule', NOT NULL, rule|agent CHECK)
ALTER TABLE alerts
  ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'rule';

ALTER TABLE alerts
  ADD CONSTRAINT chk_alerts_source CHECK (source IN ('rule', 'agent'));

-- 4. 인덱스 신설 — 대시보드 unread + 30분 dedup 윈도우
CREATE INDEX IF NOT EXISTS idx_alerts_user_unread
  ON alerts (user_id, is_read, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_alerts_dedup_window
  ON alerts (user_id, alert_type, resolved_at, created_at);

COMMENT ON COLUMN alerts.source IS 'rule=BE 룰 트리거, agent=Agent send_notification 호출';
