# S309 시연 준비 체크리스트 (시나리오 기반 압축본)

- **점검일**: 2026-05-18
- **운영 도메인**: `https://k14s309.p.ssafy.io/`

---

## 시연 대본 ↔ 의존 컴포넌트

| 컷        | 시연 동작                                                | 핵심 컴포넌트                                                                                |
| --------- | -------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| **CUT 1** | 카톡 키보드에 "마라탕" 입력 → D등급 즉시 표시            | 커스텀 IME (`feature-glucofit`) · 음식 DB · 백엔드 음식 검색 API                             |
| **CUT 2** | 친구가 "라면" 추천 → 라면 혈당 예측 화면                 | 음식명 → 혈당 예측 (`/inference/glucose/meal`) · 식약처 API or 캐시                          |
| **CUT 3** | "하이 키키" → "네, 주인님" → "나 뭐 먹을까?" → 추천 답변 | Wake word (RECORD_AUDIO + `wakeWordManager`) · TTS · LLM agent (`/agent/command`, Anthropic) |

---

## ⚠ 시연 직전 운영 룰 (가장 중요)

운영 환경의 키 주입·컨테이너 동작은 이미 정상. **건드리지 않고 그 상태를 그대로 보존하는 것**이 최우선.

```
❌ 시연 24시간 이내 컨테이너 재배포·재시작 금지
❌ Blue/Green 토글 금지
❌ Jenkins 자동 빌드 트리거 차단 (release 브랜치 push 금지)
✅ 현재 잘 돌아가는 상태를 그대로 보존
```

---

## 🔴 D-Day 직전 점검

### A. 운영 환경 검증 (이미 정상 동작 — 한 번만 확인)

```
[ ] AI 컨테이너 ANTHROPIC_API_KEY env 주입 (실값)
       docker exec s309-ai-{color} env | grep ANTHROPIC_API_KEY

[ ] AI → api.anthropic.com 도달 OK (200/401/403 어떤 응답이든 도달이면 OK)
       docker exec s309-ai-{color} curl -sI https://api.anthropic.com

[ ] AGENT_API_KEY Backend·AI 양쪽 값 일치
       docker exec s309-backend-{color} env | grep AGENT_API_KEY
       docker exec s309-ai-{color}      env | grep AGENT_API_KEY

[ ] 백엔드 / AI / Nginx / Postgres / Redis 컨테이너 healthy
       docker inspect --format='{{.Name}} {{.State.Health.Status}}' \
         s309-backend-{color} s309-ai-{color} s309-nginx s309-postgres s309-redis
```

### B. 시연 단말 (사람이 직접 챙김 — 가장 차단 가능성 큼)

```
[ ] GlucoFit 커스텀 IME가 카톡 기본 키보드로 설정
       단말 설정 → 키보드 → GlucoFit 선택 + 카톡에서 활성화 확인
[ ] 마이크 권한 ON + "하이 키키" 1회 wake word 트리거 성공
       (RECORD_AUDIO 누락 시 wakeWordManager.start() 미호출 — MainActivity.kt:118-121)
[ ] 시연 계정 로그인 상태 + 토큰 유효 (시연 30분 전 갱신 권장)
[ ] 단말 화면 항상 켜둠 + 무음/방해금지 OFF (TTS 발화용)
```

### C. 데이터 / 시나리오 사전 테스트

```
[ ] "마라탕" 입력 → D등급 응답 확인
[ ] "라면" 혈당 예측 화면 응답 확인
[ ] 풀 시나리오 리허설 1회 (마라탕 → 라면 → 하이키키 끝까지 완주)
```

---

## 시점별 액션

### D-1 (시연 전날)

- [ ] HTTPS 인증서 만료일 확인: `echo | openssl s_client -connect k14s309.p.ssafy.io:443 2>/dev/null | openssl x509 -noout -dates`
- [ ] Jenkins job disable 또는 GitLab webhook 일시 차단
- [ ] PostgreSQL 수동 백업: `docker exec s309-postgres pg_dump -U $POSTGRES_USER $POSTGRES_DB > ~/backup-$(date +%Y%m%d-%H%M).sql`
- [ ] EC2 디스크 여유 > 20% (`df -h /`)
- [ ] **풀 시나리오 리허설 1회**

