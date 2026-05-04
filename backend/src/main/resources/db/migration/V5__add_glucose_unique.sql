-- V5: glucose_records 멱등성 보장
-- 같은 사용자가 동일 시각에 중복 전송 시 DB 레벨에서 차단 (CGM 재전송 등 방어)
CREATE UNIQUE INDEX IF NOT EXISTS idx_glucose_user_measured
  ON glucose_records (user_id, measured_at);
