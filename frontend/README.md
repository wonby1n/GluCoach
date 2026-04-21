# glucoach Android

Kotlin + Jetpack Compose 기반 Android 앱

## 실행 방법

### 요구사항

- Android Studio Meerkat 이상
- JDK 17
- Android SDK (compileSdk 36, minSdk 24)

### Android Studio에서 실행

1. Android Studio 실행
2. `File > Open` → `frontend` 폴더 선택
3. Gradle sync 완료 대기 (우상단 코끼리 아이콘)
4. Device Manager에서 에뮬레이터 생성 또는 실기기 연결
5. ▶ **Run** 버튼 클릭

### 터미널에서 빌드

```bash
# Debug APK 빌드
./gradlew assembleDebug          # Mac/Linux
./gradlew.bat assembleDebug      # Windows

# 출력 경로
app/build/outputs/apk/debug/app-debug.apk
```

### 기기에 APK 설치

```bash
# adb가 PATH에 있을 경우
adb install app/build/outputs/apk/debug/app-debug.apk

# Windows (전체 경로)
& "C:\Users\<user>\AppData\Local\Android\Sdk\platform-tools\adb.exe" install app\build\outputs\apk\debug\app-debug.apk
```

---

## 프로젝트 구조

```
frontend/
├── app/src/main/
│   ├── java/com/ssafy/s309/
│   │   ├── MainActivity.kt
│   │   ├── navigation/
│   │   │   └── AppNavigation.kt       # 네비게이션 그래프
│   │   └── ui/
│   │       ├── screen/auth/
│   │       │   ├── LandingScreen.kt   # 스플래시 (2초 후 이동)
│   │       │   ├── LoginScreen.kt     # Sign In / Sign Up 선택
│   │       │   ├── SignInScreen.kt    # 로그인 폼
│   │       │   └── SignUpScreen.kt    # 회원가입 폼
│   │       └── theme/
│   │           ├── Color.kt
│   │           ├── Theme.kt
│   │           └── Type.kt
│   └── res/
│       └── drawable/
│           └── ic_glucoach_logo.png
└── docs/
    ├── icon/icon.png
    └── photo/                         # 화면 디자인 시안
```

---

## 화면 흐름

```
Landing (2초) → Login → Sign In
                      → Sign Up → (이미 회원이세요?) → Sign In
```

---

## 색상표

`ui/theme/Color.kt` 기준

| 이름 | Hex | 용도 |
|------|-----|------|
| Primary | `#71C1D2` | 버튼, 포커스 색상 |
| PrimaryDark | `#4EA8BC` | 강조 |
| PrimaryLight | `#DDF3F8` | 연한 배경 |
| Background | `#F8FCFD` | 앱 배경 |
| Surface | `#FFFFFF` | 카드/시트 배경 |
| TextPrimary | `#222222` | 본문 텍스트 |
| TextSecondary | `#757575` | 보조 텍스트 |
| TextThird | `#A9A9A9` | 힌트/비활성 텍스트 |
| Border | `#D9D9D9` | 구분선, 테두리 |
| Success | `#4DBA87` | 성공 상태 |
| Warning | `#F6B44C` | 경고 상태 |
| Error | `#E96A6A` | 오류 상태 |

---

## 기술 스택

| 항목 | 버전 |
|------|------|
| Kotlin | 2.2.10 |
| Jetpack Compose BOM | 2025.03.01 |
| Navigation Compose | 2.8.9 |
| Hilt | 2.55 |
| Retrofit | 2.11.0 |
| Coil | 3.1.0 |
