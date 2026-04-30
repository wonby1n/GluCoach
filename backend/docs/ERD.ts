export interface WeeklyReports {
    id: number; // PK / INT
    user_id: number; // FK / 리포트 소유 유저 / INT
    week_start: Date | null; // 이 리포트가 커버하는 시작일 / DATE
    avg_glucose: number | null; // 주간 평균 혈당 (mg/dL) / DECIMAL(5,2)
    min_glucose: number | null; // 주간 최저 혈당 (mg/dL) / DECIMAL(5,2)
    max_glucose: number | null; // 주간 최고 혈당 (mg/dL) / DECIMAL(5,2)
    glucose_sd: number | null; // 혈당 표준편차 - 변동 폭 지표 (mg/dL) / DECIMAL(5,2)
    target: number | null; // 목표 범위 내 비율 TIR - time in range (%) / DECIMAL(5,2)
    time_above_range: number | null; // 고혈당 비율 TAR (%) / DECIMAL(5,2)
    time_below_range: number | null; // 저혈당 비율 TBR (%) / DECIMAL(5,2)
    ai_summary: string | null; // LLM 생성 주간 요약 / TEXT
    ai_suggest: string | null; // LLM 생성 개선 제안 / TEXT
    pdf_key: string; // pdf 파일명, 사용자 정보가 조합된 key값 | https://s3.amazonaws.com/bucket/reports/user_1/week_1.pdf 에서 reports/user_1/week_1.pdf 를 의미 / VARCHAR(100)
    created_at: Date; // TIMESTAMP
    updated_at: Date; // TIMESTAMP
}

export interface WeeklyFoods {
    id: number; // PK / INT
    report_id: number; // FK / INT
    food_id: number; // FK / INT
    type: string; // GOOD / BAD로 나뉨. 좋은 음식인지, 나쁜 음식인지 결정 / VARCHAR(4)
    image_storage_key: string; // 주 중 마지막에 먹은 음식 사진 / VARCHAR(255)
    avg_slope: number; // 해당 주 해당 음식의 평균 기울기 / DECIMAL(3,1)
}

export interface GlucosePredictions {
    id: number; // PK / INT
    user_id: number; // FK / 예측 대상 유저 / INT
    food_id: number | null; // FK / 예측 대상 음식 / INT
    food_name: string | null; // 음식 DB에 없는 데이터인 경우 수기로 입력 / VARCHAR(100)
    predicted_curve: any; // 예측 곡선 [{time, value}, ...] / JSONB
    predicted_peak: number | null; // 예측 최고 혈당 / DECIMAL(5,2)
    created_at: Date; // TIMESTAMP
}

export interface MealGlucoseResponses {
    id: number; // PK / INT
    user_id: number; // FK / 혈당 반응 기록 유저 / INT
    meal_id: number; // FK / 반응 식사 / INT
    baseline_glucose_id: number; // FK / 식전 혈당 / BIGINT
    peak_glucose_id: number; // FK / 2시간 내 최고 혈당 / BIGINT
    slope: number; // 상승 기울기 (mg/dL/min) — 등급 산출 기준 / DECIMAL(3,1)
    created_at: Date; // TIMESTAMP
    updated_at: Date; // TIMESTAMP
}

export interface Users {
    id: number; // PK / INT
    email: string; // 이메일 (탈퇴 시 deleted_{uuid}@withdrawn.local) / VARCHAR(255)
    password: string | null; // 비밀번호 (소셜 로그인 시 NULL) / VARCHAR(60)
    provider: string; // email / kakao / google(예시임) / VARCHAR(10)
    name: string; // 한글 이름 풀네임 + 영문 닉네임 수용 / VARCHAR(20)
    age: number | null; // TINYINT
    gender: string | null; // male / female / VARCHAR(6)
    phone: string; // 국제번호, 하이픈 포함 / VARCHAR(20)
    height: number | null; // 키 (cm) — 예측 API용 / DECIMAL(4,1)
    weight: number | null; // 체중 (kg) — 예측 API용 / DECIMAL(4,1)
    diabetes_type: string | null; // NORMAL/ T1D/ T2D / VARCHAR(10)
    is_medicated: boolean | null; // 당뇨약 혹은 인슐린 투약 대상자인지 확인 / BOOLEAN
    target_low: number | null; // 목표 하한 혈당 (mg/dL) / DECIMAL(5,2)
    target_high: number | null; // 목표 상한 혈당 (mg/dL) / DECIMAL(5,2)
    week_start_day: number; // 다음 리포트 시작 요일 세팅 / TINYINT
    created_at: Date; // TIMESTAMP
    updated_at: Date; // TIMESTAMP
    deleted_at: Date | null; // 탈퇴 시간 (소프트 삭제) / TIMESTAMP
}

export interface UserFoodGrades {
    id: number; // PK / INT
    user_id: number; // FK / INT
    food_id: number; // FK / UNIQUE: (user_id, food_id) / INT
    avg_slope: number; // 식후 혈당 응답에서 가져온 slope값의 평균 / DECIMAL(3,1)
    grade: string; // 평균 기울기에 따른 등급 S/A/B/C/D 로 구분 / CHAR(1)
    meal_count: number; // INT
    created_at: Date; // TIMESTAMP
    updated_at: Date; // TIMESTAMP
}

