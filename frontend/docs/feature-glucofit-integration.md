# feature-glucofit 연동 정리

`feature-glucofit` 모듈(Samsung Health / Health Connect / 커스텀 IME / 오버레이)을 메인 app에 녹인 결과물 정리.

## 한눈에 보기

- 마이페이지 → "연결 기기" → 신규 화면 `HealthSourceScreen` 진입
- 그 화면에서 4가지 권한/설정을 한 번에 관리: Samsung Health / Health Connect / GlucoFit 키보드 / 오버레이
- 권한이 없거나 SDK가 미지원이어도 메인 화면은 항상 mock 데이터로 정상 동작 (무회귀 보장)

## Samsung Health 연동 과정

Samsung Health SDK는 `HealthDataService.getStore(activity)` 호출 시 Activity 인스턴스가 필수다. 그래서 다음 순서로 wiring한다:

1. **앱 실행** → `MainActivity.onCreate`
2. `samsungHealthHolder.attach(this)` 호출 → 내부에서 `SamsungHealthManager(activity)` 생성
   - API 29 미만 / Samsung Health 미설치 / SDK 초기화 실패 시 `manager = null`로 유지 (앱 깨지지 않음)
3. `MainActivity.onResume` → `maybeRequestSamsungHealthAtLaunch()` 호출 (Activity 라이프타임 1회)
   - `store.requestPermissions(...)` → Samsung Health 앱이 권한 다이얼로그 표시
   - **이미 권한이 부여된 상태이면 SDK가 다이얼로그를 띄우지 않고 즉시 리턴** (사용자 무방해)
   - 권한 다이얼로그는 Activity가 RESUMED 상태여야 표시되므로 onCreate가 아닌 onResume에서 트리거
4. 사용자가 허용 → 이후 `MainViewModel.loadDashboard()`가 데이터 조회 시 운동/수면 등 실데이터 반환
5. 사용자가 거부 → `2000: not allowed` 예외 → 매니저가 catch 후 0 반환 → mock fallback
6. 거부 후 다시 요청하고 싶으면 마이페이지 → 연결 기기 → "권한 요청" 버튼을 직접 탭

마이페이지 / 연결 기기 화면 진입 자체로는 자동 요청이 일어나지 않는다 (사용자 주도). 권한이 없는 동안에도 메인 화면은 mock 데이터로 정상 표시된다. logcat에 `2000: not allowed` 가 찍히는 건 fallback이 정상 작동 중이라는 신호.

## Health Connect 연동 과정

Health Connect는 `PermissionController.createRequestPermissionResultContract()` 기반 표준 ActivityResult 흐름:

1. `HealthSourceScreen` 진입
2. "Health Connect 권한 요청" 버튼 탭
3. `rememberLauncherForActivityResult`가 Health Connect 앱을 띄움 → 사용자 권한 부여
4. 결과 받아 `viewModel.onHealthConnectPermissionResult()` → 화면 상태 갱신
5. Health Connect 미설치 디바이스에서는 `isAvailable() = false` → 버튼 비활성

## GlucoFit 키보드 / 오버레이

둘 다 사용자가 시스템 설정 화면에서 직접 활성화해야 한다 (앱이 강제 못 함).

- **키보드**: `Settings.ACTION_INPUT_METHOD_SETTINGS` 인텐트로 시스템 입력기 설정 화면 열기
- **오버레이**: `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` 인텐트로 오버레이 권한 화면 열기

`HealthSourceScreen`에서는 각 섹션의 활성화 여부만 표시하고 설정 화면으로 보내는 버튼만 제공.

## fallback 구조

`HealthRepository`가 모든 데이터 조회 진입점이고, 내부적으로 우선순위 시도 후 mock으로 떨어진다:

```
Samsung Health → Health Connect → Mock (최종 fallback)
```

각 단계는 try-catch로 감싸있어 어떤 예외/빈 결과/null도 흡수하고 다음으로 넘어간다. 모든 primary가 데이터를 못 주면 mock 반환. 즉:

- 권한 미부여 → mock
- SDK 미지원 → mock
- 매니저 생성 실패 → mock
- 권한 부여됐지만 데이터 없음 → mock
- 권한 부여 + 데이터 있음 → 실데이터

`HealthRepository`의 public 시그니처는 변경되지 않았기 때문에 `MainViewModel`을 비롯한 호출부는 영향받지 않는다.

## 데이터 → 화면 매핑

