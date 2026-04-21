# S309 인프라 배포 가이드

EC2 서버를 받은 후 Jenkins + Docker + PostgreSQL 기반의 CI/CD 환경을 구축하는 단계별 가이드입니다.

---

## 전체 흐름

```
EC2 접속 (SSH)
    → 기본 패키지 설치 (Docker, Java)
    → Jenkins 설치 및 계정 설정
    → 프로젝트 clone
    → .env 파일 작성
    → Jenkins 파이프라인 설정
    → GitLab 웹훅 연결
    → 첫 배포 확인
```

---

## 1단계 — EC2 SSH 접속

SSAFY에서 `.pem` 키파일과 EC2 IP를 받습니다.

```bash
# 키파일 권한 설정 (최초 1회)
chmod 400 ssafy-key.pem

# EC2 접속
ssh -i ssafy-key.pem ubuntu@<EC2-IP>
```

> Windows 환경이라면 PowerShell 또는 Git Bash에서 실행하세요.

### AWS 보안 그룹 포트 열기

AWS 콘솔 → EC2 → 보안 그룹 → 인바운드 규칙 편집에서 아래 포트를 추가합니다.

| 포트 | 프로토콜 | 용도 |
|------|----------|------|
| 22 | TCP | SSH 접속 |
| 80 | TCP | HTTP (Nginx) |
| 443 | TCP | HTTPS (SSL, 추후 설정) |
| 8080 | TCP | Jenkins 웹 UI |

> PostgreSQL(5432), Redis(6379)는 외부에 열지 않습니다. Docker 내부 네트워크로만 통신하고, DataGrip 등 외부 툴은 SSH 터널로 접속합니다.

---

## 2단계 — EC2 기본 패키지 설치

EC2 서버에 접속한 상태에서 실행합니다.

```bash
# 패키지 목록 업데이트
sudo apt update && sudo apt upgrade -y

# Docker 설치
sudo apt install -y docker.io docker-compose-plugin

# Docker 서비스 시작 및 자동 시작 등록
sudo systemctl enable --now docker

# 현재 사용자(ubuntu)를 docker 그룹에 추가 (sudo 없이 docker 사용)
sudo usermod -aG docker ubuntu

# 적용을 위해 재로그인
exit
ssh -i ssafy-key.pem ubuntu@<EC2-IP>

# 확인
docker --version
docker compose version

# Java 21 설치 (Jenkins 실행에 필요)
sudo apt install -y openjdk-21-jdk
java -version
```

---

## 3단계 — Jenkins 설치 및 계정 설정

### 3-1. Jenkins 설치

```bash
# Jenkins 공식 저장소 등록
curl -fsSL https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key | sudo tee \
  /usr/share/keyrings/jenkins-keyring.asc > /dev/null

echo deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] \
  https://pkg.jenkins.io/debian-stable binary/ | sudo tee \
  /etc/apt/sources.list.d/jenkins.list > /dev/null

sudo apt update && sudo apt install -y jenkins

# Jenkins 시작 및 자동 시작 등록
sudo systemctl enable --now jenkins

# 상태 확인
sudo systemctl status jenkins
```

### 3-2. 초기 설정 (브라우저)

1. 브라우저에서 `http://<EC2-IP>:8080` 접속
2. 초기 admin 비밀번호 확인 후 입력:
   ```bash
   sudo cat /var/lib/jenkins/secrets/initialAdminPassword
   ```
3. **"Install suggested plugins"** 선택 (권장 플러그인 자동 설치)
4. admin 계정 생성 (아이디/비밀번호 팀원과 공유)

### 3-3. 추가 플러그인 설치

Jenkins 관리 → Plugins → Available plugins 에서 아래를 검색해 설치합니다.

- `GitLab` — GitLab 웹훅 트리거
- `SSH Agent` — SSH 키를 사용한 원격 배포
- `Docker Pipeline` — 파이프라인에서 Docker 사용

---

## 4단계 — 프로젝트 clone

```bash
cd /home/ubuntu
git clone <GitLab 레포지토리 URL> S14P31S309
cd S14P31S309
```

> GitLab이 Private 레포라면 EC2의 SSH 공개키를 GitLab에 등록해야 합니다.
>
> ```bash
> # EC2에서 SSH 키 생성 (없는 경우)
> ssh-keygen -t ed25519 -C "ec2-deploy"
>
> # 공개키 출력 → GitLab 프로필 → SSH Keys에 붙여넣기
> cat ~/.ssh/id_ed25519.pub
> ```

---

## 5단계 — .env 파일 작성

`docker-compose.yml`이 참조하는 환경변수 파일입니다.
**이 파일은 git에 올리지 않습니다** (`.gitignore`에 이미 등록됨).

```bash
nano /home/ubuntu/S14P31S309/infra/.env
```

아래 내용을 작성합니다. 비밀번호는 팀 내부에서 안전하게 관리하세요.

