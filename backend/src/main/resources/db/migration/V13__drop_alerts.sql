-- =============================================================
-- V13: alerts 테이블 DROP — chat_messages 단일 도메인 흡수 완료
--
-- 사전 조건 (이미 끝남):
--   V11 — chat_messages가 alert_type/resolved_at/is_read/source/options/parent_id 등 흡수
--   V12 — guardian_notifications에 chat_message_id + latitude/longitude 신설
--   T4~T7 — Agent notification / 룰 알림 / SOS 모두 chat_messages 직접 INSERT
--   T12 — UserAlertService / AlertController / AlertCreationService 등 코드 폐기
--
-- 변경:
--   1) guardian_notifications.alert_id FK + 컬럼 제거
--      - V12에서 NOT NULL 완화하고 chat_message_id를 신규 INSERT 경로로 사용 중
--      - 신규 데이터는 chat_message_id 채워져 있음
--      - 기존 alert_id만 채워진 row가 있다면 DROP 시 손실 (시연 데이터 무방)
--   2) DROP TABLE alerts CASCADE
--      - alerts.id 참조하는 다른 FK 있으면 함께 정리 (현재는 guardian_notifications만)
--      - latitude/longitude 정보는 V12에서 guardian_notifications로 이전됨
-- =============================================================

ALTER TABLE guardian_notifications
  DROP CONSTRAINT IF EXISTS ck_guardian_notifications_link;

ALTER TABLE guardian_notifications
  DROP CONSTRAINT IF EXISTS guardian_notifications_alert_id_fkey;

ALTER TABLE guardian_notifications
  DROP COLUMN alert_id;

-- chat_message_id NOT NULL 강제 (신규 INSERT는 항상 chat 참조)
ALTER TABLE guardian_notifications
  ALTER COLUMN chat_message_id SET NOT NULL;

-- chat_messages.alert_id 고아 컬럼/인덱스 정리 (V10에서 만든 alerts FK)
DROP INDEX IF EXISTS idx_chat_messages_alert;
ALTER TABLE chat_messages
  DROP CONSTRAINT IF EXISTS fk_chat_messages_alert;
ALTER TABLE chat_messages
  DROP COLUMN IF EXISTS alert_id;

DROP TABLE alerts CASCADE;
