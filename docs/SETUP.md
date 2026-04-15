# 개발 환경 셋업 가이드

팀원들이 프로젝트를 clone한 후 개발 환경을 구성하기 위한 가이드입니다.

## 1. 필수 소프트웨어 설치

### JDK 21 (Backend)

- [Eclipse Temurin 21 LTS](https://adoptium.net/temurin/releases/?version=21) 다운로드 및 설치
- `JAVA_HOME` 환경 변수 설정
- 확인: `java -version` → `openjdk version "21.x.x"`

### Android Studio Panda 3 (Frontend)

#### 설치

1. [Android Studio 다운로드 페이지](https://developer.android.com/studio)에서 최신 버전 다운로드
2. 설치 마법사 실행 → **Standard** 설치 선택 (기본 SDK 포함)
3. 설치 완료 후 Android Studio 실행

#### SDK 설정

1. File > Settings > Languages & Frameworks > **Android SDK**
2. **SDK Platforms** 탭:
   - `Android 16.0 ("Baklava")` — API Level 36 체크
3. **SDK Tools** 탭:
   - Android SDK Build-Tools (최신)
   - Android SDK Command-line Tools
   - Android Emulator
   - Android SDK Platform-Tools
4. Apply 클릭하여 설치

#### 환경 변수 설정 (Windows)

1. Windows 검색 > "환경 변수" > 시스템 환경 변수 편집
2. 시스템 변수에 추가:
   - 변수명: `ANDROID_HOME`
   - 값: `C:\Users\<사용자명>\AppData\Local\Android\Sdk`
3. `Path` 변수에 다음 추가:
   ```
   %ANDROID_HOME%\platform-tools
   %ANDROID_HOME%\tools
   ```
4. 확인:
   ```bash
   adb --version
   # Android Debug Bridge version x.x.x
   ```

#### 에뮬레이터 생성

1. Android Studio > Device Manager (우측 사이드바)
2. **Create Virtual Device** 클릭
3. 디바이스 선택: Pixel 8 (권장)
4. 시스템 이미지: API 36 다운로드 후 선택
5. Finish → 에뮬레이터 실행 확인

#### 프로젝트 열기

1. File > Open > `frontend/` 폴더 선택
2. Gradle Sync가 자동 실행됨 (최초 시 수 분 소요)
3. Sync 완료 후 상단 ▶ 버튼으로 앱 실행
4. 에뮬레이터 또는 USB 연결된 실기기 선택

> **참고**: 실기기 사용 시 개발자 옵션 > USB 디버깅 활성화 필요

### Python 3.12 (AI)

- [Python 3.12](https://www.python.org/downloads/) 다운로드 및 설치
- (권장) [uv](https://docs.astral.sh/uv/getting-started/installation/) 패키지 매니저 설치:
  ```bash
  curl -LsSf https://astral.sh/uv/install.sh | sh
  ```

### Docker (Infra)

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) 설치
- 확인: `docker --version` && `docker compose version`

### PostgreSQL 17 (로컬 개발용)

> **권장: Docker 방식** — 아래 Docker 설치만 있으면 DB 별도 설치 불필요.  
> 직접 설치는 Docker를 쓸 수 없는 환경에서만 선택합니다.

#### Docker로 실행 (권장)

```bash
docker run -d \
  --name s309-postgres \
  -e POSTGRES_DB=s309 \
  -e POSTGRES_USER=ssafy \
  -e POSTGRES_PASSWORD=ssafy \
  -p 5432:5432 \
  -v s309-postgres-data:/var/lib/postgresql/data \
  postgres:17-alpine
```

확인:
```bash
docker ps
# s309-postgres 확인

docker exec -it s309-postgres psql -U ssafy -d s309 -c "SELECT 1;"
# 결과 1 나오면 성공
```

---

#### Windows 직접 설치

1. [PostgreSQL Installer](https://www.postgresql.org/download/windows/) 다운로드
2. 설치 중 설정:
   - Port: **5432** (기본값)
   - Superuser (`postgres`) 비밀번호 설정
   - Locale: `C` 또는 `Default locale`
3. 설치 완료 후 환경 변수에 `C:\Program Files\PostgreSQL\17\bin` 추가
4. 확인:
   ```bash
   psql --version
   # psql (PostgreSQL) 17.x
   ```

#### WSL / Ubuntu 직접 설치

```bash
sudo apt update
sudo apt install -y postgresql-17 postgresql-client-17
sudo systemctl start postgresql
```

#### macOS 직접 설치

```bash
brew install postgresql@17
brew services start postgresql@17
```

#### 데이터베이스 및 계정 설정 (직접 설치 시)

```bash
# Windows: Start Menu > SQL Shell (psql) 실행
# WSL/Linux: sudo -u postgres psql
# macOS: psql postgres
```

```sql
-- 데이터베이스 생성
CREATE DATABASE s309;

-- 개발용 계정 생성 (application-local.yml의 기본값과 일치)
CREATE USER ssafy WITH PASSWORD 'ssafy';
GRANT ALL PRIVILEGES ON DATABASE s309 TO ssafy;

-- PostgreSQL 15+ 에서는 스키마 권한 추가 부여 필요
\c s309
GRANT ALL ON SCHEMA public TO ssafy;

-- 확인
\l
\du

\q
```

#### Spring Boot 연동 확인

`backend/src/main/resources/application-local.yml` 설정이 일치하는지 확인:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/s309
    username: ssafy
    password: ssafy
```

#### 접속 테스트

```bash
# Docker 방식
docker exec -it s309-postgres psql -U ssafy -d s309 -c "SELECT 1;"

# 직접 설치
psql -U ssafy -d s309 -c "SELECT 1;"
# 결과:
#  ?column?
# ----------
#         1
# (1 row)
```

이 명령이 성공하면 Backend에서 `./gradlew bootRun` 시 DB 연결 에러 없이 실행됩니다.

## 2. 프로젝트 실행

### Backend

```bash
cd backend
./gradlew bootRun
# http://localhost:8080/api/health 확인
# http://localhost:8080/swagger-ui.html 에서 API 문서 확인
```

### Frontend

1. Android Studio에서 `frontend/` 폴더 열기
2. Gradle Sync 실행 (자동 또는 File > Sync Project with Gradle Files)
3. 에뮬레이터 또는 실기기 연결 후 Run

### AI

```bash
cd ai
pip install -r requirements.txt
uvicorn app.main:app --reload
# http://localhost:8000/health 확인
# http://localhost:8000/docs 에서 API 문서 확인
```

### Docker Compose (전체 실행)

```bash
cd infra
cp .env.example .env
docker compose up -d
# http://localhost/api/health (Backend)
# http://localhost/ai/health (AI)
```

## 3. IDE 설정

### IntelliJ IDEA (Backend)

- File > Project Structure > SDK → JDK 21 선택
- Lombok 플러그인 설치
- Annotation Processor 활성화: Settings > Build > Compiler > Annotation Processors
- 권장 플러그인:
  - **Lombok** — 필수
  - **Spring Boot Assistant** — 자동완성 지원
  - **EditorConfig** — 코드 스타일 자동 적용
  - **SonarQube for IDE** — 실시간 코드 품질 검사

### Android Studio (Frontend)

- 권장 플러그인:
  - **ktlint** — Kotlin 코드 스타일 검사
  - **Detekt** — 코드 품질 분석
  - **EditorConfig** — 기본 내장
  - **JSON To Kotlin Class** — API 응답을 data class로 변환

### VS Code (AI)

- 권장 확장:
  - **Python** (ms-python) — 필수
  - **Ruff** — Python 린터/포매터
  - **EditorConfig** — 코드 스타일 적용

## 4. 코드 스타일 및 린트

### Frontend (Kotlin)

프로젝트에 ktlint와 detekt가 설정되어 있습니다.

```bash
cd frontend

# 코드 스타일 검사
./gradlew ktlintCheck

# 코드 스타일 자동 수정
./gradlew ktlintFormat

# 코드 품질 검사 (복잡도, 잠재적 버그)
./gradlew detekt
```

### Backend (Java)

Spotless + Google Java Style이 설정되어 있습니다.

```bash
cd backend

# 코드 스타일 검사
./gradlew spotlessCheck

# 코드 스타일 자동 수정
./gradlew spotlessApply
```

### 커밋 전 확인 습관

코드를 커밋하기 전에 린트를 실행하는 습관을 들여주세요.

```bash
# Frontend
cd frontend && ./gradlew ktlintFormat && ./gradlew detekt

# Backend
cd backend && ./gradlew spotlessApply
```
