-- =====================================================================
-- ERD DDL 가이드 (ERD Cloud 재임포트용 / MySQL 8.0 기준)
-- ---------------------------------------------------------------------
-- 출처   : backend/docs/ERD.ts (TypeScript export)
-- 목적   : ERD Cloud의 SQL export가 FK를 누락하는 문제를 해결하기 위해
--          FK 제약을 명시한 표준 DDL로 정리
-- 명명규칙: 인터페이스(PascalCase) → 테이블(snake_case)
-- 주의   : 본 파일은 "가이드용" — 실제 운영 마이그레이션은 JPA/Flyway
--          소스에서 생성된 것을 우선시할 것
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 1. users  (회원)
--    소셜/이메일 가입, 온보딩 신체정보·당뇨유형·목표범위 보관
--    deleted_at NOT NULL → 탈퇴 회원(소프트 삭제)
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id              INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    email           VARCHAR(255)    NOT NULL                COMMENT '이메일 (탈퇴 시 deleted_{uuid}@withdrawn.local)',
    password        VARCHAR(60)     NULL                    COMMENT '비밀번호 (소셜 로그인 시 NULL)',
    provider        VARCHAR(10)     NOT NULL                COMMENT 'email / kakao / google',
    name            VARCHAR(20)     NOT NULL                COMMENT '한글 풀네임 + 영문 닉네임 수용',
    age             TINYINT         NULL,
    gender          VARCHAR(6)      NULL                    COMMENT 'male / female',
    phone           VARCHAR(20)     NOT NULL                COMMENT '국제번호, 하이픈 포함',
    height          DECIMAL(4,1)    NULL                    COMMENT '키 (cm) — 예측 API용',
    weight          DECIMAL(4,1)    NULL                    COMMENT '체중 (kg) — 예측 API용',
    diabetes_type   VARCHAR(10)     NULL                    COMMENT 'NORMAL / T1D / T2D',
    is_medicated   BOOLEAN         NULL                    COMMENT '당뇨약/인슐린 투약 대상자 여부',
    target_low      DECIMAL(5,2)    NULL                    COMMENT '목표 하한 혈당 (mg/dL)',
    target_high     DECIMAL(5,2)    NULL                    COMMENT '목표 상한 혈당 (mg/dL)',
    week_start_day  TINYINT         NOT NULL                COMMENT '다음 리포트 시작 요일',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP       NULL                    COMMENT '탈퇴 시간 (소프트 삭제)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) COMMENT '회원';

-- ---------------------------------------------------------------------
-- 2. foods  (음식 영양정보)
--    공공데이터 API 캐시 + 사용자 커스텀 음식 (is_customized = true)
-- ---------------------------------------------------------------------
CREATE TABLE foods (
    id              INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    food_name       VARCHAR(100)    NOT NULL                COMMENT '음식 이름',
    calories        DECIMAL(6,2)    NULL                    COMMENT '칼로리 (kcal)',
    carbohydrate    DECIMAL(5,2)    NULL                    COMMENT '탄수화물 (g)',
    protein         DECIMAL(5,2)    NULL                    COMMENT '단백질 (g)',
    fat             DECIMAL(5,2)    NULL                    COMMENT '지방 (g)',
    sugar           DECIMAL(5,2)    NULL                    COMMENT '당류 (g)',
    fiber           DECIMAL(5,2)    NULL                    COMMENT '식이섬유 (g)',
    serving_size    DECIMAL(5,2)    NULL                    COMMENT '1회 제공량 (g)',
    is_customized   BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_foods_name (food_name)
) COMMENT '음식 영양정보';

