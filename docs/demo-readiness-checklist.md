# S309 시연 준비 체크리스트 (시나리오 기반 압축본)

- **발표일**: 2026-05-18 주
- **점검일**: 2026-05-18
- **운영 도메인**: `https://k14s309.p.ssafy.io/`

> 시연 동선이 좁아 광범위 체크리스트의 70%는 컷. 이 문서는 **시연 대본 3컷에 직접 필요한 항목만** 다룬다. 보안·인프라 고도화 항목은 [demo-risk-list.md](demo-risk-list.md) 의 P1/P2 참고.

---

## 시연 대본 ↔ 의존 컴포넌트

| 컷 | 시연 동작 | 핵심 컴포넌트 |
|---|---|---|
| **CUT 1** | 카톡 키보드에 "마라탕" 입력 → D등급 즉시 표시 | 커스텀 IME (`feature-glucofit`) · 음식 DB · 백엔드 음식 검색 API |
| **CUT 2** | 친구가 "라면" 추천 → 라면 혈당 예측 화면 | 음식명 → 혈당 예측 (`/inference/glucose/meal`) · 식약처 API or 캐시 |
| **CUT 3** | "하이 키키" → "네, 주인님" → "나 뭐 먹을까?" → 추천 답변 | Wake word (RECORD_AUDIO + `wakeWordManager`) · TTS · LLM agent (`/agent/command`, Anthropic) |

**시연에 안 쓰이는 영역 = 검증 제외**: BLE 패치, 카메라 FoodScan, Wear OS / 워치, FCM 푸시, SOS, 위젯, Weekly Report PDF, Health Connect, Samsung Health Polling, Blue/Green 배포 토글, S3 PDF 다운로드.

---

## 🔴 D-Day 직전 필수 7가지 (이거만 통과하면 시연 가능)

```
[ ] 1. AI 컨테이너에 ANTHROPIC_API_KEY 실주입
       docker exec s309-ai-{color} env | grep ANTHROPIC_API_KEY
       → 빈값이면 CUT 3 키키 답변이 폴백 메시지로 나옴 (시연 클라이맥스 망함)

[ ] 2. AI 컨테이너 → api.anthropic.com 도달 확인
       docker exec s309-ai-{color} curl -sI https://api.anthropic.com
       → 차단되면 모든 LLM 호출 실패 → 폴백만 발송
       → 차단 환경이면 BASE_URL을 SSAFY GMS 프록시로 즉시 교체 필요
         (ai/app/agent/morning_agent_run.py:35, postmeal_agent_run.py:30,
          food_recommend_agent_run.py:30)

[ ] 3. AGENT_API_KEY 실주입 (Backend+AI 양쪽)
       docker exec s309-backend-{color} env | grep AGENT_API_KEY
       docker exec s309-ai-{color}      env | grep AGENT_API_KEY
       → 값이 같아야 하며, "dev-agent-key-change-in-prod" 폴백이면 안 됨

[ ] 4. 백엔드 / AI / Nginx 컨테이너 healthy
       docker inspect --format='{{.Name}} {{.State.Health.Status}}' \
         s309-backend-{color} s309-ai-{color} s309-nginx s309-postgres s309-redis
       → AI start_period 120s 고려, 재시작 후 최소 2분 대기

[ ] 5. 시연 단말: GlucoFit 커스텀 IME가 카톡 기본 키보드로 설정
       단말 설정 → 키보드 → GlucoFit 키보드 선택 + 카톡 열어서 활성화 확인
       → CUT 1 시연 필수 조건

[ ] 6. 시연 단말: 마이크 권한 ON + "하이 키키" 1회 wake word 트리거 성공
       권한 누락 시 wakeWordManager.start() 호출 안 됨 (MainActivity.kt:118-121)
       → CUT 3 시연 필수 조건

[ ] 7. 시연 단말 + 백엔드 음식 DB 응답 사전 테스트
       - "마라탕" → D등급 응답
       - "라면" → 혈당 예측 응답 (가능하면 화면까지 진입해서 확인)
       → 데이터 누락 시 시연 흐름 즉시 끊김
```

---

## 시점별 액션

