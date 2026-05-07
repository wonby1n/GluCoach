# CGM 혈당 캐릭터 매핑 (키키)

대시보드의 현재 혈당 수치와 트렌드에 따라 키키 캐릭터가 9가지 모습으로 변합니다.

---

## 구간 판정

### 1. 트렌드 보정

현재 수치에 트렌드 오프셋을 더한 **adjusted 값**으로 구간을 판정합니다.

| 트렌드 | 기준 (mg/dL/min) | 보정 |
|--------|------------------|------|
| ↑↑ 급상승 | > 3 | glucose + 30 |
| ↑ 상승 | 1 ~ 3 | glucose + 15 |
| → 안정 | ±1 | glucose + 0 |
| ↓ 하강 | -1 ~ -3 | glucose - 10 |
| ↓↓ 급하강 | < -3 | glucose - 25 |

adjusted 값은 40 ~ 400 범위로 클램프됩니다.

### 2. 환자 유형별 임계값

| 유형 | 정상 상한 (ok) | mild 진입 | moderate 진입 |
|------|-------------|----------|-------------|
| 일반 (NONE) | 140 | 180 | 250 |
| 1형 당뇨 (TYPE1) | 150 | 180 | 250 |
| 2형 당뇨 (TYPE2) | 140 | 180 | 250 |

### 3. 구간 판정 결과

| adjusted 범위 | 구간 | 캐릭터 |
|-------------|------|--------|
| < ok | 정상 | `kiki_main` |
| ok ~ mild | 약한 고혈당 | `kiki_mild_*` |
| mild ~ moderate | 확연한 고혈당 | `kiki_moderate_*` |
| ≥ moderate | 심각한 고혈당 | `kiki_severe_*` |

---

## 증상 선택

트렌드 패턴에 따라 3가지 증상 중 하나가 선택됩니다.

| 패턴 | 조건 | 증상 | 이미지 접미사 |
|------|------|------|-----------|
| 급격한 피크 | rate > 3 mg/dL/min | 갈증/구강건조 | `_thirst` (`_sick`) |
| 서서히 상승 | 0 < rate ≤ 3 | 시야 흐림 | `_blur` |
| 안정/하강 | rate ≤ 0 | 피로감 | `_tired` |

---

## 이미지 에셋

`app/src/main/res/drawable/` 에 위치합니다.

| 파일명 | 단계 | 증상 |
|--------|------|------|
| `kiki_main.png` | 정상 | - |
| `kiki_mild_thirst.png` | 약한 | 갈증/구강건조 |
| `kiki_mild_tired.png` | 약한 | 피로감 |
| `kiki_mild_blur.png` | 약한 | 시야 흐림 |
| `kiki_moderate_thirst.png` | 확연한 | 갈증/구강건조 |
| `kiki_moderate_tired.png` | 확연한 | 피로감 |
| `kiki_moderate_blur.png` | 확연한 | 시야 흐림 |
| `kiki_severe_sick.png` | 심각한 | 심각한 증상 |
| `kiki_severe_tired.png` | 심각한 | 피로감 |
| `kiki_severe_blur.png` | 심각한 | 시야흐림 |

---

## 관련 파일

| 파일 | 역할 |
|------|------|
| `KikiCharacterMapper.kt` | 혈당 + 트렌드 + 환자유형 → drawable 리소스 매핑 |
| `MainUiState.kt` | `trendRateMgDlPerMin`, `diabetesType` 상태 보유 |
| `MainViewModel.kt` | 타임스탬프 기반 트렌드 속도 계산, 사용자 설정에서 당뇨 유형 로드 |
| `MainScreen.kt` | `KikiCharacterMapper.resolve()` 호출 → `CurrentGlucoseCard`에 전달 |

---

## 디버그 테스트 패널

에뮬레이터에서 BLE 기기 없이 캐릭터 변화를 확인할 수 있는 테스트 패널이 포함되어 있습니다.

홈 탭의 현재 혈당 카드 아래에 주황색 "DEBUG: 키키 테스트" 패널이 표시됩니다.
탭하면 프리셋 버튼이 나타나고, 각 버튼을 누르면 해당 혈당/트렌드 조합의 캐릭터를 즉시 확인할 수 있습니다.

### 배포 전 디버그 코드 제거

모든 디버그 코드에 `[DEBUG_KIKI_TEST]` 태그가 달려 있습니다.

**검색:**
```bash
grep -rn "DEBUG_KIKI_TEST" app/src/
```

**제거 대상:**

| 파일 | 조치 |
|------|------|
| `DebugKikiTestPanel.kt` | 파일 삭제 |
| `MainScreen.kt` | `// [DEBUG_KIKI_TEST]` 표시된 줄 삭제 |
| `MainViewModel.kt` | `// [DEBUG_KIKI_TEST]` ~ `// [/DEBUG_KIKI_TEST]` 블록 삭제 |
