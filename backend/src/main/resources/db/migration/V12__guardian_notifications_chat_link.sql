-- =============================================================
-- V12: guardian_notifications에 chat_message_id + 위치 컬럼 추가
--
-- 배경:
--   SOS 도메인이 chat_messages로 흡수되면서 alerts에서 보관하던
--   latitude/longitude도 옮겨와야 함. 위치는 보호자 알림에만 필요한 정보라
--   chat_messages에 넣지 않고 guardian_notifications가 직접 보관.
--
-- 변경:
--   1) chat_message_id BIGINT 컬럼 추가 (alerts.id를 대체할 FK)
--      - 기존 alert_id 컬럼은 alerts DROP 시점(V13)에 제거. 그때까지 병행.
--      - SOS 신규 INSERT는 chat_message_id 채움, alert_id는 NULL
--      - 기존 데이터(alert_id 있음, chat_message_id NULL)는 그대로 유지
--   2) latitude / longitude DOUBLE PRECISION 컬럼 추가
--      - SOS 신규 INSERT 시 SosService가 직접 채움
--      - 기존 데이터는 alerts.latitude/longitude에 남아있음 (DROP 시 손실 — 시연 데이터 무방)
--
-- alert_id NULLable로 완화:
--   기존엔 NOT NULL이지만 신규 INSERT는 chat_message_id로 가니 alert_id는 NULL.
--   V13에서 alerts DROP 시 컬럼 자체 제거.
-- =============================================================

ALTER TABLE guardian_notifications
  ADD COLUMN chat_message_id BIGINT NULL,
  ADD COLUMN latitude NUMERIC(9, 6) NULL,
  ADD COLUMN longitude NUMERIC(9, 6) NULL;

ALTER TABLE guardian_notifications
  ADD CONSTRAINT fk_guardian_notifications_chat_message
    FOREIGN KEY (chat_message_id) REFERENCES chat_messages (id) ON DELETE SET NULL;

-- alert_id NOT NULL 완화 (신규 INSERT는 chat_message_id 사용)
ALTER TABLE guardian_notifications
  ALTER COLUMN alert_id DROP NOT NULL;

-- 신규 데이터는 둘 중 하나는 반드시 있어야 함
ALTER TABLE guardian_notifications
  ADD CONSTRAINT ck_guardian_notifications_link CHECK (
    alert_id IS NOT NULL OR chat_message_id IS NOT NULL
  );

CREATE INDEX idx_guardian_notifications_chat_message
  ON guardian_notifications (chat_message_id)
  WHERE chat_message_id IS NOT NULL;

COMMENT ON COLUMN guardian_notifications.chat_message_id IS 'SOS chat_messages 참조 (V13 alerts DROP 후 alert_id 대체)';
COMMENT ON COLUMN guardian_notifications.latitude        IS 'SOS 발신 시점 위도. alerts에서 이전.';
COMMENT ON COLUMN guardian_notifications.longitude       IS 'SOS 발신 시점 경도. alerts에서 이전.';
