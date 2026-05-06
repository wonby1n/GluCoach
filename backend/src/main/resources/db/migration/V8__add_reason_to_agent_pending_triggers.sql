-- =============================================================
-- V8: agent_pending_triggers.reason 컬럼 추가
--
-- 배경:
--   Agent #8 schedule_followup 호출 시 Agent가 LLM 판단 메모(reason)를 함께 전달.
--   예: "회의 중이라 못 움직임, 회의 종료 후 활동 재권유"
--
--   이 reason은 N분 후 폴러가 Agent를 재호출할 때 그대로 돌려줘야 한다 — Agent가
--   다음 사이클에서 "왜 내가 다시 호출됐는지" 빠르게 컨텍스트 복원하는 단기 메모.
--
-- nullable:
--   기존 식사 INSERT 시 자동 생성되는 post_meal 트리거(MealRecordService 경로)는
--   reason이 없는 게 자연스러움 — 첫 호출이라 "왜 깨우는지" 메모할 게 없음.
--   따라서 NULL 허용.
-- =============================================================

ALTER TABLE agent_pending_triggers
    ADD COLUMN reason VARCHAR(500) NULL;

COMMENT ON COLUMN agent_pending_triggers.reason
    IS 'Agent가 schedule_followup 호출 시 전달한 LLM 판단 메모. 폴러 재호출 시 Agent에 그대로 echo.';
