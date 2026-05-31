-- =============================================================
-- V14: alert_type → message_type 리네임 + command/payload 슬롯 추가
--
-- V11에서 'alert_type'으로 도입된 컬럼을 'message_type'으로 정리.
-- agent/system/user 3-way 매트릭스 CHECK 재정의.
-- user 자발 발화(command_type) + 풍부한 콘텐츠(payload) 슬롯 추가.
--
-- 변경 6종:
--   [1] V11의 타이트한 CHECK 3개 제거 (리빌드 전 정리)
--   [2] 구 인덱스 2개 제거 (alert_type 참조)
--   [3] alert_type → message_type 컬럼 리네임
--   [4] command_type / payload 컬럼 추가
--   [5] 새 CHECK 2개 추가 (options 0~10 완화 + sender 매트릭스)
--   [6] 새 인덱스 3개 재생성
-- =============================================================

-- [1] V11 구 CHECK 제거
ALTER TABLE chat_messages
  DROP CONSTRAINT ck_chat_messages_user_reply,
  DROP CONSTRAINT ck_chat_messages_options_sender,
  DROP CONSTRAINT ck_chat_messages_options_size;

-- [2] alert_type 참조 인덱스 제거
DROP INDEX idx_chat_messages_dedup;
DROP INDEX idx_chat_messages_open_rule;

-- [3] 컬럼 리네임
ALTER TABLE chat_messages
  RENAME COLUMN alert_type TO message_type;

-- [4] 신규 슬롯
ALTER TABLE chat_messages
  ADD COLUMN command_type VARCHAR(50) NULL,
  ADD COLUMN payload      JSONB       NULL;

-- [5] 새 CHECK
ALTER TABLE chat_messages
  ADD CONSTRAINT ck_chat_messages_options_size
    CHECK (options IS NULL OR jsonb_array_length(options) BETWEEN 0 AND 10);

-- sender별 필드 조합 강제 (STI 매트릭스)
-- system : message_type 필수, command/parent/option/options 모두 NULL
-- agent  : message_type 필수, command_type/selected_option_id NULL
-- user   : message_type/options NULL, command_type 또는 parent 기반 응답
ALTER TABLE chat_messages
  ADD CONSTRAINT ck_chat_messages_shape CHECK (
    (sender = 'system' AND
       message_type IS NOT NULL AND
       command_type IS NULL AND
       parent_id IS NULL AND
       selected_option_id IS NULL AND
       options IS NULL)
    OR
    (sender = 'agent' AND
       message_type IS NOT NULL AND
       command_type IS NULL AND
       selected_option_id IS NULL)
    OR
    (sender = 'user' AND
       message_type IS NULL AND
       options IS NULL AND
       (
         command_type IS NOT NULL
         OR (parent_id IS NOT NULL AND selected_option_id IS NOT NULL)
         OR (parent_id IS NOT NULL AND command_type IS NULL AND selected_option_id IS NULL)
       ))
  );

-- [6] 인덱스 재생성

-- dedup 30분 윈도우 (message_type 기준)
CREATE INDEX idx_chat_messages_dedup
  ON chat_messages (user_id, message_type, resolved_at, created_at)
  WHERE message_type IS NOT NULL;

-- 활성 룰 메시지 조회 (정상복귀 처리용)
CREATE INDEX idx_chat_messages_open_rule
  ON chat_messages (user_id, message_type)
  WHERE resolved_at IS NULL AND message_type IS NOT NULL;

-- user command 히스토리
CREATE INDEX idx_chat_messages_user_command
  ON chat_messages (user_id, command_type, created_at)
  WHERE command_type IS NOT NULL;

COMMENT ON COLUMN chat_messages.message_type       IS '메시지 카테고리. agent/system 발신만 채움 (HIGH/LOW/SOS/WEEKLY_REPORT/AGENT_*). user는 NULL';
COMMENT ON COLUMN chat_messages.command_type       IS 'user 자발 발화 명령 의도 (USER_REQUEST_FOOD_RECOMMEND 등). user 발신에서 message_type 대신 사용';
COMMENT ON COLUMN chat_messages.payload            IS '풍부한 응답 콘텐츠(음식 카드/추천 결과/명령 파라미터). display_trace는 디버그용, payload는 UI 렌더용';
