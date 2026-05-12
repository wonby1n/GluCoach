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
[nginx :443]  ← SSL 종단, 리버스 프록시, Blue/Green 트래픽 전환
     │
     ├── /api, /swagger-ui, /v3/api-docs  →  [backend-blue 또는 backend-green :8080]
     │                                              │
     │                                              └── http://ai-{color}:8000
     ├── /ai/                             →  [ai-blue 또는 ai-green :8000]
     ├── /n8n/                            →  [n8n :5678]
     └── /                               →  정적 랜딩페이지

[PostgreSQL :5432]  ← backend, n8n 공유 DB
[Redis :6379]       ← backend 세션/캐시

[Jenkins :9090]  ← GitLab Webhook (release 브랜치 MR 머지 시 트리거)
     │ SSH
     ▼
[EC2: infra/scripts/deploy-bg.sh]  ← Blue-Green 무중단 배포

[AWS S3]  ← 음식 사진 등 파일 저장
```

---

## 3. Docker 컨테이너 구성

### Compose 파일 구조

| 파일 | 역할 |
|---|---|
| `infra/docker-compose.infra.yml` | 공유 인프라 (nginx, postgres, redis, n8n) — 배포 시 재시작 없음 |
| `infra/docker-compose.blue.yml` | Blue 앱 서비스 (backend-blue, ai-blue) |
| `infra/docker-compose.green.yml` | Green 앱 서비스 (backend-green, ai-green) |

### 실행 중인 컨테이너

| 컨테이너 | 이미지 | 포트 | 역할 |
|---|---|---|---|
| `s309-nginx` | `nginx:alpine` | 80, 443 | 리버스 프록시, HTTPS 종단, 트래픽 전환 |
| `s309-backend-blue` / `s309-backend-green` | 자체 빌드 | 8080 (내부) | Spring Boot API 서버 |
| `s309-ai-blue` / `s309-ai-green` | 자체 빌드 | 8000 (내부) | FastAPI AI 서버 |
| `s309-postgres` | `postgres:17-alpine` | 5432 (localhost만) | 메인 DB |
| `s309-redis` | `redis:7-alpine` | 6379 (localhost만) | 캐시, 세션 |
| `s309-n8n` | `n8nio/n8n:latest` | 5678 | 워크플로우 자동화 |

> PostgreSQL, Redis는 외부에 노출되지 않으며 SSH 터널을 통해서만 접근 가능.

**Jenkins는 compose에 포함되지 않음** — 배포 중 자기 자신을 down시키는 문제로 별도 컨테이너로 운영.

```bash
# Jenkins 컨테이너 실행 명령 (참고)
docker run -d \
  -p 9090:8080 \
  -v jenkins_home:/var/jenkins_home \
  --name jenkins \
  --restart unless-stopped \
  jenkins/jenkins:lts
```

### 현재 활성 환경 확인

```bash
cat /home/ubuntu/S14P31S309/infra/.active-color   # "blue" 또는 "green"
docker ps --format "table {{.Names}}\t{{.Status}}"
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

### 배포 흐름

```
① 개발자가 develop 브랜치에 push
② develop → release MR 머지
③ GitLab Webhook → Jenkins 빌드 자동 트리거
④ Jenkins: backend Gradle 빌드 (bootJar, ~20초)
⑤ Jenkins: EC2 SSH 접속
   └── git pull (release 최신화)
   └── infra/scripts/deploy-bg.sh 실행
        ├── .active-color 읽기 (현재: blue → 다음: green)
        ├── backend-green, ai-green 빌드 & 시작
        │    ↕ 이 동안 blue가 계속 서비스 중 (다운타임 없음)
        ├── green 헬스체크 통과 대기 (최대 3분)
        ├── nginx upstream.conf 교체 → nginx -s reload (트래픽 전환)
        ├── .active-color → "green" 갱신
        └── 60초 후 blue 컨테이너 정리
⑥ Mattermost 채널에 성공/실패 알림
```

### Jenkins 설정 항목

Jenkins (`http://k14s309.p.ssafy.io:9090`) 에서 설정된 Credentials:

| ID | 종류 | 용도 |
|---|---|---|
| `gitlab-token` | Username with Password | GitLab HTTPS 체크아웃 |
| `ec2-ssh-key` | SSH Private Key | EC2 배포 SSH 접속 |
| `firebase-key` | Secret File | Firebase 서비스 계정 키 |
| `food-api-key` | Secret Text | 공공 식품 API 키 |

### 트리거 조건

- **대상 브랜치**: `release` 브랜치 MR 머지 시에만 빌드
- GitLab Webhook URL: `http://k14s309.p.ssafy.io:9090/project/glucoach`

### 파이프라인 단계

1. **Checkout** — GitLab에서 release 브랜치 소스 체크아웃
2. **Build Backend** — `./gradlew bootJar -x test` (테스트 제외, Jenkins에서 실행)
3. **Deploy to EC2** — EC2에서 `deploy-bg.sh` 실행 (Blue-Green 무중단 배포)

> AI 서버는 Jenkins에서 별도 빌드하지 않음. `deploy-bg.sh` 실행 시 EC2에서 `docker compose --build`로 자동 빌드.

### 롤백

배포 후 문제 발생 시 EC2에서 즉시 롤백 가능:

