-- =============================================================
-- V11: chat_messages 확장 — 알림 도메인 흡수 + 양방향 채팅 슬롯
--
-- V10에서 만든 chat_messages를 단일 도메인으로 확장:
--   - 기존 alerts 테이블 책임(타입/dedup/라이프사이클) 흡수
--   - sender 3종(system/agent/user) 매트릭스 표현
--   - user-initiated command 발화 (음식 추천 버튼 등) 지원
--   - 풍부한 응답 콘텐츠(payload) 지원
--
-- "알림(alert)" 개념 자체를 폐기하고 "채팅 메시지"의 한 갈래로 흡수.
-- 컬럼 명명도 alert_type → message_type 으로 정리 (외부 FCM payload 키
-- alertType은 호환을 위해 그대로 유지, 내부 매핑만 변경).
--
-- 변경 6종:
--
-- ┌─[1] sender CHECK 확장 'system' 추가 (기존: agent/user → 신규: agent/system/user)
-- │   'system' = BE 룰 발신 (HIGH/LOW/SOS/WEEKLY_REPORT 등). 구 alerts.source='be' 매핑 대체.
-- │
-- ├─[2] message_type / resolved_at / is_read / source 컬럼 추가
-- │   message_type — 메시지 카테고리 (HIGH/LOW/SOS/WEEKLY_REPORT/AGENT_*)
-- │                  agent/system 발신만 채움. user 발신은 NULL (대신 command_type 사용).
-- │   resolved_at  — 룰 메시지 라이프사이클 (혈당 정상복귀 시 갱신)
-- │   is_read      — 읽음 상태 (대시보드 안 읽음 카운트)
-- │   source       — 'be'|'agent' INSERT 출처 추적 (sender는 UI용, source는 통계용)
-- │
-- ├─[3] options JSONB — agent 발신 메시지 선택지
-- │   구조: [{"id":"walk_now","label":"지금 산책"}, ...]
-- │   길이 0~10 자유 (구 V11의 정확히 3개 강제 완화).
-- │   nullable — 선택지 없는 단순 응답 메시지도 허용.
-- │
-- ├─[4] parent_id / selected_option_id — 응답 사이클 추적 (자기참조 FK)
-- │   parent_id           — 응답 메시지가 어떤 부모 메시지에 대한 것인지
-- │                          user→agent 응답: 부모는 agent 메시지
-- │                          agent→user 응답: 부모는 user의 command 메시지
-- │   selected_option_id  — user가 부모의 options 중 어떤 id를 선택했는지
-- │
-- ├─[5] command_type / payload — 신규 슬롯
-- │   command_type — user가 자발 발화한 명령 의도 (USER_REQUEST_FOOD_RECOMMEND 등)
-- │                  user 발신에서 message_type 대신 사용.
-- │   payload      — 풍부한 콘텐츠 (음식 추천 카드 N개, 추천 결과, 명령 파라미터 등)
-- │                  display_trace는 디버그용, payload는 UI 렌더용으로 의미 분리.
-- │
-- └─[6] 매트릭스 CHECK 1개 (sender 분기)
--     sender별로 어떤 필드가 NOT NULL인지 통합 표현. nullable 컬럼이 sender 값에 의존
--     하는 비정규화 설계지만, 채팅 도메인 표준 패턴(STI)을 의도적으로 채택.
-- =============================================================

-- [1] sender CHECK 확장
ALTER TABLE chat_messages
  DROP CONSTRAINT ck_chat_messages_sender;

ALTER TABLE chat_messages
  ADD CONSTRAINT ck_chat_messages_sender CHECK (sender IN ('agent', 'system', 'user'));

-- [2] 알림 도메인 흡수 컬럼
ALTER TABLE chat_messages
  ADD COLUMN message_type VARCHAR(50)         NULL,
  ADD COLUMN resolved_at  TIMESTAMP           NULL,
  ADD COLUMN is_read      BOOLEAN             NOT NULL DEFAULT false,
  ADD COLUMN source       VARCHAR(20)         NULL;

ALTER TABLE chat_messages
  ADD CONSTRAINT ck_chat_messages_source CHECK (source IS NULL OR source IN ('be', 'agent'));