-- ---------------------------------------------------------------------
-- 3. glucose_records  (혈당 측정 원시 데이터, CGM)
--    전체 서비스의 핵심 테이블. 다른 도메인에서 시간 범위로 조회.
-- ---------------------------------------------------------------------
CREATE TABLE glucose_records (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id         INT             NOT NULL                COMMENT 'FK → users.id (측정 대상 유저)',
    value           DECIMAL(5,2)    NOT NULL                COMMENT '혈당값 (mg/dL)',
    measured_at     TIMESTAMP       NOT NULL                COMMENT '실제 측정 시각',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '서버 기록 시각',
    PRIMARY KEY (id),
    KEY idx_glucose_user_time (user_id, measured_at),
    CONSTRAINT fk_glucose_user FOREIGN KEY (user_id) REFERENCES users (id)
) COMMENT '혈당 측정 원시 데이터';

-- ---------------------------------------------------------------------
-- 4. meal_records  (식사 기록)
--    이 시점부터 2시간의 glucose_records를 시간 기준으로 조회 → 혈당 반응 분석
-- ---------------------------------------------------------------------
CREATE TABLE meal_records (
    id                  INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id             INT             NOT NULL                COMMENT 'FK → users.id (식사 기록 유저)',
    food_id             INT             NOT NULL                COMMENT 'FK → foods.id (매칭된 음식, 수동 입력 시 NULL 정책은 도메인 결정)',
    image_origin_name   VARCHAR(255)    NULL                    COMMENT '유저 업로드 원본 파일명 (다운로드 표시용)',
    image_storage_key   VARCHAR(255)    NULL                    COMMENT 'S3 등 스토리지 식별자 (실제 접근용)',
    is_processed        BOOLEAN         NOT NULL DEFAULT FALSE,
    recorded_at         TIMESTAMP       NOT NULL                COMMENT '실제 식사 시각',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_meal_user_time (user_id, recorded_at),
    CONSTRAINT fk_meal_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_meal_food FOREIGN KEY (food_id) REFERENCES foods (id)
) COMMENT '식사 기록';

-- ---------------------------------------------------------------------
-- 5. meal_glucose_responses  (식후 혈당 반응)
--    meal_records 1건당 response 1건 (1:1).
--    baseline / peak 는 glucose_records 의 특정 row 를 참조.
-- ---------------------------------------------------------------------
CREATE TABLE meal_glucose_responses (
    id                      INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id                 INT             NOT NULL                COMMENT 'FK → users.id',
    meal_id                 INT             NOT NULL                COMMENT 'FK → meal_records.id',
    baseline_glucose_id     BIGINT          NOT NULL                COMMENT 'FK → glucose_records.id (식전 혈당)',
    peak_glucose_id         BIGINT          NOT NULL                COMMENT 'FK → glucose_records.id (2시간 내 최고 혈당)',
    slope                   DECIMAL(3,1)    NOT NULL                COMMENT '상승 기울기 (mg/dL/min) — 등급 산출 기준',
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_response_meal (meal_id),
    KEY idx_response_user (user_id),
    CONSTRAINT fk_response_user     FOREIGN KEY (user_id)             REFERENCES users (id),
    CONSTRAINT fk_response_meal     FOREIGN KEY (meal_id)             REFERENCES meal_records (id),
    CONSTRAINT fk_response_baseline FOREIGN KEY (baseline_glucose_id) REFERENCES glucose_records (id),
    CONSTRAINT fk_response_peak     FOREIGN KEY (peak_glucose_id)     REFERENCES glucose_records (id)
) COMMENT '식후 혈당 반응';

-- ---------------------------------------------------------------------
-- 6. glucose_predictions  (식전 예측)
--    "이걸 먹으면 혈당이 어떻게 될까" AI 시뮬레이션 결과
-- ---------------------------------------------------------------------
CREATE TABLE glucose_predictions (
    id              INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id         INT             NOT NULL                COMMENT 'FK → users.id (예측 대상 유저)',
    food_id         INT             NULL                    COMMENT 'FK → foods.id (예측 대상 음식)',
    food_name       VARCHAR(100)    NULL                    COMMENT '음식 DB에 없는 데이터인 경우 수기 입력',
    predicted_curve JSON            NOT NULL                COMMENT '예측 곡선 [{time, value}, ...]',
    predicted_peak  DECIMAL(5,2)    NULL                    COMMENT '예측 최고 혈당',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_prediction_user_time (user_id, created_at),
    CONSTRAINT fk_prediction_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_prediction_food FOREIGN KEY (food_id) REFERENCES foods (id)
) COMMENT '식전 혈당 예측';

