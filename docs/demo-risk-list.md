# S309 시연 리스크 리스트 (시나리오 기반 압축본)

- **점검일**: 2026-05-18
- **시연 대본**: 카톡 키보드 마라탕 → 라면 혈당 예측 → 키키 음성 답변 (3컷)

---

## 시연 대본 ↔ 리스크 매핑

| 컷    | 시연 동작                    | 직접 차단 가능 리스크                                         |
| ----- | ---------------------------- | ------------------------------------------------------------- |
| CUT 1 | 카톡 키보드 "마라탕" → D등급 | IME 미활성, 음식 DB 시드 누락                                 |
| CUT 2 | "라면" 혈당 예측 화면        | FE OkHttp 무한 대기 (네트워크 불안정 시 노출)                 |
| CUT 3 | 하이 키키 → 답변             | 마이크 권한 누락, wake word 무반응                            |
| 전 컷 | —                            | **시연 24시간 내 컨테이너 재배포·재시작** (현 정상 상태 깨짐) |

---

## 🔴 P0 — 시연 즉시 차단 가능

### 시연 단말 측 (사람이 챙김)

| 항목         | 내용                                                                                                 | 액션                                                      |
| ------------ | ---------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| IME 활성화   | GlucoFit 커스텀 키보드가 카톡 기본 키보드로 설정돼 있어야 CUT 1 가능                                 | 단말 설정 → 키보드 → GlucoFit 선택 + 카톡에서 활성화 확인 |
| 마이크 권한  | RECORD_AUDIO 누락 시 `wakeWordManager.start()` 미호출 (`MainActivity.kt:118-121`) → wake word 무반응 | 단말 권한 사전 부여 + "하이 키키" 1회 트리거 테스트       |
| 시연 계정    | 토큰 만료 시 로그인 화면 복귀로 시연 흐름 끊김                                                       | 시연 30분 전 로그인 갱신                                  |
| 음식 DB 시드 | "마라탕"(D등급), "라면" 응답이 없으면 CUT 1 등급 미표시 + CUT 2 예측 진입 불가                       | 사전에 키보드 입력 1회 + 예측 화면 진입 1회 리허설        |

### 운영 룰 위반

| 항목                                  | 위험                                                                                           |
| ------------------------------------- | ---------------------------------------------------------------------------------------------- |
| 시연 24시간 내 컨테이너 재배포·재시작 | 현재 정상 동작 상태가 깨질 수 있음 (env 누락 사고, 이미지 갱신 사고, AI 모델 재로딩 120s 지연) |
| Jenkins 자동 빌드                     | release 브랜치 push 차단 — 자동 트리거 막기                                                    |
| Blue/Green 토글                       | 시연 중 우발 토글로 미준비 슬롯 노출 위험                                                      |

---

## ⚙ 환경 확인 항목 (이미 정상 동작 가정, 시연 전 검증만)

운영 환경에 키 주입 완료 + 컨테이너 동작 중. 시연 직전 한 번만 확인.

| 항목               | 검증 명령                                                          | 기대 결과                                |
| ------------------ | ------------------------------------------------------------------ | ---------------------------------------- |
| Anthropic 키       | `docker exec s309-ai-{color} env \| grep ANTHROPIC_API_KEY`        | 실값                                     |
| Anthropic 도달     | `docker exec s309-ai-{color} curl -sI https://api.anthropic.com`   | 200/401/403 어떤 응답이든 도달이면 OK    |
| AGENT_API_KEY 일치 | Backend·AI 양쪽 env 같은 값                                        | 폴백 `dev-agent-key-change-in-prod` 아님 |
| 컨테이너 healthy   | `docker inspect --format='{{.Name}} {{.State.Health.Status}}' ...` | 모두 `healthy`                           |

---

## 🟡 P1 — 시연 중 어색함 가능

