# 구현에 쓰인 코틀린 / 안드로이드 문법 가이드

`feature-glucofit` 연동 작업에서 사용한 주요 문법과 함수를 정리. 실제 우리 코드에서 발췌.

---

## 1. 코틀린 기본

### `data class` — 단순 값 보관용 클래스

자동으로 `equals`, `hashCode`, `toString`, `copy`를 만들어줌.

```kotlin
// app/ui/screen/health/HealthSourceViewModel.kt
data class HealthSourceUiState(
    val samsungSdkSupported: Boolean = false,
    val samsungReady: Boolean = false,
    ...
)
```

### `sealed class` — 정해진 종류만 가질 수 있는 타입

라우팅/상태 분기에 자주 쓰임. Java의 enum 강화판.

```kotlin
// app/navigation/AppNavigation.kt
sealed class Screen(val route: String) {
    object Landing : Screen("landing")
    object Main : Screen("main")
    object HealthSource : Screen("health_source")
}
```

### `object` — 싱글톤 객체

인스턴스가 하나만 존재. `class`처럼 쓰면서 `new` 없이 직접 사용.

```kotlin
object GlucoseSimulator {
    fun start() { ... }
}
GlucoseSimulator.start()  // 그냥 이렇게 호출
```

### `companion object` — 클래스 내부 상수/팩토리

Java의 `static`과 비슷.

```kotlin
class HealthRepository @Inject constructor(...) {
    private companion object {
        const val TAG = "HealthRepository"
    }
}
```

### `?` `?:` `!!` — null 안전성

```kotlin
val mgr: SamsungHealthManager? = holder.manager  // ?  = null 허용
val mgr = holder.manager ?: return               // ?: = null이면 return
val mgr = holder.manager!!                        // !! = null이면 NPE (위험)
```

### `by lazy` — 처음 호출 시에만 초기화

```kotlin
// app/MainActivity.kt
private val samsungHealthHolder: SamsungHealthHolder by lazy {
    EntryPointAccessors.fromApplication(...).samsungHealthHolder()
}
```
`samsungHealthHolder`를 처음 사용할 때 람다 안의 코드가 실행되고 결과가 캐시됨.

### `suspend fun` — 코루틴 안에서만 호출 가능한 함수

비동기 작업에 필수.

```kotlin
suspend fun getRecentGlucose(hours: Int): List<GlucoseReading> = ...
```

### `runCatching` / `try-catch` — 예외 처리

```kotlin
runCatching { mgr.requestPermissions() }
    .onFailure { Log.w(TAG, "실패", it) }
```
또는 전통적인:
```kotlin
try {
    mgr.requestPermissions()
} catch (t: Throwable) {
    Log.w(TAG, "실패", t)
}
```

---

## 2. Hilt — 의존성 주입(DI)

객체 생성과 전달을 자동화. Android 표준.

### `@Inject constructor` — Hilt가 알아서 만들어주는 클래스

```kotlin
// app/data/repository/source/MockHealthDataSource.kt
@Singleton
class MockHealthDataSource @Inject constructor() : HealthDataSource { ... }
```
`@Inject constructor()`만 붙이면 Hilt가 필요할 때 자동 인스턴스화.

### `@Singleton` — 앱 전체에서 인스턴스 하나만

`@Inject constructor`와 같이 씀.

### `@Module` `@Provides` `@InstallIn` — 직접 컨트롤하는 인스턴스 제공

`@Inject constructor`로 못 만드는 클래스(외부 라이브러리 등)는 모듈에서 명시.

```kotlin
// app/di/GlucoFitModule.kt
@Module
@InstallIn(SingletonComponent::class)
object GlucoFitModule {
    @Provides
    @Singleton
    fun provideHealthConnectManager(
        @ApplicationContext context: Context,
    ): HealthConnectManager = HealthConnectManager(context)
}
```

### `@ApplicationContext` — 앱 전역 Context 주입

Activity Context가 아닌 Application Context를 명시적으로 받을 때.

### `@AndroidEntryPoint` — Activity/Fragment에 의존성 주입 활성화

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() { ... }
```

### `@HiltViewModel` — ViewModel에 의존성 주입

```kotlin
@HiltViewModel
class HealthSourceViewModel @Inject constructor(
    private val hcDataSource: HealthConnectDataSource,
    ...
) : ViewModel()
```

### `@EntryPoint` / `EntryPointAccessors` — Hilt 그래프에 직접 접근

`@Inject lateinit var` 대신 사용 가능. 우리는 metadata 충돌 회피용으로 사용.

```kotlin
// app/MainActivity.kt
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MainActivityEntryPoint {
    fun samsungHealthHolder(): SamsungHealthHolder
}

private val samsungHealthHolder by lazy {
    EntryPointAccessors
        .fromApplication(applicationContext, MainActivityEntryPoint::class.java)
        .samsungHealthHolder()
}
```

---

## 3. Jetpack Compose — UI 선언형 프레임워크

### `@Composable` — UI를 그리는 함수

```kotlin
@Composable
fun HealthSourceScreen(onBack: () -> Unit) { ... }
```

### `hiltViewModel()` — ViewModel을 Compose에서 주입

```kotlin
val viewModel: HealthSourceViewModel = hiltViewModel()
```

### `collectAsStateWithLifecycle` — StateFlow를 Compose 상태로 변환

ViewModel의 데이터를 화면이 안전하게 구독.

```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