-- ---------------------------------------------------------------------
-- 7. user_food_grades  (유저별 음식 성적표)
--    meal_glucose_responses.slope 의 평균을 캐싱한 등급 테이블
-- ---------------------------------------------------------------------
CREATE TABLE user_food_grades (
    id          INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id     INT             NOT NULL                COMMENT 'FK → users.id',
    food_id     INT             NOT NULL                COMMENT 'FK → foods.id',
    avg_slope   DECIMAL(3,1)    NOT NULL                COMMENT '식후 혈당 응답 slope의 평균',
    grade       CHAR(1)         NOT NULL                COMMENT 'S / A / B / C / D',
    meal_count  INT             NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_food (user_id, food_id),
    CONSTRAINT fk_grade_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_grade_food FOREIGN KEY (food_id) REFERENCES foods (id)
) COMMENT '유저별 음식 성적표';

-- ---------------------------------------------------------------------
-- 8. weekly_reports  (주간 리포트)
--    LLM 요약/제안 + TIR/TAR/TBR 통계, PDF S3 키 보유
-- ---------------------------------------------------------------------
CREATE TABLE weekly_reports (
    id                  INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id             INT             NOT NULL                COMMENT 'FK → users.id (리포트 소유 유저)',
    week_start          DATE            NULL                    COMMENT '리포트 커버 시작일',
    avg_glucose         DECIMAL(5,2)    NULL                    COMMENT '주간 평균 혈당 (mg/dL)',
    min_glucose         DECIMAL(5,2)    NULL                    COMMENT '주간 최저 혈당 (mg/dL)',
    max_glucose         DECIMAL(5,2)    NULL                    COMMENT '주간 최고 혈당 (mg/dL)',
    glucose_sd          DECIMAL(5,2)    NULL                    COMMENT '혈당 표준편차 (변동 폭)',
    target              DECIMAL(5,2)    NULL                    COMMENT '목표 범위 내 비율 TIR (%)',
    time_above_range    DECIMAL(5,2)    NULL                    COMMENT '고혈당 비율 TAR (%)',
    time_below_range    DECIMAL(5,2)    NULL                    COMMENT '저혈당 비율 TBR (%)',
    ai_summary          TEXT            NULL                    COMMENT 'LLM 생성 주간 요약',
    ai_suggest          TEXT            NULL                    COMMENT 'LLM 생성 개선 제안',
    pdf_key             VARCHAR(100)    NOT NULL                COMMENT 'S3 PDF 키 (예: reports/user_1/week_1.pdf)',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_report_user_week (user_id, week_start),
    CONSTRAINT fk_report_user FOREIGN KEY (user_id) REFERENCES users (id)
) COMMENT '주간 리포트';

-- ---------------------------------------------------------------------
-- 9. weekly_foods  (주간 리포트의 GOOD/BAD 음식 목록)
-- ---------------------------------------------------------------------
CREATE TABLE weekly_foods (
    id                  INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    report_id           INT             NOT NULL                COMMENT 'FK → weekly_reports.id',
    food_id             INT             NOT NULL                COMMENT 'FK → foods.id',
    type                VARCHAR(4)      NOT NULL                COMMENT 'GOOD / BAD',
    image_storage_key   VARCHAR(255)    NOT NULL                COMMENT '주 중 마지막에 먹은 음식 사진',
    avg_slope           DECIMAL(3,1)    NOT NULL                COMMENT '해당 주 해당 음식 평균 기울기',
    PRIMARY KEY (id),
    KEY idx_weekly_food_report (report_id),
    CONSTRAINT fk_weekly_food_report FOREIGN KEY (report_id) REFERENCES weekly_reports (id),
    CONSTRAINT fk_weekly_food_food   FOREIGN KEY (food_id)   REFERENCES foods (id)
) COMMENT '주간 리포트 음식 목록';

