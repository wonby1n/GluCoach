-- =============================================================
-- V2: 헬스 저장 구조 개편
--   - sleep_records, exercise_records 제거
--   - daily_health_summaries 신설 (삼성 헬스 1분 폴링 → upsert)
-- =============================================================

DROP TABLE IF EXISTS sleep_records;
DROP TABLE IF EXISTS exercise_records;

CREATE TABLE daily_health_summaries (
    user_id         INT          NOT NULL,
    date            DATE         NOT NULL,
    steps           INT          NULL,
    calories_burned NUMERIC(6,2) NULL,
    sleep_minutes   INT          NULL,
    avg_heart_rate  NUMERIC(5,1) NULL,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_daily_health_summaries PRIMARY KEY (user_id, date),
    CONSTRAINT fk_dhs_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_dhs_user_date ON daily_health_summaries (user_id, date DESC);

COMMENT ON TABLE  daily_health_summaries        IS '삼성 헬스 일별 요약. (user_id, date) 기준 upsert';
COMMENT ON COLUMN daily_health_summaries.date   IS '기준일 (수면은 wake-up date)';
COMMENT ON COLUMN daily_health_summaries.steps  IS '오늘 누적 걸음수';
COMMENT ON COLUMN daily_health_summaries.calories_burned IS '오늘 활동 칼로리 (TOTAL_ACTIVE_CALORIES_BURNED)';
COMMENT ON COLUMN daily_health_summaries.sleep_minutes  IS '수면 시간 (분, getLastSleepDurationMinutes)';
COMMENT ON COLUMN daily_health_summaries.avg_heart_rate IS '평균 심박수 (bpm)';