### D-Day 1시간 전

- [ ] 위 **A 운영 환경 검증** 전수 통과 (재시작·재배포는 절대 안 함)
- [ ] PREV 슬롯 컨테이너 살아있는지 확인 (60초 grace 내 즉시 롤백용)
- [ ] 시연 단말 충전 100% + 보조배터리 + 안정된 와이파이

### D-Day 10분 전 (단말 최종 점검)

- [ ] 위 **B 시연 단말** 전수 통과
- [ ] 카톡 앱 열어서 GlucoFit 키보드 활성화 (지구본 아이콘 전환)
- [ ] "하이 키키" 한 번 발화 → "네, 주인님" TTS 응답 확인

---

## CUT별 검증 시나리오 (사전 리허설용)

### CUT 1: 카톡 키보드 "마라탕" → D등급

```
[ ] 카톡 열기 → GlucoFit 키보드로 전환
[ ] "마라탕" 타이핑
[ ] 입력 즉시 등급 표시 영역에 "D등급" 노출
[ ] (실패 시 디버깅) keyboard → backend API 호출 로그, 음식 DB seed 데이터에 "마라탕" 있는지
```

### CUT 2: "라면" 혈당 예측 화면

```
[ ] 친구 카톡 메시지 "그럼 라면 ㄱㄱ?" 수신 (다른 단말 or 시연자 본인이 보내기)
[ ] 앱 진입 → 라면 혈당 예측 화면 진입
[ ] AI 응답 도착 → 그래프/수치 표시
[ ] (실패 시) /inference/glucose/meal 응답 로그, 모델 로딩 상태 확인
       curl -sk https://k14s309.p.ssafy.io/inference/glucose/health
```

### CUT 3: 키키 음성 답변

```
[ ] "하이 키키" 발화 → wake word 트리거
[ ] "네, 주인님" TTS 응답 정상 발화
[ ] "나 뭐 먹을까?" 발화 → STT 인식
[ ] /agent/command 호출 → LLM 답변 수신 (평균 5~10s 예상)
[ ] 답변 화면 표시 (오늘 혈당 + 점심 + 시간 컨텍스트 종합 추천)
```

---

## 🚨 시연 중 비상 대응

| 증상                             | 즉시 대응                                                                             |
| -------------------------------- | ------------------------------------------------------------------------------------- |
| 카톡 키보드에서 음식 등급 미표시 | 단말 자체 키보드 재선택 → 재발화 안 되면 백엔드 음식 검색 API 직접 호출 화면으로 우회 |
| 라면 혈당 예측 화면 무한 스피너  | 사전 녹화 영상으로 전환 (FE OkHttp timeout 미설정 — `AppModule.kt:41-48` 영향)        |
| "하이 키키" 무반응               | 마이크 권한 재확인 → 앱 재시작 → 마지막은 사전 녹화                                   |
| 키키 답변이 어색한 폴백 메시지   | 사전 녹화 영상으로 전환                                                               |
| AI 추론 전반 실패                | Blue/Green PREV 슬롯 롤백 (60초 grace 이내)                                           |
| 백엔드 500 연발                  | 컨테이너 재시작 → 안 되면 사전 녹화                                                   |

**최후의 보루**: 시연 전체 사전 녹화 영상 1개 — 발표자 단말에 미리 저장. 어떤 장애가 나도 발표 흐름은 유지.

---

## 시연 후 처리 (시연 막진 않지만 보안·품질상 필요)

- [ ] `AGENT_API_KEY` 폴백 제거 (`docker-compose.{blue,green}.yml:50`, `application.yml:53`, `ai/app/agent/tools.py`) → .env 누락 시 컨테이너 기동 실패하도록 강제
- [ ] `ai/app/agent/tools.py:196` `delay_minutes = 1` 시연 코드 → 실제 인자 반영으로 복구
- [ ] FE OkHttp timeout 명시 (`AppModule.kt`, `TokenAuthenticator.kt`)
- [ ] 그 외 P1/P2 항목은 [demo-risk-list.md](demo-risk-list.md) 참조