-- [3] options 선택지 (nullable, 0~10)
ALTER TABLE chat_messages
  ADD COLUMN options JSONB NULL;

ALTER TABLE chat_messages
  ADD CONSTRAINT ck_chat_messages_options_size
    CHECK (options IS NULL OR jsonb_array_length(options) BETWEEN 0 AND 10);

-- [4] 응답 사이클 추적
ALTER TABLE chat_messages
  ADD COLUMN parent_id          BIGINT      NULL,
  ADD COLUMN selected_option_id VARCHAR(50) NULL;

ALTER TABLE chat_messages
  ADD CONSTRAINT fk_chat_messages_parent
    FOREIGN KEY (parent_id) REFERENCES chat_messages (id) ON DELETE SET NULL;

-- [5] command 발화 + 풍부한 콘텐츠
ALTER TABLE chat_messages
  ADD COLUMN command_type VARCHAR(50) NULL,
  ADD COLUMN payload      JSONB       NULL;

-- [6] 매트릭스 CHECK — sender별 필드 조합 강제
-- system : message_type 필수, command/parent/option/options 모두 NULL
-- agent  : message_type 필수, command_type/selected_option_id NULL
--          parent_id는 push면 NULL, command 응답이면 NOT NULL (둘 다 허용)
-- user   : message_type/options NULL.
--          (a) command_type 채움  (b) parent+selected_option_id 채움  (c) parent만 (자유 텍스트, 장래)
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

-- [인덱스]

-- (a) dedup 30분 윈도우 — agent/system 메시지 동일 type 중복 발사 차단
CREATE INDEX idx_chat_messages_dedup
  ON chat_messages (user_id, message_type, resolved_at, created_at)
  WHERE message_type IS NOT NULL;

-- (b) 활성 룰 메시지 조회 — 정상복귀 처리용
CREATE INDEX idx_chat_messages_open_rule
  ON chat_messages (user_id, message_type)
  WHERE resolved_at IS NULL AND message_type IS NOT NULL;

-- (c) 안 읽음 카운트 — 대시보드 배지
CREATE INDEX idx_chat_messages_unread
  ON chat_messages (user_id, created_at DESC)
  WHERE is_read = false;

-- (d) 응답 사이클 — parent_id 묶음 조회
CREATE INDEX idx_chat_messages_parent
  ON chat_messages (parent_id)
  WHERE parent_id IS NOT NULL;

-- (e) user command 히스토리 — 명령 발화 분석/감사용
CREATE INDEX idx_chat_messages_user_command
  ON chat_messages (user_id, command_type, created_at)
  WHERE command_type IS NOT NULL;

COMMENT ON COLUMN chat_messages.message_type        IS '메시지 카테고리. agent/system 발신만 채움 (HIGH/LOW/SOS/WEEKLY_REPORT/AGENT_*). user는 NULL';
COMMENT ON COLUMN chat_messages.resolved_at         IS '룰 메시지 라이프사이클. 혈당 정상복귀 시 갱신';
COMMENT ON COLUMN chat_messages.is_read             IS '읽음 상태. 대시보드 안 읽음 카운트용';
COMMENT ON COLUMN chat_messages.source              IS 'be=Spring BE INSERT, agent=Agent send_notification 호출';
COMMENT ON COLUMN chat_messages.options             IS 'agent 메시지 선택지 [{id,label},...] 0~10개';
COMMENT ON COLUMN chat_messages.parent_id           IS '응답 사이클 자기참조 FK. user→agent 응답이면 부모는 agent, agent→user command 응답이면 부모는 user';
COMMENT ON COLUMN chat_messages.selected_option_id  IS 'user 응답 시 부모의 options 중 선택한 id';
COMMENT ON COLUMN chat_messages.command_type        IS 'user 자발 발화 명령 의도 (USER_REQUEST_FOOD_RECOMMEND 등). user 발신에서 message_type 대신 사용';
COMMENT ON COLUMN chat_messages.payload             IS '풍부한 응답 콘텐츠(음식 카드/추천 결과/명령 파라미터). display_trace는 디버그용, payload는 UI 렌더용';
