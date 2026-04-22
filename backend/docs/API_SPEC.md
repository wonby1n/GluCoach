# API 명세서

> **상태:** 진행 중 · **담당자:** 도현 · **업데이트:** 2026-04-22
> ERD(V1 + V2 migration) 기준으로 재정리.

---

## 범례

| 표기 | 의미 |
|------|------|
| 🔴 Highest | MVP 필수, 최우선 구현 |
| 🟠 High | MVP 필수, 순차 구현 |
| 🟡 Medium | 2차 스프린트 |
| 🟢 Low | 시연용 또는 선택 기능 |
| 🟩 구현 완료 | BE 구현 + 테스트 통과 |
| ⬜ 미구현 | 미착수 |

---

## ⚠️ 개발 중 임시 URL 안내

JWT 인증 병합 전까지 **섹션 2 (사용자 설정)** 의 엔드포인트는 임시로 `{userId}` 경로 파라미터를 받습니다.

| 명세서 (최종) | 개발 중 임시 |
|--------------|-------------|
| `/api/user/settings` | `/api/users/{userId}/settings` |
| `/api/user/guardians` | `/api/users/{userId}/guardians` |
| `/api/user/me` | `/api/users/{userId}/me` |

JWT 병합 시 단수 `/api/user/...` 로 일괄 변경되며, userId는 토큰 claims에서 추출됩니다.

---

## 1. 인증 (Auth)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 회원가입 | POST | `/api/auth/signup` | 이메일/소셜 가입. JWT access(1h) + refresh(30d) 발급. 온보딩 3단계 진입 | ⬜ | 🔴 Highest |
| 로그인 | POST | `/api/auth/login` | JWT Bearer 토큰 발급. 소셜(카카오/구글) 포함 | ⬜ | 🔴 Highest |
| 토큰 갱신 | POST | `/api/auth/refresh` | access 만료 시 refresh로 재발급. Refresh Rotation 적용 (재사용 방지) | ⬜ | 🔴 Highest |
| 로그아웃 | POST | `/api/auth/logout` | 서버 측 refresh_token 블랙리스트 등록. 클라이언트 토큰 삭제. notification_tokens 비활성화 | ⬜ | 🔴 Highest |
| 회원 탈퇴 | DELETE | `/api/auth/withdraw` | 소프트 삭제(`deleted_at` 기록). 개인정보 30일 후 완전 삭제 스케줄러 | ⬜ | 🟠 High |

---

## 2. 사용자 설정 (User)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 내 프로필 조회 | GET | `/api/user/me` | 로그인 사용자 프로필(email, provider) 및 설정값 반환 | ⬜ | 🟠 High |
| 설정 조회 | GET | `/api/user/settings` | 키·체중·당뇨유형·목표혈당·알림기준·캐릭터 등 설정값 반환 | 🟩 | 🟠 High |
| 설정 수정 | PUT | `/api/user/settings` | 변경 즉시 대시보드 기준선·알림 임계값 갱신. null 필드는 기존값 유지 (부분 업데이트) | 🟩 | 🟠 High |
| 보호자 목록 조회 | GET | `/api/user/guardians` | `priority` 오름차순 정렬. SOS 연락 순서와 동일 | 🟩 | 🟠 High |
| 보호자 등록 | POST | `/api/user/guardians` | SOS 발송 대상 보호자 등록. `priority`는 서버가 현재 보호자 수로 자동 할당 | 🟩 | 🟠 High |
| 보호자 수정 | PUT | `/api/user/guardians/{guardianId}` | 이름·번호·관계·대표여부(`isPrimary`) 수정. null 필드는 기존값 유지 | 🟩 | 🟠 High |
| 보호자 삭제 | DELETE | `/api/user/guardians/{guardianId}` | 삭제 후 나머지 `priority` 자동 재정렬 (빈자리 당기기) | 🟩 | 🟠 High |
| FCM 토큰 등록 | POST | `/api/user/fcm-token` | 앱/워치 설치 시 등록. `device_type`: android/watch. 기존 토큰 있으면 갱신 | ⬜ | 🟠 High |
| FCM 토큰 삭제 | DELETE | `/api/user/fcm-token` | 로그아웃 시 `is_active=false` 처리 | ⬜ | 🟠 High |
| 복용 기록 저장 | POST | `/api/user/medications` | 약·인슐린 복용 기록. `log_type`: medicine/insulin | ⬜ | 🟡 Medium |
| 복용 기록 조회 | GET | `/api/user/medications?range=7d` | 기간별 복용 기록 조회 | ⬜ | 🟡 Medium |

---

