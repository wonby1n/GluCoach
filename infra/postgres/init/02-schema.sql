-- =============================================================
-- S309 GlucoFit 전체 스키마 (PostgreSQL)
-- 수정 이력: ERDCloud export 기준으로 코멘트 밀림·미완성 테이블·누락 테이블 수정
-- =============================================================

-- ────────────────────────────────────────────
-- 1. users
-- ────────────────────────────────────────────
CREATE TABLE users (
    user_id        UUID          NOT NULL DEFAULT gen_random_uuid(),
    email          VARCHAR(255)  NOT NULL,
    provider       VARCHAR(32)   NOT NULL DEFAULT 'email',
    height         FLOAT         NULL,
    weight         FLOAT         NULL,
    is_medicated   BOOLEAN       NOT NULL DEFAULT false,
    target_low     INT           NOT NULL DEFAULT 70,
    target_high    INT           NOT NULL DEFAULT 140,
    alert_low      INT           NOT NULL DEFAULT 70,
    alert_high     INT           NOT NULL DEFAULT 180,
    night_watch    BOOLEAN       NOT NULL DEFAULT false,
    character_type VARCHAR(32)   NOT NULL DEFAULT 'BASIC',
    deleted_at     TIMESTAMP     NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uq_users_email UNIQUE (email)
);
CREATE INDEX idx_users_deleted ON users (deleted_at);

COMMENT ON COLUMN users.user_id        IS '사용자 고유 ID (UUID)';
COMMENT ON COLUMN users.email          IS '이메일 (Unique)';
COMMENT ON COLUMN users.provider       IS '로그인 방식 email/kakao/google';
COMMENT ON COLUMN users.height         IS '키 (cm)';
COMMENT ON COLUMN users.weight         IS '체중 (kg)';
COMMENT ON COLUMN users.is_medicated   IS '당뇨약/인슐린 복용 여부';
COMMENT ON COLUMN users.target_low     IS '목표 혈당 하한 (mg/dL)';
COMMENT ON COLUMN users.target_high    IS '목표 혈당 상한 (mg/dL)';
COMMENT ON COLUMN users.alert_low      IS '저혈당 알림 기준 (mg/dL)';
COMMENT ON COLUMN users.alert_high     IS '고혈당 알림 기준 (mg/dL)';
COMMENT ON COLUMN users.night_watch    IS '야간 Galaxy Watch 알림 활성화 여부';
COMMENT ON COLUMN users.character_type IS '디지털 트윈 캐릭터 테마';
COMMENT ON COLUMN users.deleted_at     IS '탈퇴 처리 시각 (NULL = 정상 계정)';

