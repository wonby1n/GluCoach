# S309 시연 리스크 리스트 (시나리오 기반 압축본)

- **점검일**: 2026-05-18
- **시연 대본**: 카톡 키보드 마라탕 → 라면 혈당 예측 → 키키 음성 답변 (3컷)

---

## 시연 대본 ↔ 리스크 매핑

| 컷    | 시연 동작                    | 직접 차단 가능 리스크                                                                               |
| ----- | ---------------------------- | --------------------------------------------------------------------------------------------------- |
| CUT 1 | 카톡 키보드 "마라탕" → D등급 | IME 미활성, 음식 DB 시드 누락, 백엔드 음식 검색 API 실패, BE 컨테이너 기동 실패                     |
| CUT 2 | "라면" 혈당 예측 화면        | AI 모델 미로드, `/inference/glucose/meal` 실패, FE OkHttp 무한 대기                                 |
| CUT 3 | 하이 키키 → 답변             | ANTHROPIC_API_KEY 누락, `api.anthropic.com` 차단, AGENT_API_KEY 폴백, 마이크 권한, wake word 무반응 |

---

## 🔴 P0 — 시연 즉시 차단 가능 (이거 막혀 있으면 시연 못 함)

### CUT 3 (키키 음성 답변) — 가장 위험

| ID           | 위치                                                                                                             | 내용                                                                                                                  | 발표 전 액션                                                                                            |
| ------------ | ---------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| **AI-P0-A**  | `ai/app/agent/food_recommend_agent_run.py:59` 외 2건                                                             | `ANTHROPIC_API_KEY=None` 시 `AuthenticationError` → **폴백 메시지만 발송**. CUT 3 클라이맥스 망함                     | `docker exec s309-ai-{color} env \| grep ANTHROPIC_API_KEY` — 실값 확인                                 |
| **AI-P0-B**  | `ai/app/agent/morning_agent_run.py:35`, `postmeal_agent_run.py:30`, `food_recommend_agent_run.py:30`             | `BASE_URL = "https://api.anthropic.com"` (주석엔 GMS 프록시라 적힘 — 실제는 공개 API). 차단 환경이면 LLM 전부 실패    | `docker exec s309-ai-{color} curl -sI https://api.anthropic.com` 확인. 차단 시 GMS 프록시 URL 즉시 교체 |
| **CROSS-P0** | `infra/docker-compose.{blue,green}.yml:50`, `backend/.../application.yml:53`, `ai/app/agent/tools.py:82,151,199` | `AGENT_API_KEY` 폴백 `dev-agent-key-change-in-prod` — `/api/agent/**` 인증 우회 가능 + Backend↔AI 통신 인증 꼬일 위험 | `infra/.env`에 강한 랜덤값 명시 후 양쪽 env 일치 확인                                                   |
| **FE-단말**  | 시연 단말                                                                                                        | RECORD_AUDIO 권한 누락 시 `wakeWordManager.start()` 미호출 (`MainActivity.kt:118-121`) → "하이 키키" 무반응           | 단말 권한 사전 부여 + 1회 wake word 트리거 테스트                                                       |

### CUT 1·CUT 2 (음식 입력 흐름)

| ID          | 위치                                           | 내용                                                                                                                                                     | 발표 전 액션                                                                      |
| ----------- | ---------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| **BE-P0-1** | `backend/.../config/FirebaseConfig.java:18-25` | `@Profile("!local")` + `firebase-service-account.json` 부재 시 `GoogleCredentials.fromStream(null)` → NPE → **백엔드 컨테이너 기동 실패** → 모든 컷 차단 | prod 이미지에 service-account 파일 포함 확인 (시연 안 하는 FCM이라도 기동은 필요) |
| **FE-단말** | 시연 단말                                      | GlucoFit 커스텀 IME가 카톡 기본 키보드로 설정 안 되어 있으면 CUT 1 시연 자체 불가                                                                        | 단말 설정 → 키보드 → GlucoFit 선택 + 카톡에서 활성화 확인                         |
| **데이터**  | 백엔드 음식 DB                                 | "마라탕"(D등급), "라면" 시드가 없으면 CUT 1 등급 미표시 + CUT 2 예측 진입 불가                                                                           | 사전에 키보드 입력 1회 + 예측 화면 진입 1회 리허설                                |

---

## 🟡 P1 — 시연 중 어색함 가능 (동선 내)