## 3. CGM 연동 (Continuous Glucose Monitoring)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| CGM BLE 연결 및 수신 | (로컬 SDK) | 화이바이오메드 BLE SDK | BLE 5.0. 끊김 시 자동 재연결. 수신 주기마다 UI 갱신 | ⬜ | 🔴 Highest |
| CGM 데이터 서버 동기화 | POST | `/api/cgm/sync` | 앱 → MCP CGM Server → AI Backend. TLS 1.2+ 암호화. 누락 구간 공백 표시 | ⬜ | 🔴 Highest |
| CGM 패턴 학습 | POST | `/api/cgm/pattern-learn` | 식후 상승/하강 패턴 개인화 학습. 이상치 제거 기준 업데이트 | ⬜ | 🟠 High |
| Samsung Health 연동 | POST | `/api/health/sync` | 운동(종류·시간·칼로리), 수면(시작/종료) 수신. 타임라인 핀 표시 | ⬜ | 🟡 Medium |

---

## 4. 대시보드 (Dashboard)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 실시간 혈당 표시 | (로컬) | BLE 로컬 수신 | BLE 수신 주기마다 UI 갱신. 추세 최근 15분 기준 4단계 | ⬜ | 🔴 Highest |
| 타임라인 통합 조회 | GET | `/api/timeline?range={range}` | X축 0~24h 혈당 곡선 + 식사/운동/수면 이벤트 핀 | ⬜ | 🔴 Highest |
| 대시보드 요약 지표 | GET | `/api/stats/summary?range=1d` | 실시간 대시보드용 요약(평균, TIR, 최고/최저, 변동폭) | ⬜ | 🔴 Highest |
| 디지털 트윈 아바타 상태 | GET | `/api/avatar/state` | 현재 혈당 기반 아바타 표정·혈관색·애니메이션 매핑. FE 렌더링용 | ⬜ | 🟠 High |
| 혈당 패턴 비교 조회 | GET | `/api/stats/compare?mode={daily\|weekly}&base={date}&compare={date}` | 기준일/비교일의 혈당 곡선·평균·TIR 동시 반환. `delta`로 증감 강조 | ⬜ | 🟡 Medium |

---

## 5. AI 음식 인식 (FastAPI)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 음식 사진 인식 + 공공데이터 자동 연동 | POST | `/ai/food/recognize-and-fetch` | 사진 → CV 모델 인식 → 식품안전처 API 자동 조회 → 영양 정보 반환 (원스텝) | ⬜ | 🔴 Highest |
| 음식 사진 인식 (CV만) | POST | `/ai/food/recognize` | Camera2 API 촬영 → FastAPI CV 모델. confidence 0.6 미만 시 확인 UI. 실패 시 텍스트 입력 fallback | ⬜ | 🔴 Highest |
| 음식명으로 영양 정보 조회 | GET | `/api/food/search?q={keyword}` | 식품안전처 공공데이터 API 호출 후 `foods` 캐싱. 캐시된 데이터는 DB에서 반환 | ⬜ | 🔴 Highest |

---

## 6. 커스텀 키보드 MCP

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 키보드 앱 혈당 수치 조회 | GET | `/api/mcp/keyboard/glucose` | 커스텀 키보드가 MCP 통해 현재 혈당 폴링. 키보드 상단 상태바에 표시 | ⬜ | 🟠 High |
| 키보드 음식 키워드 감지 → 위험도 반환 | POST | `/api/mcp/keyboard/food-risk` | 음식명 입력 감지 시 MCP 호출. 현재 혈당 기준 위험도 계산 | ⬜ | 🟠 High |
| 키보드 인라인 배너 알림 | POST | `/api/mcp/keyboard/banner` | `TYPE_APPLICATION_OVERLAY` 오버레이. 현재 혈당 + 음식명 + 위험문구. 3초 자동 소멸 | ⬜ | 🟠 High |
| AccessibilityService 음식 키워드 탐지 | (로컬) | Android AccessibilityService | 허용 앱: 카카오톡·배달의민족. 텍스트 노드 읽기 → 키워드 DB 매칭 → 즉시 폐기 | ⬜ | 🟢 Low |

---

## 7. 프리밀 시뮬레이터 (Pre-meal)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 식후 혈당 곡선 예측 | POST | `/api/predict/glucose` | 서버가 `cgm_patterns.personalized_at` 유무로 generic/personalized 자동 분기. 14일 이상 + CGM 충분 시 전이학습 기반 개인화 | ⬜ | 🔴 Highest |
| A/B 비교 모드 | POST | `/api/predict/glucose/compare` | 예측 API 2회 호출. 동일 시간축 곡선 비교. 최고 혈당 차이 강조 | ⬜ | 🔴 Highest |

---