현재 메인 화면에 실제로 흘러가는 외부 데이터는 Samsung Health에서 가져오는 **운동 칼로리 + 수면** 2개뿐이다. 나머지는 mock.

| 화면 요소 | 위치 | 출처 | 비고 |
|---|---|---|---|
| 칼로리 소모 카드 | `MainScreen` 하단 SummaryRow | Samsung Health → `getTodayExerciseCalories()` | 운동 세션 수 × 200 kcal 추정 |
| 수면 카드 | `MainScreen` 하단 SummaryRow | Samsung Health → `getLastSleepDurationMinutes()` | `시:분` 포맷 |
| 현재 혈당 (큰 카드) | `MainScreen` CurrentGlucoseCard | mock | Samsung 단일 값 매핑 보류 |
| 혈당 그래프 | `MainScreen` GlucoseChartCard | mock | epoch ms 보존된 시리즈 매핑 필요 |
| 식사 핀 | 그래프 위 | mock | BE 연동 예정 |
| 알림 패널 | 우측 슬라이드 | mock | BE 연동 예정 |
| 마이페이지 표시 | MyPageContent | local (auth user email) | — |

데이터 흐름 (요약 카드 기준):

```
Samsung Health 앱
  → SamsungHealthManager (feature-glucofit)
  → SamsungHealthDataSource.getTodaySummary() — 칼로리/수면 병합
  → HealthRepository.getTodaySummary() — 우선순위 / fallback
  → MainViewModel.loadDashboard() — state.summary 갱신
  → MainScreen SummaryRow → SummaryStatCard 2개에 바인딩
```

권한이 없거나 데이터가 0이면 `null` → Repository fallback → `MockHealthDataSource.getTodaySummary()` 의 `(485 kcal, 7시간 15분)` 표시. 화면 자체는 동일.

Samsung Health가 읽을 수 있지만 **현재 화면에는 안 흘러가는** 데이터:
- 최근 혈당 단일 값 (`getLatestBloodGlucose`)
- 심박수 (`getLatestHeartRate`)

Health Connect 데이터(혈당 시리즈, 영양)는 인프라만 깔려 있고 실제 매핑은 미구현 — 권한을 받아도 현재는 모두 mock으로 떨어진다.

## 추가/수정된 파일

신규:
- `app/data/repository/source/HealthDataSource.kt` — 인터페이스
- `app/data/repository/source/MockHealthDataSource.kt` — 기존 mock 데이터 이전
- `app/data/repository/source/HealthConnectDataSource.kt`
- `app/data/repository/source/SamsungHealthDataSource.kt`
- `app/data/repository/source/SamsungHealthHolder.kt` — Activity 의존성 우회
- `app/di/GlucoFitModule.kt` — Hilt 브리지
- `app/ui/screen/health/HealthSourceViewModel.kt`
- `app/ui/screen/health/HealthSourceScreen.kt`

수정:
- `app/data/repository/HealthRepository.kt` — 내부 우선순위 fallback (시그니처 동일)
- `app/MainActivity.kt` — `samsungHealthHolder.attach/detach` 호출 (EntryPointAccessors 사용) + `onResume`에서 Samsung Health 권한 자동 요청 (라이프타임 1회)
- `app/navigation/AppNavigation.kt` — `Screen.HealthSource` 라우트 1개 추가
- `app/ui/screen/main/MainScreen.kt` — `onConnectedDeviceClick` 콜백 1개 추가 (default `{}`)
- `app/build.gradle.kts` — Health Connect client 의존성 1줄 추가

`feature-glucofit/` 모듈 코드는 한 줄도 수정하지 않았다.

## 알아둘 것

- `MainActivity`에서 `@Inject lateinit var` 대신 `EntryPointAccessors`를 쓰는 이유: Hilt 2.55 + Kotlin 2.2 조합에서 members-injection validation이 metadata 2.2.0을 거부하는 이슈가 있어 우회.
- `HealthDebugActivity`(feature-glucofit 모듈 내부)는 LAUNCHER가 아니라 출시 빌드에서도 사용자 눈에 띄지 않지만, release APK에서 빼고 싶으면 후속 작업으로 `feature-glucofit/src/debug/AndroidManifest.xml`로 격리 가능.
- Samsung Health 권한 자동 요청은 `MainActivity` 라이프타임당 1회만 트리거된다 (앱 실행 시점). 마이페이지 / 연결 기기 화면에서는 자동 요청이 일어나지 않으며, 사용자가 거부 후 다시 요청하려면 연결 기기 화면의 "권한 요청" 버튼을 직접 탭.