-- ---------------------------------------------------------------------
-- 10. notification_tokens  (FCM 푸시 토큰)
-- ---------------------------------------------------------------------
CREATE TABLE notification_tokens (
    id          INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id     INT             NOT NULL                COMMENT 'FK → users.id (토큰 소유 유저)',
    token       VARCHAR(200)    NOT NULL                COMMENT 'FCM 토큰 값',
    device_type VARCHAR(10)     NOT NULL                COMMENT 'android / watch',
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE   COMMENT '로그아웃 시 false',
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_noti_user_active (user_id, is_active),
    CONSTRAINT fk_noti_user FOREIGN KEY (user_id) REFERENCES users (id)
) COMMENT 'FCM 푸시 토큰';

-- ---------------------------------------------------------------------
-- 11. alerts  (알림 기록)
--    HIGH/LOW/SOS/WEEKLY_REPORT 4종.
--    혈당 알림이면 glucose_record_id, 리포트 알림이면 weekly_report_id.
-- ---------------------------------------------------------------------
CREATE TABLE alerts (
    id                  INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id             INT             NOT NULL                COMMENT 'FK → users.id (알림 대상)',
    alert_type          VARCHAR(20)     NOT NULL                COMMENT 'HIGH / LOW / SOS / WEEKLY_REPORT',
    glucose_record_id   BIGINT          NULL                    COMMENT 'FK → glucose_records.id (HIGH/LOW/SOS 트리거 측정)',
    weekly_report_id    INT             NULL                    COMMENT 'FK → weekly_reports.id (리포트 알림 대상)',
    message             VARCHAR(100)    NULL                    COMMENT '알림 메시지 본문',
    latitude            DECIMAL(9,6)    NULL                    COMMENT 'SOS 발송 시 GPS 위도',
    longitude           DECIMAL(9,6)    NULL                    COMMENT 'SOS 발송 시 GPS 경도',
    is_read             BOOLEAN         NOT NULL DEFAULT FALSE  COMMENT '읽음/안읽음 + SOS 미응답 에스컬레이션',
    resolved_at         TIMESTAMP       NULL                    COMMENT '혈당 정상 복귀 / SOS 응답 / 사용자 확인',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP       NULL                    COMMENT '소프트 삭제',
    PRIMARY KEY (id),
    KEY idx_alert_user_time (user_id, created_at),
    CONSTRAINT fk_alert_user    FOREIGN KEY (user_id)           REFERENCES users (id),
    CONSTRAINT fk_alert_glucose FOREIGN KEY (glucose_record_id) REFERENCES glucose_records (id),
    CONSTRAINT fk_alert_report  FOREIGN KEY (weekly_report_id)  REFERENCES weekly_reports (id)
) COMMENT '알림 기록';

-- ---------------------------------------------------------------------
-- 12. ward_guardian  (피보호자-보호자 매핑)
--    같은 user_id 내에서 priority 중복 금지 (작을수록 우선)
-- ---------------------------------------------------------------------
CREATE TABLE ward_guardian (
    id          INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    ward_id     INT             NOT NULL                COMMENT 'FK → users.id (피보호자)',
    guardian_id INT             NOT NULL                COMMENT 'FK → users.id (보호자)',
    relation    VARCHAR(10)     NULL                    COMMENT '부모 / 배우자 / 자녀 등',
    priority    TINYINT         NOT NULL                COMMENT 'SOS 연락 순서 (0이 1순위, 작을수록 먼저)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ward_priority (ward_id, priority),
    KEY idx_ward (ward_id),
    KEY idx_guardian (guardian_id),
    CONSTRAINT fk_wg_ward     FOREIGN KEY (ward_id)     REFERENCES users (id),
    CONSTRAINT fk_wg_guardian FOREIGN KEY (guardian_id) REFERENCES users (id)
) COMMENT '피보호자-보호자 관계';