### `LaunchedEffect` — 컴포지션이 시작될 때 한 번 실행하는 코루틴

```kotlin
LaunchedEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
}
```

### `rememberLauncherForActivityResult` — Activity 결과 받기 (권한 등)

```kotlin
val hcPermissionLauncher = rememberLauncherForActivityResult(
    contract = PermissionController.createRequestPermissionResultContract(),
) { _ -> viewModel.onHealthConnectPermissionResult() }

// 사용:
hcPermissionLauncher.launch(viewModel.healthConnectPermissions)
```

### `LocalContext` / `LocalLifecycleOwner` — Compose에서 안드로이드 컨텍스트 가져오기

```kotlin
val context = LocalContext.current
val lifecycleOwner = LocalLifecycleOwner.current
```

### `Modifier` — UI 속성을 체인으로 조립

```kotlin
Box(modifier = Modifier.fillMaxSize().background(Color.White))
```

### `remember { mutableStateOf(...) }` — Compose 안 로컬 상태

```kotlin
var selectedTab by remember { mutableStateOf("home") }
```

---

## 4. 코루틴 — 비동기 처리

### `viewModelScope.launch` — ViewModel 안에서 비동기

ViewModel이 사라지면 자동 취소.

```kotlin
fun refresh() {
    viewModelScope.launch {
        val state = ...
        _uiState.update { it.copy(...) }
    }
}
```

### `lifecycleScope.launch` — Activity 안에서 비동기

Activity 라이프사이클에 묶임.

```kotlin
lifecycleScope.launch {
    runCatching { mgr.requestPermissions() }
}
```

### `StateFlow` / `MutableStateFlow` — 상태 흐름

UI에 노출하는 표준 방식.

```kotlin
private val _uiState = MutableStateFlow(HealthSourceUiState())
val uiState: StateFlow<HealthSourceUiState> = _uiState.asStateFlow()

// 상태 갱신
_uiState.update { it.copy(samsungReady = true) }
```
- `_uiState`는 변경 가능, 외부에는 `uiState`(읽기 전용)만 노출.

### `delay` / `coroutineScope` — 일시 정지 / 새 스코프

```kotlin
delay(1000L)  // 1초 대기 (suspend)
```

---

## 5. 안드로이드 라이프사이클

### Activity 콜백 순서

```
onCreate → onStart → onResume  (화면 보임)
                  ↓
                onPause → onStop → onDestroy  (화면 사라짐)
```

우리 코드에선:
- `onCreate`: holder.attach (Activity 인스턴스 등록)
- `onResume`: Samsung Health 권한 자동 요청 (다이얼로그는 RESUMED 필수)
- `onDestroy`: holder.detach (정리)

### `LifecycleEventObserver` — Compose에서 라이프사이클 관찰

```kotlin
LifecycleEventObserver { _, event ->
    if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
}
```

---

## 6. Hilt 의존성 흐름 (우리 코드 예시)

```
MainActivity
  └─ EntryPointAccessors → SamsungHealthHolder (@Singleton)

HealthSourceViewModel (@HiltViewModel)
  ├─ HealthConnectDataSource (@Singleton)
  │    └─ HealthConnectManager (GlucoFitModule.@Provides로 제공)
  ├─ SamsungHealthDataSource (@Singleton)
  │    └─ SamsungHealthHolder (@Singleton)
  ├─ SamsungHealthHolder (@Singleton)
  └─ HealthConnectManager (GlucoFitModule.@Provides로 제공)

HealthRepository (@Singleton)  ← MainViewModel이 사용
  ├─ MockHealthDataSource
  ├─ SamsungHealthDataSource
  └─ HealthConnectDataSource
```

Hilt가 위 그래프를 자동으로 해석해서 필요한 객체를 만들어 넣어줌.

---

## 7. 자주 헷갈리는 패턴

### `private val _x = MutableStateFlow(...)` + `val x = _x.asStateFlow()`

캡슐화. 외부는 읽기만 가능, 변경은 클래스 내부에서만.

### `?.let { }` — null이 아닐 때만 실행

```kotlin
holder.manager?.let { mgr ->
    mgr.requestPermissions()
}
```

### `it` — 람다 단일 인자 자동 이름

```kotlin
list.filter { it > 0 }  // it = 각 원소
```

### `?: return` — null 가드 패턴

```kotlin
val mgr = holder.manager ?: return  // null이면 함수 종료
// 여기부턴 mgr는 non-null 보장
mgr.requestPermissions()
```

### `data class.copy()` — 일부 필드만 바꾼 복사본

```kotlin
_uiState.update { it.copy(samsungReady = true) }
// 다른 필드는 그대로 유지하고 samsungReady만 변경
```

---

## 8. 공식 문서 링크

- [Kotlin 공식 가이드](https://kotlinlang.org/docs/home.html)
- [Hilt for Android](https://developer.android.com/training/dependency-injection/hilt-android)
- [Jetpack Compose 기초](https://developer.android.com/jetpack/compose/tutorial)
- [Coroutines on Android](https://developer.android.com/kotlin/coroutines)
- [Android 라이프사이클](https://developer.android.com/topic/libraries/architecture/lifecycle)
