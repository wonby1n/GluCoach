-- =============================================================
-- V11: chat_messages 컬럼 확장 — alerts 도메인 흡수 + 선택지 구조
--
-- V10에서 만든 chat_messages를 알림 메타 + 라이프사이클 + 선택지 응답까지
-- 모두 흡수하는 단일 도메인으로 확장.
--
-- 변경 5종:
--
-- ┌─[1] sender CHECK 확장 'system' 추가
-- │   기존: ('agent', 'user')   →   신규: ('agent', 'system', 'user')
-- │   'system' = BE 룰 발신 (HIGH/LOW/SOS/WEEKLY_REPORT 등)
-- │   기존 alerts.source='be' 매핑 대체.
-- │
-- ├─[2] alert_type / resolved_at / is_read / source 컬럼 추가
-- │   alerts 테이블 책임을 chat_messages에 흡수:
-- │     alert_type   — dedup 키 (user 메시지는 NULL)
-- │     resolved_at  — 룰 알림 라이프사이클 (혈당 정상복귀 시 갱신)
-- │     is_read      — 읽음 상태 (대시보드 안 읽음 카운트)
-- │     source       — 'be'|'agent' 통계용 (sender와 중복 보이지만 sender는 UI용,
-- │                    source는 INSERT 출처 추적용으로 의미 분리)
-- │
-- ├─[3] options JSONB 컬럼 추가 (agent 발신 메시지 선택지)
-- │   구조: [{"id":"walk_now","label":"지금 산책"}, ...]
-- │   정확히 3개 고정 (UX 룰). CHECK는 jsonb_array_length=3 으로 강제.
-- │   user/system 메시지는 NULL 허용.
-- │
-- ├─[4] parent_id / selected_option_id 컬럼 추가 (사용자 응답 추적)
-- │   parent_id            — user 응답이 어떤 agent 메시지에 대한 것인지 (자기참조 FK)
-- │   selected_option_id   — 그 메시지 options 중 어떤 id를 선택했는지
-- │   user 메시지에만 채워짐. agent/system은 NULL.
-- │
-- └─[5] 인덱스 4종
--     (a) idx_chat_messages_dedup       — alert_type 30분 윈도우 dedup
--     (b) idx_chat_messages_open_rule   — resolved_at IS NULL 활성 룰 조회
--     (c) idx_chat_messages_unread      — 안 읽음 카운트 부분 인덱스
--     (d) idx_chat_messages_parent      — 응답 사이클 묶음 조회
-- =============================================================

-- [1] sender CHECK 확장
ALTER TABLE chat_messages
  DROP CONSTRAINT ck_chat_messages_sender;

ALTER TABLE chat_messages
  ADD CONSTRAINT ck_chat_messages_sender CHECK (sender IN ('agent', 'system', 'user'));

-- [2] alerts 도메인 흡수 컬럼
ALTER TABLE chat_messages
  ADD COLUMN alert_type VARCHAR(50)         NULL,
  ADD COLUMN resolved_at TIMESTAMP          NULL,
  ADD COLUMN is_read BOOLEAN                NOT NULL DEFAULT false,
  ADD COLUMN source VARCHAR(20)             NULL;

ALTER TABLE chat_messages
  ADD CONSTRAINT ck_chat_messages_source CHECK (source IS NULL OR source IN ('be', 'agent'));

-- [3] options 선택지
ALTER TABLE chat_messages
  ADD COLUMN options JSONB NULL;

ALTER TABLE chat_messages
  ADD CONSTRAINT ck_chat_messages_options_size
    CHECK (options IS NULL OR jsonb_array_length(options) = 3);

-- [4] 사용자 응답 추적
ALTER TABLE chat_messages
  ADD COLUMN parent_id BIGINT NULL,
  ADD COLUMN selected_option_id VARCHAR(50) NULL;

ALTER TABLE chat_messages
  ADD CONSTRAINT fk_chat_messages_parent
    FOREIGN KEY (parent_id) REFERENCES chat_messages (id) ON DELETE SET NULL;

-- user 메시지는 parent_id + selected_option_id 둘 다 있어야 함
-- agent/system은 둘 다 NULL이어야 함
ALTER TABLE chat_messages
  ADD CONSTRAINT ck_chat_messages_user_reply CHECK (
    (sender = 'user'  AND parent_id IS NOT NULL AND selected_option_id IS NOT NULL)
    OR
    (sender <> 'user' AND parent_id IS NULL     AND selected_option_id IS NULL)
  );

-- agent 메시지는 options 필수, system은 options 없음
ALTER TABLE chat_messages
  ADD CONSTRAINT ck_chat_messages_options_sender CHECK (
    (sender = 'agent'  AND options IS NOT NULL)
    OR
    (sender <> 'agent' AND options IS NULL)
  );

-- [5] 인덱스 4종

-- (a) dedup 30분 윈도우 — AlertCreationService 흡수 후 사용
CREATE INDEX idx_chat_messages_dedup
  ON chat_messages (user_id, alert_type, resolved_at, created_at)
  WHERE alert_type IS NOT NULL;

-- (b) 활성 룰 알림 조회 — AlertTriggerService 정상복귀 처리용
CREATE INDEX idx_chat_messages_open_rule
  ON chat_messages (user_id, alert_type)
  WHERE resolved_at IS NULL AND alert_type IS NOT NULL;

-- (c) 안 읽음 카운트 — 대시보드 배지
CREATE INDEX idx_chat_messages_unread
  ON chat_messages (user_id, created_at DESC)
  WHERE is_read = false;

-- (d) 응답 사이클 — parent_id로 묶음 조회
CREATE INDEX idx_chat_messages_parent
  ON chat_messages (parent_id)
  WHERE parent_id IS NOT NULL;

COMMENT ON COLUMN chat_messages.alert_type           IS 'dedup 키. agent/system 발신만, user 메시지는 NULL';
COMMENT ON COLUMN chat_messages.resolved_at          IS '룰 알림 라이프사이클. 혈당 정상복귀 시 갱신';
COMMENT ON COLUMN chat_messages.is_read              IS '읽음 상태. 대시보드 안 읽음 카운트용';
COMMENT ON COLUMN chat_messages.source               IS 'be=Spring BE INSERT, agent=Agent send_notification 호출';
COMMENT ON COLUMN chat_messages.options              IS 'agent 메시지 선택지 3개 [{id,label},...]';
COMMENT ON COLUMN chat_messages.parent_id            IS 'user 응답이 어떤 agent 메시지(parent)에 대한 것인지 자기참조 FK';
COMMENT ON COLUMN chat_messages.selected_option_id   IS 'user 메시지에만, parent의 options 중 선택한 id';