-- ---------------------------------------------------------------------
-- 13. guardian_notifications  (보호자 SOS 발송 이력)
--    어떤 alert로 인해 어떤 보호자에게 발송됐는지 추적, 미응답 판정 기준
-- ---------------------------------------------------------------------
CREATE TABLE guardian_notifications (
    id                  INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    alert_id            INT             NOT NULL                COMMENT 'FK → alerts.id (어떤 SOS인지)',
    guard_relation_id   INT             NOT NULL                COMMENT 'FK → ward_guardian.id (알림 받는 보호자 식별)',
    sent_at             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created_at 역할, 미응답 판정 기준',
    responded_at        TIMESTAMP       NULL                    COMMENT 'NULL이면 미응답',
    PRIMARY KEY (id),
    KEY idx_gn_alert (alert_id),
    KEY idx_gn_relation (guard_relation_id),
    CONSTRAINT fk_gn_alert    FOREIGN KEY (alert_id)          REFERENCES alerts (id),
    CONSTRAINT fk_gn_relation FOREIGN KEY (guard_relation_id) REFERENCES ward_guardian (id)
) COMMENT '보호자 알림 발송 이력';

-- ---------------------------------------------------------------------
-- 14. medications_records  (복약 기록)
-- ---------------------------------------------------------------------
CREATE TABLE medications_records (
    id          INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id     INT             NOT NULL                COMMENT 'FK → users.id (복용 유저)',
    memo        VARCHAR(200)    NULL                    COMMENT '약 이름, 세부사항 등',
    taken_at    TIMESTAMP       NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_med_user_time (user_id, taken_at),
    CONSTRAINT fk_med_user FOREIGN KEY (user_id) REFERENCES users (id)
) COMMENT '복약 기록';

-- ---------------------------------------------------------------------
-- 15. sleep_records  (수면 기록)
-- ---------------------------------------------------------------------
CREATE TABLE sleep_records (
    id          INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id     INT             NOT NULL                COMMENT 'FK → users.id (수면 유저)',
    started_at  TIMESTAMP       NOT NULL,
    ended_at    TIMESTAMP       NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sleep_user_time (user_id, started_at),
    CONSTRAINT fk_sleep_user FOREIGN KEY (user_id) REFERENCES users (id)
) COMMENT '수면 기록';

-- ---------------------------------------------------------------------
-- 16. exercise_records  (운동 기록)
-- ---------------------------------------------------------------------
CREATE TABLE exercise_records (
    id              INT             NOT NULL AUTO_INCREMENT COMMENT 'PK',
    user_id         INT             NOT NULL                COMMENT 'FK → users.id (운동 유저)',
    exercise_type   VARCHAR(20)     NOT NULL                COMMENT 'WALKING / RUNNING / CYCLING 등',
    calories        DECIMAL(6,2)    NULL                    COMMENT '소모 칼로리 (kcal)',
    started_at      TIMESTAMP       NOT NULL,
    ended_at        TIMESTAMP       NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ex_user_time (user_id, started_at),
    CONSTRAINT fk_ex_user FOREIGN KEY (user_id) REFERENCES users (id)
) COMMENT '운동 기록';

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 관계 요약
-- ---------------------------------------------------------------------
-- users (1) ──┬─ (N) glucose_records
--             ├─ (N) meal_records ──── (0..1) foods
--             ├─ (N) meal_glucose_responses
--             ├─ (N) glucose_predictions ── (0..1) foods
--             ├─ (N) user_food_grades ── (1) foods
--             ├─ (N) weekly_reports ── (N) weekly_foods ── (1) foods
--             ├─ (N) notification_tokens
--             ├─ (N) alerts
--             └─ (N) ward_guardian [ward_id] ── (N) guardian_notifications
--                 (N) ward_guardian [guardian_id]
-- =====================================================================