### D-1 (시연 전날)
- [ ] HTTPS 인증서 만료일 확인: `echo | openssl s_client -connect k14s309.p.ssafy.io:443 2>/dev/null | openssl x509 -noout -dates`
- [ ] Jenkins job disable 또는 GitLab webhook 일시 차단 — release 브랜치 push 금지
- [ ] PostgreSQL 수동 백업: `docker exec s309-postgres pg_dump -U $POSTGRES_USER $POSTGRES_DB > ~/backup-$(date +%Y%m%d-%H%M).sql`
- [ ] EC2 디스크 여유 > 20% (`df -h /`)
- [ ] **풀 시나리오 리허설 1회** — 마라탕 → 라면 → 하이키키까지 끝까지 완주

### D-Day 1시간 전
- [ ] 위 **필수 7가지** 전수 통과
- [ ] PREV 슬롯 컨테이너 살아있는지 확인 (60초 grace 내 즉시 롤백용)
- [ ] 시연 단말 충전 100% + 보조배터리 + 안정된 와이파이 접속

### D-Day 10분 전 (단말 최종 점검)
- [ ] 시연 계정 로그인 상태 확인
- [ ] 카톡 앱 열어서 GlucoFit 키보드 활성화 (지구본 아이콘으로 전환)
- [ ] "하이 키키" 한 번 발화 → "네, 주인님" TTS 응답 확인
- [ ] **단말 화면 항상 켜둠** 설정 (잠금 화면으로 인한 wake word 멈춤 방지)
- [ ] 무음/방해금지 모드 OFF (TTS 발화용)
- [ ] n8n schedule/webhook 트리거가 시연 시간대에 발화 가능한지 한번 더 확인

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
[ ] /agent/command 호출 → LLM 답변 수신 (최대 30s 타임아웃, 평균 5~10s 예상)
[ ] 답변 화면 표시 (오늘 혈당 + 점심 + 시간 컨텍스트 종합 추천)
[ ] (실패 시) ANTHROPIC_API_KEY, api.anthropic.com 접근, AGENT_API_KEY 재확인
```

---

## 🚨 시연 중 비상 대응

| 증상 | 즉시 대응 |
|---|---|
| 카톡 키보드에서 음식 등급 미표시 | 단말 자체 키보드 재선택 → 재발화 안 되면 백엔드 음식 검색 API 직접 호출 화면으로 우회 |
| 라면 혈당 예측 화면 무한 스피너 | 사전 녹화 영상으로 전환 (FE OkHttp timeout 미설정 — `AppModule.kt:41-48` 영향) |
| "하이 키키" 무반응 | 마이크 권한 재확인 → 앱 재시작 → 마지막은 사전 녹화 |
| 키키 답변이 어색한 폴백 메시지 | LLM 폴백 발동 — Anthropic 키 만료 / 네트워크 차단 의심. 사전 녹화 영상으로 전환 |
| AI 추론 전반 실패 | Blue/Green PREV 슬롯 롤백 (60초 grace 이내) |
| 백엔드 500 연발 | 컨테이너 재시작 → 안 되면 사전 녹화 |

**최후의 보루**: 시연 전체 사전 녹화 영상 1개 — 발표자 단말에 미리 저장. 어떤 장애가 나도 발표 흐름은 유지.

---

## 시연 후 처리 (시연 막진 않지만 보안상 필요)

- [ ] `infra/.env` 의 `AGENT_API_KEY` 폴백 제거 (`docker-compose.{blue,green}.yml:50`)
- [ ] AGENT_API_KEY를 실제 강한 랜덤값으로 교체
- [ ] 그 외 P1/P2 보안·품질 항목은 [demo-risk-list.md](demo-risk-list.md) 참조

---

## 컷한 영역 (시연 무관 — 점검 안 함)

원래 광범위 체크리스트에는 있었으나 이번 시연 대본에 안 들어가는 항목들. 발표 후 출시/품질 관점에서 별도로 다루면 됨.

- BLE 패치 연결, 실시간 모드 그래프 갱신 (`BleScreen`, `BleConfig`)
- 카메라 권한 + FoodScan 촬영 흐름
- Wear OS 페어링, 워치 화면, Complication, **워치 라벨 vs 데이터 불일치** (FE-P0-1)
- FCM 푸시, SOS 알림, 보호자 페어링
- Glance 위젯 (30분 갱신, 새로고침 버튼)
- Weekly Report PDF S3 presigned URL
- Health Connect / Samsung Health Polling
- 식사 알림 스케줄, BootReceiver 재예약
- Blue/Green 배포 토글 시연 (가동만 확인, 토글은 안 함)
- 인증/회원가입 풀 흐름 (사전 로그인 상태로 시작)