-- ────────────────────────────────────────────
-- 2. guardians  ← 미완성 → 보완
-- ────────────────────────────────────────────
CREATE TABLE guardians (
    guardian_id UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    relation    VARCHAR(32)  NULL,
    is_primary  BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_guardians  PRIMARY KEY (guardian_id),
    CONSTRAINT fk_guardians_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
CREATE INDEX idx_guardian_user ON guardians (user_id);

COMMENT ON COLUMN guardians.guardian_id IS '보호자 고유 ID';
COMMENT ON COLUMN guardians.user_id     IS 'FK → users';
COMMENT ON COLUMN guardians.name        IS '보호자 성명';
COMMENT ON COLUMN guardians.phone       IS '연락처';
COMMENT ON COLUMN guardians.relation    IS '관계 (가족/지인 등)';
COMMENT ON COLUMN guardians.is_primary  IS '대표 보호자 여부';

-- ────────────────────────────────────────────
-- 3. cgm_readings
-- ────────────────────────────────────────────
CREATE TABLE cgm_readings (
    reading_id   UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL,
    glucose_mgdl FLOAT        NOT NULL,
    trend        VARCHAR(10)  NULL,
    source       VARCHAR(16)  NOT NULL DEFAULT 'ble',
    recorded_at  TIMESTAMP    NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cgm_readings  PRIMARY KEY (reading_id),
    CONSTRAINT fk_cgm_readings_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
CREATE INDEX idx_cgm_user_time ON cgm_readings (user_id, recorded_at);

COMMENT ON COLUMN cgm_readings.reading_id   IS '측정 기록 고유 ID';
COMMENT ON COLUMN cgm_readings.user_id      IS 'FK → users';
COMMENT ON COLUMN cgm_readings.glucose_mgdl IS '측정 혈당 수치 (mg/dL)';
COMMENT ON COLUMN cgm_readings.trend        IS '추세 기호 ↑↑/↑/→/↓';
COMMENT ON COLUMN cgm_readings.source       IS '수신 경로 ble/sync';
COMMENT ON COLUMN cgm_readings.recorded_at  IS '기기 측정 시각';

-- ────────────────────────────────────────────
-- 4. cgm_patterns
-- ────────────────────────────────────────────
CREATE TABLE cgm_patterns (
    pattern_id      UUID      NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID      NOT NULL,
    version         INT       NOT NULL DEFAULT 1,
    rise_rate       FLOAT     NULL,
    fall_rate       FLOAT     NULL,
    spike_threshold FLOAT     NULL,
    response_count  INT       NOT NULL DEFAULT 0,
    personalized_at TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cgm_patterns  PRIMARY KEY (pattern_id),
    CONSTRAINT fk_cgm_patterns_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

COMMENT ON COLUMN cgm_patterns.pattern_id      IS '패턴 고유 ID';
COMMENT ON COLUMN cgm_patterns.user_id         IS 'FK → users';
COMMENT ON COLUMN cgm_patterns.version         IS '패턴 버전 (업데이트마다 +1)';
COMMENT ON COLUMN cgm_patterns.rise_rate       IS '평균 혈당 상승 속도 (mg/dL/min)';
COMMENT ON COLUMN cgm_patterns.fall_rate       IS '평균 혈당 하강 속도 (mg/dL/min)';
COMMENT ON COLUMN cgm_patterns.spike_threshold IS '개인화 스파이크 임계값';
COMMENT ON COLUMN cgm_patterns.response_count  IS '누적 실측 식사 횟수 캐시';
COMMENT ON COLUMN cgm_patterns.personalized_at IS '개인화 모델 전환 시각 (NULL = generic 사용 중)';

-- ────────────────────────────────────────────
-- 5. foods
-- ────────────────────────────────────────────
CREATE TABLE foods (
    food_id    VARCHAR(64)  NOT NULL,
    name       VARCHAR(128) NOT NULL,
    carbs_g    FLOAT        NULL,
    sugar_g    FLOAT        NULL,
    protein_g  FLOAT        NULL,
    fat_g      FLOAT        NULL,
    kcal       FLOAT        NULL,
    gi_score   INT          NULL,
    source     VARCHAR(16)  NOT NULL DEFAULT 'api',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_foods PRIMARY KEY (food_id)
);
CREATE INDEX idx_foods_name ON foods (name);

COMMENT ON COLUMN foods.food_id   IS 'API 고유 식품 ID';
COMMENT ON COLUMN foods.name      IS '음식명';
COMMENT ON COLUMN foods.carbs_g   IS '탄수화물 (g)';
COMMENT ON COLUMN foods.sugar_g   IS '당류 (g)';
COMMENT ON COLUMN foods.protein_g IS '단백질 (g)';
COMMENT ON COLUMN foods.fat_g     IS '지방 (g)';
COMMENT ON COLUMN foods.kcal      IS '칼로리 (kcal)';
COMMENT ON COLUMN foods.gi_score  IS '혈당지수 GI (0–100, 집단 평균값)';
COMMENT ON COLUMN foods.source    IS '데이터 출처 api/db/cv';

-- ────────────────────────────────────────────
-- 6. meal_logs
-- ────────────────────────────────────────────
CREATE TABLE meal_logs (
    meal_id      UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL,
    food_id      VARCHAR(64)  NULL,
    input_method VARCHAR(8)   NOT NULL DEFAULT 'text',
    simulated    BOOLEAN      NOT NULL DEFAULT false,
    image_url    VARCHAR(512) NULL,
    raw_input    VARCHAR(256) NULL,
    logged_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_meal_logs  PRIMARY KEY (meal_id),
    CONSTRAINT fk_meal_user  FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_meal_food  FOREIGN KEY (food_id) REFERENCES foods (food_id)
);
CREATE INDEX idx_meal_user_time ON meal_logs (user_id, logged_at);

COMMENT ON COLUMN meal_logs.meal_id      IS '식사 기록 고유 ID';
COMMENT ON COLUMN meal_logs.user_id      IS 'FK → users';
COMMENT ON COLUMN meal_logs.food_id      IS 'FK → foods (인식 실패 시 NULL)';
COMMENT ON COLUMN meal_logs.input_method IS '입력 방식 text/image/voice';
COMMENT ON COLUMN meal_logs.simulated    IS '시뮬레이터 가상 식사 여부';
COMMENT ON COLUMN meal_logs.image_url    IS '사진 스토리지 경로 (S3 URL)';
COMMENT ON COLUMN meal_logs.raw_input    IS '원본 입력값 (음성 텍스트/검색어)';
COMMENT ON COLUMN meal_logs.logged_at    IS '섭취/기록 시각';

-- ────────────────────────────────────────────
-- 7. meal_glucose_responses
-- ────────────────────────────────────────────
CREATE TABLE meal_glucose_responses (
    response_id UUID      NOT NULL DEFAULT gen_random_uuid(),
    meal_id     UUID      NOT NULL,
    user_id     UUID      NOT NULL,
    peak_mgdl   FLOAT     NULL,
    return_min  INT       NULL,
    grade       CHAR(1)   NULL,
    measured_at TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_meal_glucose_responses PRIMARY KEY (response_id),
    CONSTRAINT fk_mgr_meal FOREIGN KEY (meal_id) REFERENCES meal_logs (meal_id),
    CONSTRAINT fk_mgr_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
CREATE INDEX idx_response_user ON meal_glucose_responses (user_id);

COMMENT ON COLUMN meal_glucose_responses.response_id IS '혈당 반응 기록 고유 ID';
COMMENT ON COLUMN meal_glucose_responses.meal_id     IS 'FK → meal_logs';
COMMENT ON COLUMN meal_glucose_responses.user_id     IS 'FK → users (반정규화, 조회 성능용)';
COMMENT ON COLUMN meal_glucose_responses.peak_mgdl   IS '식후 최고 혈당 (mg/dL)';
COMMENT ON COLUMN meal_glucose_responses.return_min  IS '목표 범위 복귀 소요 시간 (분)';
COMMENT ON COLUMN meal_glucose_responses.grade       IS 'A~D 등급';
COMMENT ON COLUMN meal_glucose_responses.measured_at IS '측정 시각';

-- ────────────────────────────────────────────
-- 8. glucose_predictions
-- ────────────────────────────────────────────
CREATE TABLE glucose_predictions (
    pred_id              UUID      NOT NULL DEFAULT gen_random_uuid(),
    user_id              UUID      NOT NULL,
    meal_id              UUID      NOT NULL,
    model_type           VARCHAR(16) NOT NULL,
    curve_json           JSONB     NOT NULL,
    peak_pred            FLOAT     NULL,
    accuracy_pct         FLOAT     NULL,
    accuracy_updated_at  TIMESTAMP NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_glucose_predictions PRIMARY KEY (pred_id),
    CONSTRAINT fk_gp_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_gp_meal FOREIGN KEY (meal_id) REFERENCES meal_logs (meal_id)
);
CREATE INDEX idx_pred_user     ON glucose_predictions (user_id);
CREATE INDEX idx_pred_meal     ON glucose_predictions (meal_id);
CREATE INDEX idx_pred_accuracy_pending ON glucose_predictions (accuracy_updated_at)
    WHERE accuracy_updated_at IS NULL;

COMMENT ON COLUMN glucose_predictions.pred_id             IS '예측 고유 ID';
COMMENT ON COLUMN glucose_predictions.user_id             IS 'FK → users';
COMMENT ON COLUMN glucose_predictions.meal_id             IS 'FK → meal_logs';
COMMENT ON COLUMN glucose_predictions.model_type          IS 'generic / personalized';
COMMENT ON COLUMN glucose_predictions.curve_json          IS '식후 2시간 예측 곡선 배열';
COMMENT ON COLUMN glucose_predictions.peak_pred           IS '예측 최고 혈당 (mg/dL)';
COMMENT ON COLUMN glucose_predictions.accuracy_pct        IS '예측 정확도 (%)';
COMMENT ON COLUMN glucose_predictions.accuracy_updated_at IS '정확도 역산 완료 시각 (NULL = 미처리)';

-- ────────────────────────────────────────────
-- 9. alert_logs
-- ────────────────────────────────────────────
CREATE TABLE alert_logs (
    alert_id     UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    alert_type   VARCHAR(16) NOT NULL,
    glucose_val  FLOAT       NULL,
    responded    BOOLEAN     NOT NULL DEFAULT false,
    responded_at TIMESTAMP   NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_alert_logs PRIMARY KEY (alert_id),
    CONSTRAINT fk_alert_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
CREATE INDEX idx_alert_user_time ON alert_logs (user_id, created_at);

COMMENT ON COLUMN alert_logs.alert_id     IS '알림 로그 고유 ID';
COMMENT ON COLUMN alert_logs.user_id      IS 'FK → users';
COMMENT ON COLUMN alert_logs.alert_type   IS '알림 유형 low/high/sos/reminder';
COMMENT ON COLUMN alert_logs.glucose_val  IS '알림 발생 시점 혈당 (없으면 NULL)';
COMMENT ON COLUMN alert_logs.responded    IS '사용자 반응 여부';
COMMENT ON COLUMN alert_logs.responded_at IS '반응 확인 시각';

-- ────────────────────────────────────────────
-- 10. smarthome_events  ← 미완성 → 보완
-- ────────────────────────────────────────────
CREATE TABLE smarthome_events (
    event_id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    alert_id         UUID        NOT NULL,
    user_id          UUID        NOT NULL,
    device_type      VARCHAR(16) NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'sent',
    triggered_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    response_payload JSONB       NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_smarthome_events PRIMARY KEY (event_id),
    CONSTRAINT fk_sh_alert FOREIGN KEY (alert_id) REFERENCES alert_logs (alert_id),
    CONSTRAINT fk_sh_user  FOREIGN KEY (user_id)  REFERENCES users (user_id)
);
CREATE INDEX idx_sh_event_alert ON smarthome_events (alert_id);
CREATE INDEX idx_sh_event_user  ON smarthome_events (user_id);

COMMENT ON COLUMN smarthome_events.event_id         IS '스마트홈 이벤트 고유 ID';
COMMENT ON COLUMN smarthome_events.alert_id         IS 'FK → alert_logs';
COMMENT ON COLUMN smarthome_events.user_id          IS 'FK → users';
COMMENT ON COLUMN smarthome_events.device_type      IS '제어 종류 hue/smartthings/sns';
COMMENT ON COLUMN smarthome_events.status           IS '상태 sent/done/fail';
COMMENT ON COLUMN smarthome_events.triggered_at     IS '트리거 시각';
COMMENT ON COLUMN smarthome_events.response_payload IS '기기 응답 원문 (JSON)';

-- ────────────────────────────────────────────
-- 11. samsung_health_events
-- ────────────────────────────────────────────
CREATE TABLE samsung_health_events (
    sh_event_id   UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL,
    event_type    VARCHAR(16) NOT NULL,
    start_at      TIMESTAMP   NOT NULL,
    end_at        TIMESTAMP   NULL,
    calories_kcal FLOAT       NULL,
    detail_json   JSONB       NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_samsung_health_events PRIMARY KEY (sh_event_id),
    CONSTRAINT fk_she_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
CREATE INDEX idx_sh_user_time ON samsung_health_events (user_id, start_at);

COMMENT ON COLUMN samsung_health_events.sh_event_id   IS 'Samsung Health 이벤트 고유 ID';
COMMENT ON COLUMN samsung_health_events.user_id       IS 'FK → users';
COMMENT ON COLUMN samsung_health_events.event_type    IS '이벤트 유형 exercise/sleep 등';
COMMENT ON COLUMN samsung_health_events.start_at      IS '시작 시각';
COMMENT ON COLUMN samsung_health_events.end_at        IS '종료 시각';
COMMENT ON COLUMN samsung_health_events.calories_kcal IS '소모 칼로리 (운동)';
COMMENT ON COLUMN samsung_health_events.detail_json   IS '상세 메타데이터';

-- ────────────────────────────────────────────
-- 12. weekly_reports
-- ────────────────────────────────────────────
CREATE TABLE weekly_reports (
    report_id     UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    week_start    DATE         NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    avg_glucose   FLOAT        NULL,
    tir_pct       FLOAT        NULL,
    std_dev       FLOAT        NULL,
    top_good_json JSONB        NULL,
    top_bad_json  JSONB        NULL,
    llm_summary   TEXT         NULL,
    llm_suggest   TEXT         NULL,
    pdf_url       VARCHAR(512) NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_weekly_reports PRIMARY KEY (report_id),
    CONSTRAINT fk_wr_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
CREATE INDEX idx_report_user_week ON weekly_reports (user_id, week_start);
CREATE INDEX idx_report_status ON weekly_reports (status)
    WHERE status IN ('PENDING', 'PROCESSING');

COMMENT ON COLUMN weekly_reports.report_id     IS '리포트 고유 ID';
COMMENT ON COLUMN weekly_reports.user_id       IS 'FK → users';
COMMENT ON COLUMN weekly_reports.week_start    IS '주간 기준일 (월요일)';
COMMENT ON COLUMN weekly_reports.status        IS '생성 상태 PENDING/PROCESSING/DONE/FAILED';
COMMENT ON COLUMN weekly_reports.avg_glucose   IS '평균 혈당 (mg/dL)';
COMMENT ON COLUMN weekly_reports.tir_pct       IS '목표 범위 유지율 (%)';
COMMENT ON COLUMN weekly_reports.std_dev       IS '혈당 변동성 표준편차';
COMMENT ON COLUMN weekly_reports.top_good_json IS '혈당 안정 음식 TOP3';
COMMENT ON COLUMN weekly_reports.top_bad_json  IS '혈당 스파이크 음식 TOP3';
COMMENT ON COLUMN weekly_reports.llm_summary   IS 'AI 주간 요약 텍스트';
COMMENT ON COLUMN weekly_reports.llm_suggest   IS 'AI 다음 주 개선 제안';
COMMENT ON COLUMN weekly_reports.pdf_url       IS 'PDF 내보내기 S3 경로';

-- ────────────────────────────────────────────
-- 13. medication_logs  ← 신규 추가
-- ────────────────────────────────────────────
CREATE TABLE medication_logs (
    log_id      UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    log_type    VARCHAR(16)  NOT NULL DEFAULT 'medicine',
    name        VARCHAR(128) NULL,
    dose_amount FLOAT        NULL,
    dose_unit   VARCHAR(16)  NULL,
    taken_at    TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_medication_logs PRIMARY KEY (log_id),
    CONSTRAINT fk_med_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
CREATE INDEX idx_med_user_time ON medication_logs (user_id, taken_at);

COMMENT ON COLUMN medication_logs.log_id      IS '복용 기록 고유 ID';
COMMENT ON COLUMN medication_logs.user_id     IS 'FK → users';
COMMENT ON COLUMN medication_logs.log_type    IS '유형 insulin/medicine';
COMMENT ON COLUMN medication_logs.name        IS '약품명 또는 인슐린 종류';
COMMENT ON COLUMN medication_logs.dose_amount IS '투여량';
COMMENT ON COLUMN medication_logs.dose_unit   IS '단위 (mg/unit 등)';
COMMENT ON COLUMN medication_logs.taken_at    IS '복용/투여 시각';

-- ────────────────────────────────────────────
-- 14. notification_tokens  ← 신규 추가
-- ────────────────────────────────────────────
CREATE TABLE notification_tokens (
    token_id    UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    device_type VARCHAR(16)  NOT NULL DEFAULT 'android',
    fcm_token   VARCHAR(512) NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_notification_tokens PRIMARY KEY (token_id),
    CONSTRAINT fk_nt_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
CREATE INDEX idx_nt_user ON notification_tokens (user_id);

COMMENT ON COLUMN notification_tokens.token_id    IS '토큰 고유 ID';
COMMENT ON COLUMN notification_tokens.user_id     IS 'FK → users';
COMMENT ON COLUMN notification_tokens.device_type IS '기기 종류 android/watch';
COMMENT ON COLUMN notification_tokens.fcm_token   IS 'FCM 등록 토큰';
COMMENT ON COLUMN notification_tokens.is_active   IS '활성 여부';