| ID      | 위치                                                      | 내용                                                                                                   | 회피책                                                           |
| ------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------- |
| FE-P1-A | `frontend/.../di/AppModule.kt:41-48`                      | 모든 OkHttpClient에 timeout 미설정 → 백엔드/AI 응답 지연 시 **무한 스피너** (CUT 2 라면 예측에서 노출) | 안정 와이파이 + 백엔드/AI healthy 사전 확인으로 회피             |
| FE-P1-B | `frontend/.../data/network/TokenAuthenticator.kt:54,79`   | refresh 호출마다 `OkHttpClient()` 신규 + timeout 미설정 + BASE_URL 하드코딩 → 세션 만료 시 hang 가능   | 시연 30분 전 로그인 갱신 → 시연 중 세션 만료 시점 우회           |
| AI-P1-A | `ai/app/agent/fallback.py:17`                             | LLM 타임아웃 30s × 3회 재시도 → 최대 ~97초 후 폴백 메시지. CUT 3 화면이 90초 이상 멈출 수 있음         | 사전 풀 시나리오 리허설로 평균 응답 시간(5~10s 예상) 확인        |
| AI-P1-B | `ai/app/agent/tools.py:196`                               | `schedule_followup` 인자 무시하고 항상 1분 후 재호출 — Backend debounce 없으면 알림 폭주               | **시연 동선에서 "30분 뒤" 버튼 누르지 않기**                     |
| BE-P1-A | `backend/.../domain/cgm/controller/CgmController.java:50` | `LocalDateTime.now()` 서버 TZ 의존. 컨테이너 UTC + 클라이언트 KST 시 9시간 차이로 400                  | 컨테이너 TZ=`Asia/Seoul` 확인 (compose에 명시되어 있음 — 검증만) |

---

## ⚪ 시연 후 처리 (코드 변경 필요, 시연 막진 않음)

### 보안 / 운영

- **CROSS**: `AGENT_API_KEY` 폴백 제거 (`docker-compose.{blue,green}.yml:50`, `application.yml:53`, `ai/app/agent/tools.py`) → .env 누락 시 컨테이너 기동 실패하도록 강제
- **BE-P0-3**: `AgentApiKeyFilter.java:39` `.equals()` → `MessageDigest.isEqual()` (timing attack)
- **AI**: `app/main.py` Backend→AI 인바운드 `X-Agent-Api-Key` 검증 미들웨어 추가
- **AI-P0-3**: `/health` 가 모델 상태/LLM 키 부재 무관 항상 UP → `/inference/glucose/health` 와 연동
- **AI-P0-1**: `tools.py:196` `delay_minutes = 1` 시연 코드 → 실제 인자 반영으로 복구
- **Infra**: n8n 5678 포트 `0.0.0.0` → `127.0.0.1:5678:5678` (`docker-compose.infra.yml:65`)
- **Infra**: PostgreSQL 자동 백업 cron 추가
- **Infra**: `deploy-bg.sh:35` — `ai-{next}` healthy 대기 추가
- **Infra**: `Jenkinsfile:73,90` — Mattermost Webhook URL을 Credentials로 이동

### 코드 품질

- **FE-P0-4·5**: `AppModule.kt`, `TokenAuthenticator.kt` OkHttpClient timeout 명시 (`connect 5s / read 15s / write 15s`)
- **FE-P0-2**: `S309Application.kt:31-35` `workManagerConfiguration` getter null/init 가드
- **FE-P2**: `HttpLoggingInterceptor.Level.BODY` release 시 NONE으로 (토큰 logcat 평문 방지)
- **FE-P2**: `TokenManager` 평문 SharedPreferences → EncryptedSharedPreferences
- **BE-P2**: Flyway V11이 V12 이후 추가된 파일명 흐름 — 클린 DB 풀 마이그레이션 리허설
- **AI-P2**: `requirements.txt` vs `pyproject.toml` 의존성 버전 불일치 정리 (pandas, scikit-learn, requests, python-multipart)

---

## 🚨 발표 직전 액션 우선순위 Top 5 (압축)

1. **AI-P0-A**: AI 컨테이너 `ANTHROPIC_API_KEY` 실주입 검증 — CUT 3 클라이맥스 사수
2. **AI-P0-B**: AI 컨테이너 → `api.anthropic.com` 도달 확인. 차단 시 BASE_URL을 GMS 프록시로 즉시 교체
3. **CROSS-P0**: `infra/.env`에 `AGENT_API_KEY` 강한 값 명시 → Backend+AI 양쪽 env 일치
4. **BE-P0-1**: prod 이미지에 `firebase-service-account.json` 포함 + 백엔드/AI/Nginx healthy 전수 확인
5. **시연 단말**: GlucoFit IME 활성화 + 마이크 권한 + "하이 키키" 1회 + 마라탕/라면 사전 응답 테스트

---

## 시연 중 비상 대응

| 증상                           | 의심 리스크                            | 즉시 대응                                           |
| ------------------------------ | -------------------------------------- | --------------------------------------------------- |
| 카톡 키보드 등급 미표시        | IME 비활성, 음식 DB 시드 누락          | 키보드 재선택 → 음식 검색 화면 우회                 |
| 라면 예측 화면 무한 스피너     | FE-P1-A OkHttp timeout, AI 모델 미로드 | 사전 녹화 영상으로 전환                             |
| 키키 답변이 어색한 폴백 메시지 | AI-P0-A 키 만료, AI-P0-B 차단          | 사전 녹화 영상으로 전환                             |
| "하이 키키" 무반응             | 마이크 권한, wake word 트리거 실패     | 권한 재확인 → 앱 재시작 → 마지막은 사전 녹화        |
| 백엔드 500 연발                | BE-P0-1 NPE, 컨테이너 다운             | 컨테이너 재시작 → 안 되면 Blue/Green PREV 슬롯 롤백 |
| 전반 실패                      | -                                      | 사전 녹화 영상 1개 — 발표 흐름 유지 (최후의 보루)   |
