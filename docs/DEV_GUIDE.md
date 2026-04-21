# S309 개발 가이드

프로젝트를 clone한 후 각 파트별로 개발을 시작하기 위한 가이드입니다.

---

## 공통 사항 (모든 파트)

### 1. 프로젝트 clone

```bash
git clone <레포지토리 URL>
cd S14P31S309
```

### 2. Git Hook 설치

커밋할 때 코드 스타일이 자동으로 맞춰지도록 hook을 설치합니다.
**clone 후 딱 한 번만** 실행하면 됩니다.

```bash
bash scripts/setup-hooks.sh
```

이후 `git commit`을 하면:
- **backend/** 파일 변경 시 → Java 코드 스타일 자동 수정 (Spotless)
- **frontend/** 파일 변경 시 → Kotlin 코드 스타일 자동 수정 (ktlint) + 품질 검사 (detekt)

> 린트를 일시적으로 건너뛰려면 `git commit --no-verify`
> 하지만 가급적 사용하지 마세요.

### 3. .env 관리 규칙

각 모듈에 `.env.example` 파일이 있습니다. 실제 `.env`는 git에 올라가지 않습니다.

```bash
# 각 모듈에서 example을 복사한 뒤 값 채우기
cp backend/.env.example backend/.env
cp ai/.env.example ai/.env
cp infra/.env.example infra/.env
```

- `.env`에는 비밀번호, API 키 같은 민감 정보를 넣습니다
- `.env`는 `.gitignore`에 등록되어 있어 절대 커밋되지 않습니다
- 키가 추가되면 `.env.example`에도 빈 값으로 반영해주세요

### 4. 브랜치 전략

```
master          ← 최종 배포 (태그로 버전 관리)
  ↑
release         ← 배포 전 QA (전체 기능)
  ↑
develop         ← 전체 통합 (모든 기능이 모이는 곳)
  ↑                    ↑
develop-client         팀 자체 feature 브랜치
  ↑
클라이언트 요구 feature 브랜치
```

| 브랜치 | 용도 | 직접 push |
|--------|------|-----------|
| `master` | 최종 배포 | 금지 (MR만) |
| `release` | 전체 기능 QA | 금지 (MR만) |
| `develop` | 전체 통합 | 금지 (MR만) |
| `develop-client` | 클라이언트 요구 기능 현황 관리 | 금지 (MR만) |
| `{파트}/feature-{기능}-{이슈번호}` | 기능 개발 | 허용 |
| `hotfix/{기능}-{이슈번호}` | 운영 긴급 수정 | 허용 |

#### 기능 유형별 MR 대상

| 기능 유형 | MR 보낼 곳 |
|-----------|-----------|
| **클라이언트 요구 기능** | `develop-client` → 이후 `develop-client`에서 `develop`으로 MR |
| **팀 자체 추가 기능** | `develop` 바로 |

> `develop-client`는 클라이언트 요구 기능 완성 현황을 추적하기 위한 브랜치입니다.  
> 배포 경로는 `develop → release → master` 하나로 통일됩니다.

#### 작업 흐름

```bash
# [클라이언트 요구 기능]
git checkout develop-client && git pull origin develop-client
git checkout -b fe/feature-login-S309-131
# ... 작업 ...
git push origin fe/feature-login-S309-131
# GitLab: fe/feature-login-S309-131 → develop-client MR
# develop-client가 쌓이면: develop-client → develop MR

# [팀 자체 기능]
git checkout develop && git pull origin develop
git checkout -b fe/feature-myfeature-S309-200
# ... 작업 ...
git push origin fe/feature-myfeature-S309-200
# GitLab: fe/feature-myfeature-S309-200 → develop MR
```

#### 배포 플로우

```bash
# develop → release (QA) → master → 태그
git tag v1.0.0
git push origin v1.0.0
```

---

## Backend (Spring Boot)

### 필요한 것

| 항목 | 버전 | 비고 |
|------|------|------|
| JDK | 21 (Eclipse Temurin) | `java -version`으로 확인 |
| IDE | IntelliJ IDEA | Community 또는 Ultimate |
| PostgreSQL | 17 | 로컬 설치 또는 Docker (Docker 권장) |

### Step 1. JDK 21 설치

[Eclipse Temurin 21 다운로드](https://adoptium.net/temurin/releases/?version=21)

설치 후 확인:
```bash
java -version
# openjdk version "21.x.x"
```

**Windows 환경 변수**: `JAVA_HOME`에 JDK 설치 경로 등록 필요
(예: `C:\Program Files\Eclipse Adoptium\jdk-21.x.x-hotspot`)

> 주의: 경로 끝에 `\bin`을 붙이면 안 됩니다.

### Step 2. PostgreSQL 설치 및 DB 생성

> **권장: Docker 방식 (A안)** — 설치/삭제 간편, OS 무관, 팀 환경 통일  
> 직접 설치(B안)는 Docker를 쓸 수 없는 경우에만 선택

---

#### A안: Docker로 실행 (권장)

**사전 조건**: Docker Desktop 설치 ([다운로드](https://www.docker.com/products/docker-desktop/))

로컬 개발용으로 PostgreSQL 컨테이너만 단독 실행:

```bash
docker run -d \
  --name s309-postgres \
  -e POSTGRES_DB=s309 \
  -e POSTGRES_USER=ssafy \
  -e POSTGRES_PASSWORD=ssafy1234 \
  -p 5432:5432 \
  -v s309-postgres-data:/var/lib/postgresql/data \
  postgres:17-alpine
```

실행 확인:
```bash
docker ps
# s309-postgres 컨테이너가 보이면 성공

docker logs s309-postgres
# "database system is ready to accept connections" 메시지 확인
```

접속 테스트 (선택):
```bash
docker exec -it s309-postgres psql -U ssafy -d s309
# psql 프롬프트가 뜨면 \q 입력으로 종료
```

**컨테이너 제어:**
```bash
docker stop s309-postgres     # 중지
docker start s309-postgres    # 재시작
docker rm -f s309-postgres    # 완전 삭제 (볼륨 유지)
docker volume rm s309-postgres-data  # 데이터까지 삭제
```

---

#### B안: 직접 설치

**Windows**: [PostgreSQL Installer](https://www.postgresql.org/download/windows/)에서 PostgreSQL 17 설치  
- 설치 중 `postgres` 계정 비밀번호 설정 (기억해둘 것)
- Port: `5432` (기본값)
- Locale: `C` 또는 `Default locale`

**WSL / Ubuntu**:
```bash
sudo apt update && sudo apt install -y postgresql-17 postgresql-client-17
sudo systemctl start postgresql
```

**macOS (Homebrew)**:
```bash
brew install postgresql@17
brew services start postgresql@17
```

설치 후 DB와 계정을 생성합니다:

```bash
# Windows: Start Menu > SQL Shell (psql)
# WSL/Linux: sudo -u postgres psql
# macOS: psql postgres
```

```sql
CREATE DATABASE s309;

CREATE USER ssafy WITH PASSWORD 'ssafy1234';
GRANT ALL PRIVILEGES ON DATABASE s309 TO ssafy;

-- PostgreSQL 15+ 에서는 스키마 권한 추가 부여 필요
\c s309
GRANT ALL ON SCHEMA public TO ssafy;

\q
```

### Step 3. .env 설정

```bash
cp backend/.env.example backend/.env
```

`backend/.env`를 열어서 값을 채웁니다:

```env
DB_URL=jdbc:postgresql://localhost:5432/s309
DB_USERNAME=ssafy
DB_PASSWORD=ssafy1234
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8080
JPA_DDL_AUTO=update
```

### Step 4. 실행

```bash
cd backend
./gradlew bootRun
```

정상 실행 확인:
- http://localhost:8080/api/health → `{"status":"UP"}`
- http://localhost:8080/swagger-ui.html → API 문서

### Step 5. IntelliJ 설정

1. File > Open > `backend/` 폴더 선택
2. File > Project Structure > SDK → **JDK 21** 선택
3. Settings > Build > Compiler > Annotation Processors → **Enable** 체크 (Lombok용)

**권장 플러그인:**
- **Lombok** — 필수 (getter/setter/builder 등 어노테이션)
- **EditorConfig** — 코드 스타일 자동 적용
- **SonarQube for IDE** — 실시간 코드 품질 검사

> IntelliJ Ultimate에는 Spring 지원이 내장되어 있습니다.
> Community 에디션이라면 **Spring Boot Assistant** 플러그인을 추가로 설치하세요.

### 수동 린트 (필요 시)

```bash
cd backend

# 코드 스타일 검사만
./gradlew spotlessCheck

# 자동 수정
./gradlew spotlessApply
```

> pre-commit hook이 설치되어 있으면 커밋 시 자동으로 `spotlessApply`가 실행됩니다.

### 주요 디렉토리 구조

```
backend/
├── src/main/java/com/ssafy/s309/
│   ├── config/           ← Spring 설정 (Security 등)
│   ├── controller/       ← API 컨트롤러
│   ├── service/          ← 비즈니스 로직
│   ├── repository/       ← DB 접근 (JPA)
│   ├── entity/           ← DB 엔티티
│   └── dto/              ← 요청/응답 DTO
├── src/main/resources/
│   ├── application.yml       ← 공통 설정 (환경변수 참조)
│   └── application-local.yml ← 로컬 개발용 설정
└── build.gradle.kts          ← 빌드 설정
```

---

## Frontend (Android / Kotlin Compose)

### 필요한 것

| 항목 | 버전 | 비고 |
|------|------|------|
| Android Studio | Otter 3 이상 | 최신 안정 버전 |
| Kotlin | 2.2.10 | 프로젝트에 설정됨 |
| Gradle | 9.3.1 | 프로젝트에 설정됨 |
| compileSdk | 36 | build.gradle.kts에 설정됨 |

### Step 1. Android Studio 설치

[Android Studio 다운로드](https://developer.android.com/studio)

설치 시 **Standard** 선택 (기본 SDK 포함)

### Step 2. SDK 설정

Android Studio > Settings > Languages & Frameworks > **Android SDK**

**SDK Platforms 탭:**
- `Android 16.0 ("Baklava")` — API Level 36 체크

**SDK Tools 탭:**
- Android SDK Build-Tools (최신)
- Android SDK Command-line Tools
- Android Emulator
- Android SDK Platform-Tools

### Step 3. 에뮬레이터 만들기

1. Device Manager (우측 사이드바) > **Create Virtual Device**
2. 디바이스: **Pixel 8** 권장
3. 시스템 이미지: **API 36** 다운로드 후 선택
4. Finish > 에뮬레이터 실행 확인

> 실기기를 쓸 경우 개발자 옵션 > USB 디버깅을 활성화하세요.

### Step 4. 프로젝트 열기 및 실행

1. File > Open > `frontend/` 폴더 선택
2. Gradle Sync 자동 실행 (최초 시 수 분 소요)
3. Sync 완료 후 상단 ▶ 버튼 클릭
4. 에뮬레이터 또는 실기기 선택

### Step 5. Android Studio 플러그인

- **ktlint** — Kotlin 코드 스타일 검사
- **Detekt** — 코드 품질 분석
- **JSON To Kotlin Class** — API 응답을 data class로 변환

### 수동 린트 (필요 시)

```bash
cd frontend

# 코드 스타일 자동 수정
./gradlew ktlintFormat

# 코드 품질 검사
./gradlew detekt
```

> pre-commit hook이 설치되어 있으면 커밋 시 자동 실행됩니다.

### 프로젝트 기술 스택

| 라이브러리 | 용도 |
|-----------|------|
| Jetpack Compose | UI (XML 대신 선언형 UI) |
| Material3 | 디자인 시스템 |
| Hilt | 의존성 주입 (DI) |
| Navigation Compose | 화면 이동 |
| Retrofit 2 + OkHttp | HTTP API 호출 |
| Coil 3 | 이미지 로딩 |
| kotlinx.serialization | JSON 직렬화 |

### 주요 디렉토리 구조

```
frontend/
├── app/src/main/java/com/ssafy/s309/
│   ├── di/              ← Hilt 모듈 정의
│   ├── data/
│   │   ├── api/         ← Retrofit API 인터페이스
│   │   ├── model/       ← 응답 data class
│   │   └── repository/  ← 데이터 접근 계층
│   ├── ui/
│   │   ├── screen/      ← Composable 화면
│   │   ├── component/   ← 재사용 UI 컴포넌트
│   │   └── theme/       ← 테마/색상/타이포
│   └── navigation/      ← 네비게이션 그래프
├── gradle/
│   └── libs.versions.toml  ← 버전 카탈로그 (의존성 버전 관리)
└── build.gradle.kts
```

---

## AI (FastAPI / Python)

### 필요한 것

| 항목 | 버전 | 비고 |
|------|------|------|
| Python | 3.12 | `python --version`으로 확인 |
| IDE | VS Code 권장 | PyCharm도 가능 |

### Step 1. Python 설치

[Python 3.12 다운로드](https://www.python.org/downloads/)

(권장) uv 패키지 매니저 설치:
```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
```

### Step 2. .env 설정

```bash
cp ai/.env.example ai/.env
```

현재는 기본값으로 충분합니다. 추후 AI 모델 키가 추가되면 여기에 넣습니다.

### Step 3. 의존성 설치 및 실행

```bash
cd ai

# pip 사용 시
pip install -r requirements.txt

# uv 사용 시 (더 빠름)
uv pip install -r requirements.txt

# 서버 실행
uvicorn app.main:app --reload
```

정상 실행 확인:
- http://localhost:8000/health
- http://localhost:8000/docs → API 문서 (Swagger)

### Step 4. VS Code 확장

- **Python** (ms-python) — 필수
- **Ruff** — Python 린터/포매터
- **EditorConfig** — 코드 스타일 적용

### 주요 디렉토리 구조

```
ai/
├── app/
│   ├── main.py          ← FastAPI 앱 진입점
│   ├── routers/         ← API 라우터
│   ├── services/        ← 비즈니스 로직
│   ├── models/          ← Pydantic 모델
│   └── config.py        ← 설정 (pydantic-settings)
├── requirements.txt     ← 의존성 목록
└── Dockerfile
```

---

## Infra (Docker)

> 현재 Docker Compose 설정까지 구성된 상태입니다. Jenkins CI/CD는 추후 세팅 예정입니다.

### 필요한 것

| 항목 | 버전 | 비고 |
|------|------|------|
| Docker Desktop | 최신 | `docker --version`으로 확인 |
| Docker Compose | v2 | Docker Desktop에 포함 |

### Step 1. Docker 설치

[Docker Desktop 다운로드](https://www.docker.com/products/docker-desktop/)

설치 확인:
```bash
docker --version
docker compose version
```

### Step 2. .env 설정

```bash
cp infra/.env.example infra/.env
```

`infra/.env`를 열어서 값을 채웁니다:

```env
POSTGRES_DB=s309
POSTGRES_USER=ssafy
POSTGRES_PASSWORD=ssafy1234
DB_URL=jdbc:postgresql://postgres:5432/s309
DB_USERNAME=ssafy
DB_PASSWORD=ssafy1234
SPRING_PROFILES_ACTIVE=local
AI_DEBUG=false
```

> **주의**: Docker 내에서는 DB 호스트가 `localhost`가 아니라 `postgres`입니다.
> (`docker-compose.yml`의 서비스 이름으로 접근)

### Step 3. 전체 서비스 실행

```bash
cd infra
docker compose up -d
```

실행 확인:
```bash
docker compose ps        # 컨테이너 상태 확인
docker compose logs -f   # 로그 확인
```

접속:
- http://localhost/api/health → Backend (Nginx 경유)
- http://localhost/ai/health → AI (Nginx 경유)

### 자주 쓰는 Docker 명령어

```bash
# 전체 중지
docker compose down

# 전체 중지 + DB 데이터도 삭제
docker compose down -v

# 특정 서비스만 재빌드
docker compose up -d --build backend

# 로그 확인 (특정 서비스)
docker compose logs -f backend
```

### Docker Compose 구조

```
infra/
├── docker-compose.yml   ← 서비스 정의
├── .env.example         ← 환경변수 템플릿
├── nginx/
│   └── nginx.conf       ← 리버스 프록시 설정
└── postgres/
    └── init/            ← DB 초기화 SQL (자동 실행)
```

서비스 구성:

| 서비스 | 이미지 | 포트 | 역할 |
|--------|--------|------|------|
| postgres | postgres:17-alpine | 5432 | 데이터베이스 |
| backend | 자체 빌드 | 8080 | Spring Boot API |
| ai | 자체 빌드 | 8000 | FastAPI AI 서버 |
| nginx | nginx:alpine | 80 | 리버스 프록시 |

---

## 코드 컨벤션

프로젝트 루트의 `.editorconfig`가 모든 파트에 공통 적용됩니다. EditorConfig를 지원하는 IDE라면 별도 설정 없이 자동 반영됩니다.

**공통 규칙:**
- 인코딩: UTF-8
- 줄바꿈: LF (Windows에서도 LF 통일)
- 들여쓰기: 스페이스 4칸 (yml/json/toml만 2칸)
- 파일 끝 빈 줄 삽입
- 줄 끝 공백 제거

### Backend (Java)

**도구**: Spotless + Google Java Style (커밋 시 자동 적용)

| 항목 | 규칙 |
|------|------|
| 들여쓰기 | 스페이스 2칸 (Google Java Style 기본값) |
| 최대 줄 길이 | 100자 |
| import 정렬 | 자동 정리, 사용하지 않는 import 제거 |
| 중괄호 | K&R 스타일 (여는 중괄호는 같은 줄) |
| 파일 끝 | 반드시 빈 줄 1개 |

**네이밍:**

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스 | PascalCase | `UserService`, `HealthController` |
| 메서드/변수 | camelCase | `findById`, `userName` |
| 상수 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 패키지 | 소문자 | `com.ssafy.s309.controller` |
| DTO | 접미사 `Request`/`Response` | `LoginRequest`, `UserResponse` |
| Entity | 테이블명과 일치 | `User`, `Device` |

**패키지 구조 규칙:**

```
controller/  ← API 진입점만. 비즈니스 로직 금지
service/     ← 비즈니스 로직. Repository 호출
repository/  ← JPA 인터페이스만
entity/      ← DB 테이블 매핑
dto/         ← 요청/응답 객체. Entity 직접 노출 금지
config/      ← Spring 설정 클래스
```

**기타:**
- Lombok 사용: `@Getter`, `@Builder`, `@RequiredArgsConstructor` 권장
- `@Setter` 사용 지양 (불변 객체 지향)
- Entity에 `@Builder` 사용 시 `@AllArgsConstructor(access = AccessLevel.PRIVATE)` 함께 사용
- API 경로는 `/api/` prefix 필수 (예: `/api/users`, `/api/devices`)
- REST 규칙: `GET` 조회, `POST` 생성, `PUT` 전체 수정, `PATCH` 부분 수정, `DELETE` 삭제

### Frontend (Kotlin)

**도구**: ktlint (스타일 자동 수정) + detekt (품질 검사) — 커밋 시 자동 적용

| 항목 | 규칙 |
|------|------|
| 들여쓰기 | 스페이스 4칸 |
| 최대 줄 길이 | 120자 |
| wildcard import | 금지 (`import package.*` 사용 불가) |
| 함수 최대 길이 | 50줄 (detekt) |
| 함수 파라미터 | 최대 8개 (detekt) |
| 클래스 함수 수 | 최대 15개 (detekt) |

**네이밍:**

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스/인터페이스 | PascalCase | `UserRepository`, `DeviceState` |
| 함수/변수 | camelCase | `fetchUsers`, `isLoading` |
| 상수 | UPPER_SNAKE_CASE | `BASE_URL` |
| Composable 함수 | PascalCase (예외) | `HomeScreen`, `DeviceCard` |
| 패키지 | 소문자 | `com.ssafy.s309.ui.screen` |

> Compose 함수는 일반 Kotlin 함수와 달리 PascalCase를 사용합니다.
> `ui/`, `composable/`, `screen/` 하위 파일에서는 detekt가 이 규칙을 허용합니다.

**Compose 규칙:**
- State는 ViewModel에서 관리, Composable은 표시만
- `remember`/`mutableStateOf` 대신 `StateFlow` + `collectAsStateWithLifecycle()` 권장
- Composable 파라미터 순서: 필수값 → 옵션값 → modifier → content lambda

**패키지 구조 규칙:**

```
di/          ← Hilt 모듈만
data/api/    ← Retrofit 인터페이스만 (구현 금지)
data/model/  ← data class (서버 응답 매핑)
data/repository/ ← 데이터 접근 로직
ui/screen/   ← 화면 단위 Composable
ui/component/← 재사용 UI 조각
ui/theme/    ← 색상, 타이포, Shape
navigation/  ← 네비게이션 그래프
```

### AI (Python)

**도구**: Ruff (VS Code 확장)

| 항목 | 규칙 |
|------|------|
| 들여쓰기 | 스페이스 4칸 |
| 최대 줄 길이 | 120자 |
| 따옴표 | 큰따옴표 `"` (Ruff 기본값) |
| import 정렬 | isort 규칙 (Ruff 내장) |

**네이밍:**

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스 | PascalCase | `UserService`, `DeviceModel` |
| 함수/변수 | snake_case | `get_user`, `device_id` |
| 상수 | UPPER_SNAKE_CASE | `API_VERSION` |
| 파일명 | snake_case | `user_router.py` |
| Pydantic 모델 | PascalCase + 접미사 | `UserCreate`, `DeviceResponse` |

**패키지 구조 규칙:**

```
routers/     ← FastAPI 라우터 (엔드포인트 정의만)
services/    ← 비즈니스 로직
models/      ← Pydantic 모델 (요청/응답 스키마)
config.py    ← 환경 설정 (pydantic-settings)
```

**기타:**
- Type hint 필수 (함수 파라미터, 반환값)
- Pydantic v2 문법 사용 (`model_validator` 등)
- API 경로 prefix: `/ai/` (Nginx에서 라우팅)

### Infra

**Docker/Nginx 컨벤션:**

| 항목 | 규칙 |
|------|------|
| 컨테이너 이름 | `s309-서비스명` (예: `s309-backend`) |
| 이미지 태그 | 항상 버전 명시 (예: `postgres:17-alpine`, `nginx:alpine`) |
| 환경변수 | `.env` 파일로 관리, docker-compose.yml에 직접 값 넣지 않기 |
| 볼륨 | named volume 사용 (예: `postgres-data`) |
| 포트 | 호스트:컨테이너 형식 명시 |

---

## 포트 정리

| 서비스 | 포트 | URL |
|--------|------|-----|
| Backend | 8080 | http://localhost:8080 |
| AI | 8000 | http://localhost:8000 |
| PostgreSQL | 5432 | - |
| Nginx | 80 | http://localhost |
| Swagger (BE) | 8080 | http://localhost:8080/swagger-ui.html |
| Swagger (AI) | 8000 | http://localhost:8000/docs |

---

## 문제가 생겼을 때

### Backend

| 증상 | 원인 | 해결 |
|------|------|------|
| `Access denied for user` | DB 계정 불일치 | `.env`의 DB_USERNAME/PASSWORD 확인 |
| 로그인 화면 뜸 | Spring Security | `/api/health`, `/swagger-ui.html`은 인증 없이 접근 가능. 다른 API는 인증 필요 |
| `Port 5432 already in use` | PostgreSQL이 이미 실행 중 | `netstat -aon \| findstr 5432`으로 확인 후 중복 서비스 중지 |
| `password authentication failed` | DB 비밀번호 불일치 | `.env`와 PostgreSQL 계정 비밀번호 일치 확인 |
| `database "s309" does not exist` | DB 미생성 | Step 2의 `CREATE DATABASE` 실행 확인 |
| Gradle JVM 오류 | JAVA_HOME 불일치 | `JAVA_HOME`이 JDK 21을 가리키는지 확인 (경로 끝에 `\bin` 없어야 함) |

### Frontend

| 증상 | 원인 | 해결 |
|------|------|------|
| Gradle Sync 실패 | SDK 미설치 | SDK Manager에서 API 36 + Build-Tools 설치 |
| 에뮬레이터 안 뜸 | 시스템 이미지 없음 | Device Manager에서 API 36 이미지 다운로드 |
| ktlint 에러 | 코드 스타일 불일치 | `./gradlew ktlintFormat`으로 자동 수정 |

### AI

| 증상 | 원인 | 해결 |
|------|------|------|
| `ModuleNotFoundError` | 의존성 미설치 | `pip install -r requirements.txt` |
| 포트 충돌 | 8000 포트 사용 중 | `uvicorn app.main:app --port 8001` |

### Docker

| 증상 | 원인 | 해결 |
|------|------|------|
| `connection refused` | 서비스 미시작 | `docker compose ps`로 상태 확인 |
| DB 연결 실패 | .env 미설정 | `infra/.env` 파일 확인 |
| 빌드 실패 | Dockerfile 문제 | `docker compose logs 서비스명`으로 확인 |