export interface Alerts {
    id: number; // PK / INT
    user_id: number; // FK / 알림 대상 / INT
    alert_type: string; // HIGH / LOW / SOS / WEEKLY_REPORT / VARCHAR(20)
    glucose_record_id: number | null; // FK / 혈당 알림(HIGH/LOW/SOS) 트리거 측정 / BIGINT
    weekly_report_id: number | null; // FK / 리포트 알림 대상 / INT
    message: string | null; // 알림 메시지 본문 / VARCHAR(100)
    latitude: number | null; // SOS 발송 시 GPS 위도 / DECIMAL(9, 6)
    longitude: number | null; // SOS 발송 시 GPS 경도 / DECIMAL(9, 6)
    is_read: boolean; // 알림 목록에서 "읽음/안읽음" 표시 + SOS 미응답 시 에스컬레이션 작동 / BOOLEAN
    resolved_at: Date | null; // 혈당 정상 복귀 / SOS 응답 / 사용자 확인 / TIMESTAMP
    created_at: Date; // TIMESTAMP
    deleted_at: Date | null; // 소프트 삭제 / TIMESTAMP
}

export interface Foods {
    id: number; // PK / INT
    food_name: string; // 음식 이름 / VARCHAR(100)
    calories: number | null; // 칼로리 (kcal) / DECIMAL(6,2)
    carbohydrate: number | null; // 탄수화물 (g) / DECIMAL(5,2)
    protein: number | null; // 단백질 (g) / DECIMAL(5,2)
    fat: number | null; // 지방 (g) / DECIMAL(5,2)
    sugar: number | null; // 당류 (g) / DECIMAL(5,2)
    fiber: number | null; // 식이섬유 (g) / DECIMAL(5,2)
    serving_size: number | null; // 1회 제공량 (g) / DECIMAL(5,2)
    is_customized: boolean; // BOOLEAN
    created_at: Date; // TIMESTAMP
    updated_at: Date; // TIMESTAMP
}

export interface MealRecords {
    id: number; // PK / INT
    user_id: number; // FK / 식사 기록하는 유저 / INT
    food_id: number; // FK / 매칭된 음식 (수동 입력 시 NULL) / INT
    image_origin_name: string | null; // 유저가 업로드한 원본 파일명 (다운로드 시 표시용) / VARCHAR(255)
    image_storage_key: string | null; // S3 등 스토리지 저장 식별자 (실제 접근용) / VARCHAR(255)
    is_processed: boolean; // BOOLEAN
    recorded_at: Date; // TIMESTAMP
    created_at: Date; // TIMESTAMP
    updated_at: Date; // TIMESTAMP
}

export interface NotificationTokens {
    id: number; // PK / INT
    user_id: number; // FK / 토큰을 소유한 사용자 / INT
    token: string; // FCM 토큰 값 / VARCHAR(200)
    device_type: string; // android / watch / VARCHAR(10)
    is_active: boolean; // 로그아웃 시 false / BOOLEAN
    created_at: Date; // TIMESTAMP
    updated_at: Date; // TIMESTAMP
}

export interface GuardianNotifications {
    id: number; // PK / INT
    alert_id: number; // FK / 어떤 SOS 때문에 알림이 발송됐는지 추적 / INT
    guard_relation_id: number; // FK / 알림 받는 보호자를 식별 / INT
    sent_at: Date; // 'created_at'과 같은 역할, 미응답 판정 기준 / TIMESTAMP
    responded_at: Date | null; // NULL이면 미응답 / TIMESTAMP
}

export interface WardGuardian {
    id: number; // PK / INT
    ward_id: number; // FK / INT
    guardian_id: number; // FK / INT
    relation: string | null; // 부모/배우자/자녀 등 / VARCHAR(10)
    priority: number; // 피보호자의 보호자 우선순위, SOS 연락 순서 (0이 1순위, 작을수록 먼저), 같은 유저 내에서 priority 중복 금지 / TINYINT
}

export interface GlucoseRecords {
    id: number; // PK / BIGINT
    user_id: number; // FK / 측정 대상 유저 / INT
    value: number; // 혈당값 (mg/dL) / DECIMAL(5,2)
    measured_at: Date; // 실제 측정 시각 / TIMESTAMP
    created_at: Date; // 서버 기록 시각 / TIMESTAMP
}

export interface MedicationsRecords {
    id: number; // PK / INT
    user_id: number; // FK / 복용 유저 / INT
    memo: string | null; // 약 이름, 세부사항 등 기록 / VARCHAR(200)
    taken_at: Date; // TIMESTAMP
    created_at: Date; // TIMESTAMP
    updated_at: Date; // TIMESTAMP
}

export interface SleepRecords {
    id: number; // PK / INT
    user_id: number; // FK / 수면 유저 / INT
    started_at: Date; // TIMESTAMP
    ended_at: Date; // TIMESTAMP
    created_at: Date; // TIMESTAMP
}

export interface ExerciseRecords {
    id: number; // PK / INT
    user_id: number; // FK / 운동 유저 / INT
    exercise_type: string; // 운동 종류 (WALKING / RUNNING / CYCLING 등) / VARCHAR(20)
    calories: number | null; // 소모 칼로리 (kcal) / DECIMAL(6, 2)
    started_at: Date; // TIMESTAMP
    ended_at: Date; // TIMESTAMP
    created_at: Date; // TIMESTAMP
}