```env
# PostgreSQL 컨테이너 설정
POSTGRES_DB=s309
POSTGRES_USER=s309user
POSTGRES_PASSWORD=여기에_강력한_비밀번호

# Spring Boot 백엔드 DB 연결
# postgres는 docker-compose 내 서비스 이름 (컨테이너끼리 통신)
DB_URL=jdbc:postgresql://postgres:5432/s309
DB_USERNAME=s309user
DB_PASSWORD=여기에_강력한_비밀번호

# Spring 프로파일
SPRING_PROFILES_ACTIVE=prod

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

# AI 서버
AI_DEBUG=false
```

> `DB_URL`의 `postgres`, `REDIS_HOST`의 `redis`는 호스트명이 아니라 `docker-compose.yml`의 서비스명입니다.
> 컨테이너끼리는 서비스명으로 통신하므로 `localhost`가 아닙니다.

---

## 6단계 — Jenkins SSH Credentials 등록

Jenkins가 EC2에 SSH로 배포하려면 키를 등록해야 합니다.

1. Jenkins 관리 → Credentials → System → Global credentials → **Add Credentials**
2. Kind: `SSH Username with private key`
3. ID: `ec2-ssh-key` (Jenkinsfile에서 참조하는 이름)
4. Username: `ubuntu`
5. Private Key → Enter directly → `.pem` 파일 내용 전체 붙여넣기
6. Save

---

## 7단계 — Jenkins 파이프라인 생성

1. Jenkins 메인 → **New Item**
2. 이름 입력 → `Pipeline` 선택 → OK
3. Pipeline 설정:
   - Definition: `Pipeline script from SCM`
   - SCM: `Git`
   - Repository URL: GitLab 레포 주소
   - Credentials: GitLab 접근용 계정 추가
   - Branch: `*/develop`
   - Script Path: `infra/Jenkinsfile`
4. Save

### Jenkinsfile EC2 정보 수정

EC2 IP를 받으면 `infra/Jenkinsfile`의 한 줄만 수정합니다:

```groovy
EC2_HOST = 'ubuntu@<실제-EC2-IP>'  // 여기만 수정
```

수정 후 커밋하면 다음 빌드부터 적용됩니다.

### 파이프라인 동작 방식

```
GitLab push (develop 브랜치)
    → Jenkins 웹훅 수신
    → EC2에 SSH 접속
    → git pull origin develop
    → docker compose up -d --build
    → Health Check (curl /api/health)
```

> 빌드는 EC2에서 직접 수행합니다. Jenkins 서버에 Docker 이미지를 만들지 않으므로 Jenkins 서버 사양이 낮아도 됩니다.

---

## 8단계 — GitLab 웹훅 연결

코드가 push되면 Jenkins 파이프라인이 자동 실행되도록 연결합니다.

1. GitLab 레포 → Settings → Webhooks → **Add new webhook**
2. URL: `http://<EC2-IP>:8080/project/<Jenkins-job-이름>`
3. Trigger: `Push events`, `Merge request events` 체크
4. Add webhook → Test → 200 OK 확인

> Jenkins에서 GitLab 웹훅 허용 설정:
> Jenkins 관리 → Security → "Enable authentication for /project end-point" 체크 해제 (또는 GitLab 플러그인에서 토큰 설정)

---

## 9단계 — 첫 배포 확인

Jenkins 없이 EC2에서 직접 먼저 테스트합니다.

```bash
cd /home/ubuntu/S14P31S309/infra

# 컨테이너 빌드 및 실행
docker compose --env-file .env up -d --build

# 상태 확인
docker compose ps

# 로그 확인
docker compose logs postgres    # DB 정상 시작 여부
docker compose logs backend     # Spring 부팅 로그
docker compose logs ai          # Python 서버 로그
docker compose logs nginx       # Nginx 로그
```

### 동작 확인

```bash
# Nginx를 통해 백엔드 응답 확인
curl http://localhost/api/health

# PostgreSQL 접속 테스트
docker exec -it s309-postgres psql -U s309user -d s309db -c "\l"
```

---

## 컨테이너 구조

```
EC2 서버
└── Docker
    ├── s309-nginx (포트 80) ─── 외부 요청 분기
    │   ├── /api/** → s309-backend (8080)
    │   └── /ai/**  → s309-ai (8000)
    ├── s309-backend (Spring Boot, Java 21)
    │   ├── DB 연결: s309-postgres:5432
    │   └── 캐시 연결: s309-redis:6379
    ├── s309-ai (FastAPI, Python 3.12)
    │   └── 캐시 연결: s309-redis:6379
    ├── s309-postgres (PostgreSQL 17)
    │   └── 초기화: infra/postgres/init/01-init.sql
    └── s309-redis (Redis 7)
```

> `infra/mysql/` 폴더는 사용하지 않습니다. PostgreSQL로 전환됐으므로 무시하세요.

---

## DataGrip 연결 설정