| ID      | 위치                                                      | 내용                                                                                                   | 회피책                                                    |
| ------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ | --------------------------------------------------------- |
| FE-P1-A | `frontend/.../di/AppModule.kt:41-48`                      | 모든 OkHttpClient에 timeout 미설정 → 백엔드/AI 응답 지연 시 **무한 스피너** (CUT 2 라면 예측에서 노출) | 안정 와이파이 + 백엔드/AI healthy 사전 확인으로 회피      |
| FE-P1-B | `frontend/.../data/network/TokenAuthenticator.kt:54,79`   | refresh 호출마다 `OkHttpClient()` 신규 + timeout 미설정 → 세션 만료 시 hang 가능                       | 시연 30분 전 로그인 갱신으로 세션 만료 시점 우회          |
| AI-P1-A | `ai/app/agent/fallback.py:17`                             | LLM 타임아웃 30s × 3회 재시도 → 최대 ~97초 후 폴백. CUT 3 화면이 90초 이상 멈출 가능                   | 사전 풀 시나리오 리허설로 평균 응답 시간(5~10s 예상) 확인 |
| AI-P1-B | `ai/app/agent/tools.py:196`                               | `schedule_followup` 인자 무시하고 항상 1분 후 재호출 — Backend debounce 없으면 알림 폭주               | **시연 동선에서 "30분 뒤" 버튼 누르지 않기**              |
| BE-P1-A | `backend/.../domain/cgm/controller/CgmController.java:50` | `LocalDateTime.now()` 서버 TZ 의존. 컨테이너 UTC + 클라이언트 KST 시 9시간 차이로 400                  | 컨테이너 TZ=`Asia/Seoul` (compose에 명시) — 검증만        |

---

## ⚪ 시연 후 처리 (코드 변경 필요, 시연 막진 않음)

### 보안 / 운영

- `AGENT_API_KEY` 폴백 제거 (`docker-compose.{blue,green}.yml:50`, `application.yml:53`, `ai/app/agent/tools.py`) → .env 누락 시 컨테이너 기동 실패하도록 강제
- `AgentApiKeyFilter.java:39` `.equals()` → `MessageDigest.isEqual()` (timing attack)
- `app/main.py` Backend→AI 인바운드 `X-Agent-Api-Key` 검증 미들웨어 추가
- `/health` 가 모델 상태/LLM 키 부재 무관 항상 UP → `/inference/glucose/health` 와 연동
- `tools.py:196` `delay_minutes = 1` 시연 코드 → 실제 인자 반영으로 복구
- n8n 5678 포트 `0.0.0.0` → `127.0.0.1:5678:5678` (`docker-compose.infra.yml:65`)
- PostgreSQL 자동 백업 cron 추가
- `deploy-bg.sh:35` — `ai-{next}` healthy 대기 추가
- `Jenkinsfile:73,90` — Mattermost Webhook URL을 Credentials로 이동

### 코드 품질

- `AppModule.kt`, `TokenAuthenticator.kt` OkHttpClient timeout 명시 (`connect 5s / read 15s / write 15s`)
- `S309Application.kt:31-35` `workManagerConfiguration` getter null/init 가드
- `HttpLoggingInterceptor.Level.BODY` release 시 NONE으로 (토큰 logcat 평문 방지)
- `TokenManager` 평문 SharedPreferences → EncryptedSharedPreferences
- Flyway V11이 V12 이후 추가된 파일명 흐름 — 클린 DB 풀 마이그레이션 리허설
- `requirements.txt` vs `pyproject.toml` 의존성 버전 불일치 정리 (pandas, scikit-learn, requests, python-multipart)

---

## 🚨 발표 직전 액션 우선순위 Top 3

1. **운영 환경 4개 검증 명령** 1분 안에 통과 (Anthropic 키 · Anthropic 도달 · AGENT_API_KEY 일치 · 컨테이너 healthy)
2. **시연 단말 챙기기**: IME + 마이크 + 시연 계정 + "마라탕"/"라면" 응답 사전 테스트
3. **시연 24시간 내 컨테이너 재배포·재시작 절대 금지** (현 정상 상태 보존)

---

## 시연 중 비상 대응

| 증상                           | 의심 리스크                             | 즉시 대응                                         |
| ------------------------------ | --------------------------------------- | ------------------------------------------------- |
| 카톡 키보드 등급 미표시        | IME 비활성, 음식 DB 시드 누락           | 키보드 재선택 → 음식 검색 화면 우회               |
| 라면 예측 화면 무한 스피너     | FE-P1-A OkHttp timeout, 네트워크 불안정 | 사전 녹화 영상으로 전환                           |
| 키키 답변이 어색한 폴백 메시지 | LLM 호출 실패 (네트워크 일시 단절)      | 사전 녹화 영상으로 전환                           |
| "하이 키키" 무반응             | 마이크 권한, wake word 트리거 실패      | 권한 재확인 → 앱 재시작 → 마지막은 사전 녹화      |
| 백엔드 500 연발                | 컨테이너 일시 이상                      | Blue/Green PREV 슬롯 롤백 (60초 grace 이내)       |
| 전반 실패                      | -                                       | 사전 녹화 영상 1개 — 발표 흐름 유지 (최후의 보루) |
