# GluCoach 포팅 매뉴얼

## 목차

1. [서버 환경](#1-서버-환경)
2. [전체 아키텍처](#2-전체-아키텍처)
3. [Docker 컨테이너 구성](#3-docker-컨테이너-구성)
4. [데이터베이스 관리](#4-데이터베이스-관리)
5. [CI/CD 파이프라인](#5-cicd-파이프라인)
6. [nginx 설정](#6-nginx-설정)
7. [AWS S3 파일 저장소](#7-aws-s3-파일-저장소)
8. [환경변수 설정](#8-환경변수-설정)
9. [신규 서버 세팅 순서](#9-신규-서버-세팅-순서)
10. [남은 작업](#10-남은-작업)

---

## 1. 서버 환경

| 항목 | 내용 |
|---|---|
| 서버 | SSAFY 지급 AWS EC2 (Ubuntu 22.04 LTS) |
| 도메인 | `k14s309.p.ssafy.io` |
| Jenkins | `http://k14s309.p.ssafy.io:9090` |
| 허용 포트 | 22 (SSH), 80 (HTTP), 443 (HTTPS), 9090 (Jenkins) |
| GitLab | `https://lab.ssafy.com/s14-final/S14P31S309` |

---

## 2. 전체 아키텍처

```
[Android App]
     │
     │ HTTPS
     ▼
[nginx :443]  ─────────────────────────────────────────
     │                                                 │
     │ /api/*                    /ai/*                 │ /
     ▼                           ▼                     ▼
[backend :8080]          [ai :8000]           [정적 랜딩페이지]
     │                       │
     ▼                       ▼
[PostgreSQL :5432]      [Redis :6379]
     (Docker)               (Docker)

[Jenkins :9090]  ← GitLab Webhook (release 브랜치)
     │ SSH
     ▼
[EC2: docker compose up]

[AWS S3]  ← 음식 사진 등 파일 저장 (백엔드에서 연동)
```

---

## 3. Docker 컨테이너 구성

`infra/docker-compose.yml` 기준. EC2 서버에서 실행.

| 컨테이너 | 이미지 | 포트 | 역할 |
|---|---|---|---|
| `s309-nginx` | `nginx:alpine` | 80, 443 | 리버스 프록시, HTTPS 종단 |
| `s309-backend` | 자체 빌드 | 8080 (내부) | Spring Boot API 서버 |
| `s309-ai` | 자체 빌드 | 8000 (내부) | AI 추론 서버 (FastAPI) |
| `s309-postgres` | `postgres:17-alpine` | 5432 (localhost만) | 메인 DB |
| `s309-redis` | `redis:7-alpine` | 6379 (localhost만) | 캐시, 세션 |

> PostgreSQL, Redis는 외부에 노출되지 않으며 SSH 터널을 통해서만 접근 가능.

**Jenkins는 docker-compose에 포함되지 않음** — 자기 자신을 down시키는 문제로 별도 컨테이너로 운영.

```bash
# Jenkins 컨테이너 실행 명령 (참고)
docker run -d \
  -p 9090:8080 \
  -v jenkins_home:/var/jenkins_home \
  --name jenkins \
  --restart unless-stopped \
  jenkins/jenkins:lts
```

---

## 4. 데이터베이스 관리

### PostgreSQL

- **버전**: 17 (Docker)
- **데이터 영속화**: Docker Volume `postgres-data`
- **스키마 관리**: Flyway 마이그레이션 (`backend/src/main/resources/db/migration/`)
  - `V1__init_schema.sql` — 초기 스키마
  - `V2__add_guardian_priority.sql` — 보호자 우선순위 추가
- **특이사항**: `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` 설정 (기존 DB에 Flyway 적용 시 필요)

### Redis

- **버전**: 7 (Docker)
- **데이터 영속화**: Docker Volume `redis-data`
- **인증**: 비밀번호 필수 (`REDIS_PASSWORD`)
- **용도**: 세션 저장, API 캐시

### DataGrip으로 접속하는 법

SSH 터널 방식으로만 접근 가능. 자세한 설정은 `docs/DATAGRIP_GUIDE.md` 참고.

| | PostgreSQL | Redis |
|---|---|---|
| SSH Host | `k14s309.p.ssafy.io:22` | 동일 |
| SSH User | `ubuntu` | 동일 |
| SSH 인증 | EC2 `.pem` 키 파일 | 동일 |
| DB Host | `localhost` | `localhost` |
| DB Port | `5432` | `6379` |

---

## 5. CI/CD 파이프라인

### 흐름

```
개발자 → release 브랜치 push/merge
    → GitLab Webhook → Jenkins 빌드 트리거
    → Jenkins: 코드 체크아웃 + Gradle 빌드
    → Jenkins: EC2 SSH 접속 → git pull → docker compose 재시작
```

### Jenkins 설정 항목

Jenkins (`http://k14s309.p.ssafy.io:9090`) 에서 설정된 Credentials:

| ID | 종류 | 용도 |
|---|---|---|
| `gitlab-token` | Username with Password | GitLab HTTPS 체크아웃 |
| `ec2-ssh-key` | SSH Private Key | EC2 배포 SSH 접속 |

### 트리거 조건

- **대상 브랜치**: `release` 브랜치에 push 또는 merge 시에만 빌드
- GitLab Webhook URL: `http://k14s309.p.ssafy.io:9090/project/glucoach`
- Secret token: Jenkins 파이프라인 설정에서 확인

### 파이프라인 단계

1. **Checkout** — GitLab에서 release 브랜치 소스 체크아웃
2. **Build Backend** — `./gradlew bootJar -x test` (테스트 제외 빌드)
3. **Deploy to EC2** — EC2에 SSH 접속 후 `git pull` + `docker compose down && up`

> AI 서버는 Jenkins에서 별도 빌드하지 않음. EC2에서 `docker compose up --build` 시 자동 빌드.

---

## 6. nginx 설정

파일 위치: `infra/nginx/nginx.conf`

### 주요 설정

| 설정 | 내용 |
|---|---|
| HTTP → HTTPS | 80 포트 요청을 443으로 301 리다이렉트 |
| SSL 인증서 | Let's Encrypt (`/etc/nginx/ssl/fullchain.pem`) |
| SSL 자동 갱신 | systemd timer (`certbot.timer`) — EC2에서 운영 중 |
| Rate Limiting | IP당 100req/min, 초과 시 429 응답 |

### 보안 헤더

- `Strict-Transport-Security` (HSTS)
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `X-XSS-Protection`
- `Referrer-Policy`

### 라우팅 규칙

| 경로 | 대상 |
|---|---|
| `/` | 랜딩 페이지 (`infra/nginx/html/index.html`) |
| `/api/*` | Backend (`:8080`) |
| `/ai/*` | AI 서버 (`:8000`) |

---

## 7. AWS S3 파일 저장소

### 구성

| 항목 | 내용 |
|---|---|
| 버킷 이름 | `glucoach-images` |
| 리전 | `ap-northeast-2` (서울) |
| IAM 사용자 | `glucoach-s3` |
| 접근 방식 | Presigned URL (앱에서 직접 업로드) 또는 백엔드 경유 |

### IAM 정책 (최소 권한)

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
    "Resource": "arn:aws:s3:::glucoach-images/*"
  }]
}
```

### 백엔드 연동 현황

- `S3Config.java` — AWS SDK v2 클라이언트 설정 ✅
- `S3Service.java` — 업로드/다운로드/Presigned URL 로직 ✅
- **API 엔드포인트(Controller)** — 미구현 ❌ (백엔드 팀 작업 필요)

---

## 8. 환경변수 설정

EC2 서버 `/home/ubuntu/S14P31S309/infra/.env` 파일에 설정. (`.env.example` 참고)

```env
# PostgreSQL
POSTGRES_DB=s309
POSTGRES_USER=<DB 사용자명>
POSTGRES_PASSWORD=<DB 비밀번호>

# Backend
DB_URL=jdbc:postgresql://postgres:5432/s309
DB_USERNAME=<DB 사용자명>
DB_PASSWORD=<DB 비밀번호>
SPRING_PROFILES_ACTIVE=prod

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=<Redis 비밀번호>

# AI
AI_DEBUG=false

# AWS S3
AWS_S3_ACCESS_KEY=<IAM Access Key>
AWS_S3_SECRET_KEY=<IAM Secret Key>
AWS_S3_REGION=ap-northeast-2
AWS_S3_BUCKET=glucoach-images
```

> `.env` 파일은 Git에 포함되지 않음. 팀 내부 채널에서 공유.

---

## 9. 신규 서버 세팅 순서

새 서버에 처음 세팅하는 경우 아래 순서대로 진행.

```bash
# 1. Docker 설치
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker ubuntu

# 2. 소스 클론
cd /home/ubuntu
git clone https://lab.ssafy.com/s14-final/S14P31S309.git

# 3. .env 파일 생성
cd S14P31S309/infra
cp .env.example .env
# .env 파일에 실제 값 입력

# 4. SSL 인증서 발급 (certbot)
sudo snap install --classic certbot
sudo certbot certonly --standalone -d k14s309.p.ssafy.io
sudo cp /etc/letsencrypt/live/k14s309.p.ssafy.io/fullchain.pem infra/nginx/ssl/
sudo cp /etc/letsencrypt/live/k14s309.p.ssafy.io/privkey.pem infra/nginx/ssl/

# 5. 컨테이너 실행
docker compose -f infra/docker-compose.yml up -d --build

# 6. Jenkins 실행
docker run -d \
  -p 9090:8080 \
  -v jenkins_home:/var/jenkins_home \
  --name jenkins \
  --restart unless-stopped \
  jenkins/jenkins:lts
```

**Jenkins 초기 설정 (웹 UI)**
1. `http://서버IP:9090` 접속
2. 초기 비밀번호: `docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword`
3. 권장 플러그인 설치
4. GitLab, SSH Agent 플러그인 추가 설치
5. Credentials 등록: `gitlab-token`, `ec2-ssh-key`
6. 파이프라인 생성: SCM에서 `infra/Jenkinsfile` 경로 지정
7. Jenkins URL 설정: `http://서버IP:9090/`

---

## 10. 남은 작업

### 인프라

| 항목 | 상태 | 내용 |
|---|---|---|
| S3 업로드 API (Controller) | ❌ | 백엔드 팀에서 도메인별 파일 업로드 엔드포인트 구현 필요 |
| Mattermost 배포 알림 | ❌ | Jenkins post 블록에 Mattermost Webhook 연동 필요 |
| GitLab Webhook 인증 | 확인 필요 | Jenkins Secret Token 설정 후 403 오류 해결 확인 |

### 백엔드

| 항목 | 상태 | 내용 |
|---|---|---|
| S3Service 활용 Controller | ❌ | 음식 사진 등 파일 업로드 API 구현 |
| Redis 활용 캐싱 | 미확인 | Spring Data Redis 실제 사용 여부 확인 |

### 앱

| 항목 | 상태 | 내용 |
|---|---|---|
| API BASE_URL 설정 | 확인 필요 | `https://k14s309.p.ssafy.io` 로 설정되어 있는지 확인 |