DataGrip에서 EC2의 PostgreSQL과 Redis에 접속하는 방법입니다.
PostgreSQL/Redis 포트는 외부에 열려있지 않으므로 **SSH 터널**을 통해 접속합니다.

### PostgreSQL 연결 (EC2)

1. DataGrip → **+** → Data Source → **PostgreSQL**
2. **SSH/SSL 탭** 설정:
   - Use SSH tunnel: ✅ 체크
   - Proxy host: `<EC2-IP>`
   - Proxy port: `22`
   - Proxy user: `ubuntu`
   - Auth type: `Key pair (OpenSSH or PuTTY)`
   - Private key file: `.pem` 파일 경로 선택
3. **General 탭** 설정:
   - Host: `localhost`
   - Port: `5432`
   - Database: `s309`
   - User: `.env`의 `POSTGRES_USER` 값
   - Password: `.env`의 `POSTGRES_PASSWORD` 값
4. **Test Connection** → 성공 확인 후 OK

### PostgreSQL 연결 (로컬 개발)

SSH 터널 없이 직접 연결합니다.

1. DataGrip → **+** → Data Source → **PostgreSQL**
2. **General 탭** 설정:
   - Host: `localhost`
   - Port: `5432`
   - Database: `s309`
   - User / Password: `.env.example` 참고
3. Test Connection → OK

### Redis 연결 (EC2)

1. DataGrip → **+** → Data Source → **Redis**
2. **SSH/SSL 탭** 설정 (PostgreSQL과 동일하게):
   - Use SSH tunnel: ✅ 체크
   - Proxy host: `<EC2-IP>`, Port: `22`, User: `ubuntu`
   - Private key file: `.pem` 파일 경로
3. **General 탭** 설정:
   - Host: `localhost`
   - Port: `6379`
4. Test Connection → OK

### Redis 연결 (로컬 개발)

1. DataGrip → **+** → Data Source → **Redis**
2. Host: `localhost`, Port: `6379`
3. Test Connection → OK

> DataGrip에서 Redis를 사용하려면 **Database Tools and SQL** 플러그인이 활성화되어 있어야 합니다 (기본 활성화).

---

## 자주 쓰는 명령어

```bash
# 전체 재시작
docker compose down && docker compose up -d --build

# 특정 서비스만 재시작
docker compose restart backend

# 실시간 로그 보기
docker compose logs -f backend

# 컨테이너 내부 접속
docker exec -it s309-backend /bin/sh
docker exec -it s309-postgres psql -U s309user -d s309db
docker exec -it s309-redis redis-cli

# Redis 기본 명령어 (redis-cli 접속 후)
# KEYS *          → 전체 키 목록
# GET <key>       → 값 조회
# DEL <key>       → 키 삭제
# FLUSHALL        → 전체 삭제 (주의!)

# 이미지/볼륨 정리 (주의: DB 데이터 날아감)
docker compose down -v
docker system prune -a
```

---

## 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| backend가 계속 재시작 | postgres/redis 헬스체크 실패 | `docker compose logs postgres` 또는 `logs redis` 확인, `.env` 값 일치 여부 |
| 8080 접속 안 됨 | 보안 그룹 미설정 | AWS 콘솔에서 인바운드 규칙 확인 |
| `permission denied` (docker) | 그룹 미적용 | `sudo usermod -aG docker ubuntu` 후 재로그인 |
| Jenkins 빌드 중 `./gradlew: Permission denied` | 실행 권한 없음 | `chmod +x backend/gradlew` 후 커밋 |
| DB 연결 에러 (Spring) | `DB_URL` 오타 | `.env`에서 `postgres`가 서비스명인지 확인 |
| Redis 연결 에러 | `REDIS_HOST` 오타 | `.env`에서 `REDIS_HOST=redis` (서비스명)인지 확인 |
| DataGrip SSH 터널 실패 | 키 파일 형식 문제 | `.pem` 파일을 OpenSSH 형식으로 사용, 파일 경로에 한글/공백 없는지 확인 |

---

## 체크리스트

```
[ ] EC2 SSH 접속 성공
[ ] 보안 그룹 포트 오픈 (22, 80, 8080) — 5432/6379는 열지 않음
[ ] Docker 설치 및 권한 설정
[ ] Jenkins 설치 + 계정 생성 + 플러그인 설치
[ ] 프로젝트 git clone
[ ] infra/.env 파일 작성 (PostgreSQL + Redis 항목 포함)
[ ] Jenkins SSH Credentials 등록
[ ] Jenkins 파이프라인 생성
[ ] Jenkinsfile EC2_HOST 수정 후 커밋
[ ] GitLab 웹훅 연결
[ ] docker compose up 첫 배포 성공 (postgres, redis, backend, ai, nginx)
[ ] curl로 API 응답 확인
[ ] DataGrip SSH 터널로 PostgreSQL/Redis 접속 확인
```