```bash
# 이전 색상으로 nginx upstream 교체 후 reload
PREV="blue"   # 또는 green
cp infra/nginx/upstream.${PREV}.conf infra/nginx/upstream.conf
docker exec s309-nginx nginx -s reload
echo "${PREV}" > infra/.active-color
```

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
| 업스트림 전환 | `nginx/upstream.conf` include 방식 — `nginx -s reload`로 무중단 전환 |

### Blue-Green upstream 구조

```
nginx.conf
  └── include /etc/nginx/upstream.conf   ← 배포 시 교체되는 파일

upstream.conf (현재 활성이 blue인 경우):
  upstream backend { server backend-blue:8080; }
  upstream ai      { server ai-blue:8000;      }
```

### 라우팅 규칙

| 경로 | 대상 |
|---|---|
| `/` | 랜딩 페이지 (`infra/nginx/html/index.html`) |
| `/api/*` | Backend (rate limit 적용) |
| `/swagger-ui`, `/v3/api-docs` | Backend |
| `/ai/*` | AI 서버 (rate limit 적용) |
| `/n8n/` | n8n 워크플로우 (WebSocket 지원) |
| `/health` | nginx 헬스체크 엔드포인트 |

---

## 7. AWS S3 파일 저장소

### 구성

| 항목 | 내용 |
|---|---|
| 버킷 이름 | `glucoach-images` |
| 리전 | `ap-northeast-2` (서울) |
| IAM 사용자 | `glucoach-s3` |
| 접근 방식 | 백엔드 경유 업로드 / Presigned URL |

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
JWT_SECRET=<JWT 서명 키>
ADMIN_API_KEY=<관리자 API 키>

# Redis
REDIS_PASSWORD=<Redis 비밀번호>

# AI
AI_DEBUG=false
OPENAI_API_KEY=<OpenAI API 키>
OPENAI_BASE_URL=https://gms.ssafy.io/gmsapi/api.openai.com/v1
ANTHROPIC_API_KEY=<Anthropic API 키>
AGENT_API_KEY=<AI 서버 내부 인증 키>

# AWS S3
AWS_S3_ACCESS_KEY=<IAM Access Key>
AWS_S3_SECRET_KEY=<IAM Secret Key>
AWS_S3_REGION=ap-northeast-2
AWS_S3_BUCKET=glucoach-images
AWS_S3_ENDPOINT=<S3 엔드포인트>

# 공공 식품 API (Jenkins Credentials에서 주입)
FOOD_API_KEY=<식품안전처 API 키>
```

> `.env` 파일은 Git에 포함되지 않음. 팀 내부 채널에서 공유.

---

## 9. 신규 서버 세팅 순서

새 서버에 처음 세팅하는 경우 아래 순서대로 진행.

### 사전 준비

```bash
# Docker 설치
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker ubuntu
newgrp docker

# 소스 클론
cd /home/ubuntu
git clone https://lab.ssafy.com/s14-final/S14P31S309.git S14P31S309
cd S14P31S309

# .env 파일 생성
cp infra/.env.example infra/.env
# infra/.env 파일에 실제 값 입력
```

### SSL 인증서 발급

```bash
sudo snap install --classic certbot
# 80 포트를 사용 중인 프로세스가 없어야 함
sudo certbot certonly --standalone -d k14s309.p.ssafy.io
sudo mkdir -p /home/ubuntu/ssl
sudo cp /etc/letsencrypt/live/k14s309.p.ssafy.io/fullchain.pem /home/ubuntu/ssl/
sudo cp /etc/letsencrypt/live/k14s309.p.ssafy.io/privkey.pem /home/ubuntu/ssl/
sudo chmod 644 /home/ubuntu/ssl/*.pem
```

### 컨테이너 초기 기동 (Blue-Green)

```bash
# 1. 공유 인프라 시작 (네트워크 s309-internal, 볼륨 생성)
docker compose -f infra/docker-compose.infra.yml --env-file infra/.env up -d

# 2. blue를 초기 활성으로 설정
echo "blue" > infra/.active-color
cp infra/nginx/upstream.blue.conf infra/nginx/upstream.conf

# 3. blue 앱 컨테이너 시작
docker compose \
  -f infra/docker-compose.infra.yml \
  -f infra/docker-compose.blue.yml \
  --env-file infra/.env \
  up -d --build

# 4. nginx upstream 반영
docker exec s309-nginx nginx -s reload

# 5. 상태 확인
docker ps --format "table {{.Names}}\t{{.Status}}"
```

### Jenkins 컨테이너 실행

```bash
docker run -d \
  -p 9090:8080 \
  -v jenkins_home:/var/jenkins_home \
  --name jenkins \
  --restart unless-stopped \
  jenkins/jenkins:lts
```

### Jenkins 초기 설정 (웹 UI)

1. `http://서버IP:9090` 접속
2. 초기 비밀번호 확인: `docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword`
3. 권장 플러그인 설치
4. 추가 플러그인 설치: **GitLab**, **SSH Agent**
5. Credentials 등록:

| ID | 종류 | 값 |
|---|---|---|
| `gitlab-token` | Username with Password | GitLab 계정 + 토큰 |
| `ec2-ssh-key` | SSH Private Key | EC2 `.pem` 키 내용 |
| `firebase-key` | Secret File | `firebase-service-account.json` |
| `food-api-key` | Secret Text | 공공 식품 API 키 |

6. 파이프라인 생성: Pipeline script from SCM → Git → `infra/Jenkinsfile` 경로 지정
7. GitLab Webhook 설정: `http://서버IP:9090/project/glucoach`
