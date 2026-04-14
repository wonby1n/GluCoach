# 개발 환경 셋업 가이드

팀원들이 프로젝트를 clone한 후 개발 환경을 구성하기 위한 가이드입니다.

## 1. 필수 소프트웨어 설치

### JDK 21 (Backend)

- [Eclipse Temurin 21 LTS](https://adoptium.net/temurin/releases/?version=21) 다운로드 및 설치
- `JAVA_HOME` 환경 변수 설정
- 확인: `java -version` → `openjdk version "21.x.x"`

### Android Studio Panda 3 (Frontend)

- [Android Studio](https://developer.android.com/studio) 최신 버전 다운로드
- 설치 후 SDK Manager에서 다음 설치:
  - Android SDK Platform 36
  - Android SDK Build-Tools
  - Android Emulator
- `ANDROID_HOME` 환경 변수 설정

### Python 3.12 (AI)

- [Python 3.12](https://www.python.org/downloads/) 다운로드 및 설치
- (권장) [uv](https://docs.astral.sh/uv/getting-started/installation/) 패키지 매니저 설치:
  ```bash
  curl -LsSf https://astral.sh/uv/install.sh | sh
  ```

### Docker (Infra)

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) 설치
- 확인: `docker --version` && `docker compose version`

### MySQL 8.4 LTS (로컬 개발용, Docker 미사용 시)

#### Windows 설치

1. [MySQL Installer](https://dev.mysql.com/downloads/installer/) 다운로드 (mysql-installer-community)
2. 설치 유형: **Custom** 선택
3. 설치 대상에서 **MySQL Server 8.4.x** 선택
4. 설치 진행 후 Configuration 단계에서:
   - Type: **Development Computer**
   - Port: **3306** (기본값 유지)
   - Root 비밀번호 설정 (기억해 둘 것)
   - Windows Service로 등록 체크 (자동 시작)
5. 설치 완료 후 환경 변수 설정:
   - `Path`에 `C:\Program Files\MySQL\MySQL Server 8.4\bin` 추가
6. 확인:
   ```bash
   mysql --version
   # mysql  Ver 8.4.x for Win64 on x86_64
   ```

#### WSL 설치

```bash
sudo apt update
sudo apt install -y mysql-server-8.0
# (Ubuntu 저장소 기준 — 8.4가 필요하면 MySQL APT Repository 추가)
sudo systemctl start mysql
sudo mysql_secure_installation
```

#### 데이터베이스 및 계정 설정

MySQL 설치 후 아래 SQL을 실행하여 프로젝트 DB와 개발용 계정을 생성합니다.

```bash
mysql -u root -p
```

```sql
-- 데이터베이스 생성
CREATE DATABASE s309 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 개발용 계정 생성 (application-local.yml의 기본값과 일치)
CREATE USER 'ssafy'@'localhost' IDENTIFIED BY 'ssafy';
GRANT ALL PRIVILEGES ON s309.* TO 'ssafy'@'localhost';
FLUSH PRIVILEGES;

-- 확인
SHOW DATABASES;
SELECT user, host FROM mysql.user WHERE user = 'ssafy';
```

#### Spring Boot 연동 확인

`backend/src/main/resources/application-local.yml`의 DB 설정이 위 계정과 일치하는지 확인합니다.

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/s309
    username: root      # 또는 ssafy
    password: ssafy     # 설치 시 설정한 비밀번호로 변경
```

비밀번호를 다르게 설정한 경우 `application-local.yml`을 수정하세요.

#### 접속 테스트

```bash
mysql -u ssafy -p -e "SELECT 1"
# Enter password: ssafy
# +---+
# | 1 |
# +---+
# | 1 |
# +---+
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

### IntelliJ IDEA / Android Studio

- File > Project Structure > SDK → JDK 21 선택
- Lombok 플러그인 설치 (Backend 개발 시)
- Annotation Processor 활성화: Settings > Build > Compiler > Annotation Processors

### VS Code (AI 개발 시)

- Python 확장 설치
- 인터프리터를 Python 3.12로 설정