## 8. 식사 기록 & 성적표 (Meal Logs)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 식사 기록 저장 | POST | `/api/meals` | 실제 식사/시뮬레이션 구분 저장(`simulated`). `raw_input`으로 원본 입력값 보존 | ⬜ | 🔴 Highest |
| 식후 혈당 반응 기록 | POST | `/api/meals/{mealId}/response` | 식사 후 2시간 CGM 수집. 최고 혈당·복귀 시간 자동 계산. 2회 이상 시 등급 산출 | ⬜ | 🟠 High |
| 음식 성적표 조회 | GET | `/api/food-report` | A:≤140 / B:141~170 / C:171~200 / D:201+. 1회는 기록중. 등급 배지 + 최고혈당 + 복귀시간 | ⬜ | 🟠 High |

---

## 9. AI 주간 리포트 (Weekly Report)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 주간 리포트 생성 (수동) | POST | `/api/report/weekly` | 매주 월요일 07:00 자동 생성. 수동 버튼 제공. LLM 비동기 처리 → status 폴링 | ⬜ | 🟡 Medium |
| 주간 리포트 조회 | GET | `/api/report/{reportId}` | 평균혈당·TIR·변동폭·음식 TOP3·LLM요약·개선제안. `status=PROCESSING`이면 202 반환 | ⬜ | 🟡 Medium |
| PDF 내보내기 | GET | `/api/report/{reportId}/pdf` | A4 1~2장. 혈당 요약 + 음식반응 + 패턴요약. 카카오톡/이메일 공유 | ⬜ | 🟡 Medium |

---

## 10. 알림 & 응급 (Alert & Emergency)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 온디바이스 저혈당 감지 | (로컬 ML) | TFLite (로컬) | 온디바이스 감지 → 알림 1초 이내. 무반응 30초 → 스마트홈 자동 대응 | ⬜ | 🔴 Highest |
| 알림 발생 기록 | POST | `/api/alert` | 저혈당/고혈당/SOS 알림 서버 기록. 이후 `smarthome_events` 체인 트리거 | ⬜ | 🔴 Highest |
| SOS 발송 | POST | `/api/alert/sos` | SMS + 앱 푸시 동시 발송. GPS 위치 + 혈당 + 시각 포함. 미응답 시 `priority` 순서대로 2차 보호자 연속 발송 | ⬜ | 🔴 Highest |
| Galaxy Watch 진동 알림 | (로컬) | Wear OS Notification API | 야간(22:00~07:00) 저혈당 감지 시 진동 강도 상향 | ⬜ | 🟢 Low |

---

## 11. 스마트홈 (Smart Home)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 스마트홈 자동 대응 실행 | POST | `/api/smarthome/trigger` | 무반응 30초 후 자동 호출. 조명 빨간 점멸 + 도어락 해제 + SOS 동시 실행 | ⬜ | 🟢 Low |
| 스마트 도어락 자동 해제 | (IoT) | 도어락 IoT API | 무반응 시 자동 잠금 해제. 이벤트 타임스탬프 기록. 보호자 앱 푸시. 원격 재잠금 가능 | ⬜ | 🟢 Low |
| Philips Hue 조명 제어 | (로컬 REST) | Hue Bridge Local REST API | 동일 Wi-Fi 필요. 저혈당 시 빨간색 점멸. 정상 복귀 시 원래 색상 복원. 시연 전용 | ⬜ | 🟢 Low |

---

## 공통 사항

### 인증
- 모든 엔드포인트는 `Authorization: Bearer {accessToken}` 헤더 필요 (섹션 1 제외)
- 서버는 토큰 claims에서 `userId` 추출 → SecurityContextHolder에 저장 → Controller에서 `@AuthenticationPrincipal` 로 사용

### 응답 포맷
- **성공:** `200 OK`, `201 Created`, `204 No Content`
- **클라이언트 오류:** `400 Bad Request` (검증 실패, 소유권 위반 등)
- **인증/권한:** `401 Unauthorized`, `403 Forbidden`
- **리소스 없음:** `404 Not Found`

### 오류 응답 형식
```json
{ "message": "존재하지 않는 유저입니다: {uuid}" }
```

### `characterType` 허용값 (섹션 2 설정 관련)
> FE와 협의 필요. 현재 DB 기본값은 `"BASIC"`.
- `BASIC` / (추가 예정)

### 보호자 `priority` 동작
- 등록 시: 현재 보호자 수를 `priority`로 자동 할당 (0, 1, 2...)
- 삭제 시: 삭제된 `priority` 이후 항목들이 1씩 앞당겨짐
- 조회 시: `priority ASC` 정렬 → SOS 연락 순서와 일치
