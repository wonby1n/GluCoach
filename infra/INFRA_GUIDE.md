# GluCoach 인프라 가이드

## 목차

1. [서버 환경 개요](#1-서버-환경-개요)
2. [전체 아키텍처](#2-전체-아키텍처)
3. [사전 준비](#3-사전-준비)
4. [EC2 초기 서버 세팅](#4-ec2-초기-서버-세팅)
5. [SSL 인증서 발급](#5-ssl-인증서-발급)
6. [환경변수 설정 (.env)](#6-환경변수-설정-env)
7. [Docker Compose 실행](#7-docker-compose-실행)
8. [Jenkins CI/CD 설정](#8-jenkins-cicd-설정)
9. [GitLab Webhook 설정](#9-gitlab-webhook-설정)
10. [DB 접근 (DataGrip)](#10-db-접근-datagrip)
11. [AWS S3 설정](#11-aws-s3-설정)
12. [현재 인프라 상태 체크리스트](#12-현재-인프라-상태-체크리스트)
13. [자주 쓰는 명령어](#13-자주-쓰는-명령어)
14. [트러블슈팅](#14-트러블슈팅)

---

## 1. 서버 환경 개요

| 항목 | 내용 |
|---|---|
| 서버 | SSAFY 지급 AWS EC2 (Ubuntu 22.04 LTS) |
| 도메인 | `k14s309.p.ssafy.io` |
| 서비스 URL | `https://k14s309.p.ssafy.io` |
| Jenkins | `http://k14s309.p.ssafy.io:9090` |
| EC2 접속 | `ssh -i <pem파일> ubuntu@k14s309.p.ssafy.io` |
| GitLab 저장소 | `https://lab.ssafy.com/s14-final/S14P31S309` |
| EC2 소스 경로 | `/home/ubuntu/S14P31S309` |

**허용 포트**

| 포트 | 용도 |
|---|---|
| 22 | SSH |
| 80 | HTTP (→ HTTPS 리다이렉트) |
| 443 | HTTPS |
| 9090 | Jenkins |

---

## 2. 전체 아키텍처

```
[Android App]
     │
     │ HTTPS (443)
     ▼
┌─────────────────────────────────────────────┐
│  nginx (Docker)                             │
│  - /api/* → backend:8080                   │
│  - /ai/*  → ai:8000                        │
│  - /      → 랜딩페이지 (HTML)              │
│  - Rate Limit: IP당 100req/min              │
│  - 보안 헤더 (HSTS, X-Frame-Options 등)    │
└───────────────────────┬─────────────────────┘
                        │ Docker internal network
          ┌─────────────┴─────────────┐
          ▼                           ▼
   [backend :8080]            [ai :8000]
   (Spring Boot)              (FastAPI)
          │                       │
          ▼                       │
   [postgres :5432]         [redis :6379]
   (Docker Volume)          (Docker Volume)
          ▲
          └─── Flyway 마이그레이션

[Jenkins :9090] ← GitLab Webhook (release 브랜치)
     │ SSH
     ▼
  EC2: git pull + docker compose up

[AWS S3] ← 파일 저장 (음식 사진 등)
  버킷: glucoach-images (ap-northeast-2)
```

---

## 3. 사전 준비

아래 항목을 미리 준비해야 합니다.

- [ ] EC2 SSH 접속용 `.pem` 키 파일
- [ ] GitLab Personal Access Token (read_repository, write_repository 권한)
- [ ] AWS IAM Access Key / Secret Key (S3용)
- [ ] Mattermost Incoming Webhook URL
- [ ] DB 계정 정보 (POSTGRES_USER, POSTGRES_PASSWORD, REDIS_PASSWORD)

---

## 4. EC2 초기 서버 세팅

> **이미 세팅된 서버에는 불필요.** 새 서버 구축 시에만 진행.

### 4-1. Docker 설치

```bash
# Docker 설치
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker ubuntu
# 재로그인 후 docker 명령어 sudo 없이 사용 가능
```

### 4-2. 소스코드 클론

```bash
cd /home/ubuntu
git clone https://lab.ssafy.com/s14-final/S14P31S309.git

# HTTPS 자격증명 저장 (git pull 시 매번 입력 방지)
cd S14P31S309
git config credential.helper store
git pull origin release
# → 최초 1회 GitLab 아이디 + Personal Access Token 입력
```

> **주의**: lab.ssafy.com은 SSH(포트 22)가 차단되어 있어 반드시 HTTPS + PAT 방식 사용.

---

## 5. SSL 인증서 발급

### 5-1. certbot 설치 및 발급

```bash
sudo snap install --classic certbot
sudo ln -s /snap/bin/certbot /usr/bin/certbot

# nginx 컨테이너가 80포트를 사용 중이면 먼저 내리기
docker stop s309-nginx

# 인증서 발급 (standalone 방식)
sudo certbot certonly --standalone -d k14s309.p.ssafy.io
```

### 5-2. 인증서를 nginx ssl 디렉토리에 복사

```bash
cd /home/ubuntu/S14P31S309
sudo cp /etc/letsencrypt/live/k14s309.p.ssafy.io/fullchain.pem infra/nginx/ssl/
sudo cp /etc/letsencrypt/live/k14s309.p.ssafy.io/privkey.pem infra/nginx/ssl/
sudo chown ubuntu:ubuntu infra/nginx/ssl/*.pem
```

### 5-3. 자동 갱신 확인

```bash
# 자동 갱신 타이머 확인 (이미 설정되어 있음)
systemctl list-timers | grep certbot
```

> 인증서 만료 시 `sudo certbot renew` 후 위 5-2 복사 과정 반복.

---

## 6. 환경변수 설정 (.env)

```bash
cd /home/ubuntu/S14P31S309/infra
cp .env.example .env
nano .env  # 또는 vi .env
```

**.env 파일 내용** (실제 값으로 채우기):

```env
# PostgreSQL
POSTGRES_DB=s309
POSTGRES_USER=<DB 사용자명>
POSTGRES_PASSWORD=<DB 비밀번호>

# Backend
DB_URL=jdbc:postgresql://postgres:5432/s309
DB_USERNAME=<POSTGRES_USER와 동일>
DB_PASSWORD=<POSTGRES_PASSWORD와 동일>
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

> `.env` 파일은 Git에 포함되지 않습니다. 팀 내부 채널에서 공유하세요.

---

## 7. Docker Compose 실행

```bash
cd /home/ubuntu/S14P31S309

# 최초 실행 또는 재배포
docker compose -f infra/docker-compose.yml up -d --build

# 컨테이너 상태 확인
docker compose -f infra/docker-compose.yml ps

# 로그 확인
docker compose -f infra/docker-compose.yml logs -f backend
docker compose -f infra/docker-compose.yml logs -f nginx
```

**정상 실행 시 컨테이너 목록:**

| 컨테이너 | 상태 |
|---|---|
| s309-nginx | Up |
| s309-backend | Up |
| s309-ai | Up |
| s309-postgres | Up (healthy) |
| s309-redis | Up (healthy) |

---

## 8. Jenkins CI/CD 설정

### 8-1. Jenkins 컨테이너 실행

> **이미 실행 중인 경우 불필요.**

```bash
docker run -d \
  -p 9090:8080 \
  -v jenkins_home:/var/jenkins_home \
  --name jenkins \
  --restart unless-stopped \
  jenkins/jenkins:lts
```

### 8-2. Jenkins 초기 설정

1. `http://k14s309.p.ssafy.io:9090` 접속
2. 초기 비밀번호 확인:
   ```bash
   docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
   ```
3. 권장 플러그인 설치
4. 추가 플러그인 설치: **GitLab**, **SSH Agent**

### 8-3. Credentials 등록

**Jenkins → Manage Jenkins → Credentials → System → Global credentials**

| ID | Kind | 내용 |
|---|---|---|
| `gitlab-token` | Username with Password | GitLab 아이디 + Personal Access Token |
| `ec2-ssh-key` | SSH Username with private key | ubuntu / EC2 pem 키 내용 |

### 8-4. 파이프라인 생성

1. Jenkins → New Item → Pipeline → 이름: `glucoach`
2. Build Triggers → **Build when a change is pushed to GitLab** 체크
3. Branch: **Allow all branches** 선택 (Jenkinsfile에서 release 필터링)
4. Advanced → **Secret token** → Generate → 토큰 복사 (GitLab Webhook에 사용)
5. Pipeline → Definition: **Pipeline script from SCM**
   - SCM: Git
   - Repository URL: `https://lab.ssafy.com/s14-final/S14P31S309.git`
   - Credentials: `gitlab-token`
   - Branch: `*/release`
   - Script Path: `infra/Jenkinsfile`
6. Save

### 8-5. Jenkins URL 설정

**Jenkins → Manage Jenkins → System → Jenkins URL**

```
http://k14s309.p.ssafy.io:9090/
```

### 8-6. 타임존 설정

**Jenkins → Manage Jenkins → Script Console**

```groovy
System.setProperty('org.apache.commons.jelly.tags.fmt.timeZone', 'Asia/Seoul')
```

---

## 9. GitLab Webhook 설정

1. GitLab 저장소 → **Settings → Webhooks → Add new webhook**
2. 설정:

   | 항목 | 값 |
   |---|---|
   | URL | `http://k14s309.p.ssafy.io:9090/project/glucoach` |
   | Secret token | Jenkins에서 생성한 토큰 (8-4 참고) |
   | Trigger | **Push events** 체크, Branch: `release` |
   | SSL verification | 비활성화 (Jenkins가 HTTP이므로) |

3. **Add webhook** → **Test → Push events** 로 테스트

> **배포 트리거 조건**: `release` 브랜치에 push 또는 merge 시에만 빌드.

---

## 10. DB 접근 (DataGrip)

DB 포트는 외부에 직접 노출되지 않으며 **SSH 터널**을 통해서만 접근합니다.

자세한 설정은 `docs/DATAGRIP_GUIDE.md` 참고.

**요약:**

| | PostgreSQL | Redis |
|---|---|---|
| SSH Host | `k14s309.p.ssafy.io:22` | 동일 |
| SSH User | `ubuntu` | 동일 |
| SSH 인증 | `.pem` 파일 | 동일 |
| DB Host | `localhost` | `localhost` |
| DB Port | `5432` | `6379` |
| DB 이름 | `s309` | - |
| 계정 | `.env`의 DB 계정 | `.env`의 REDIS_PASSWORD |

---

## 11. AWS S3 설정

| 항목 | 값 |
|---|---|
| 버킷 이름 | `glucoach-images` |
| 리전 | `ap-northeast-2` (서울) |
| IAM 사용자 | `glucoach-s3` |

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

### 백엔드 연동 코드 위치

| 파일 | 역할 |
|---|---|
| `backend/src/.../config/S3Config.java` | AWS SDK 클라이언트 설정 |
| `backend/src/.../common/service/S3Service.java` | 업로드/다운로드/Presigned URL |

---

## 12. 현재 인프라 상태 체크리스트

### 완료된 항목 ✅

- [x] Docker Compose 5개 컨테이너 구성
- [x] HTTPS + SSL 인증서 (Let's Encrypt)
- [x] SSL 인증서 자동 갱신 (systemd timer)
- [x] nginx HTTP → HTTPS 리다이렉트
- [x] nginx Rate Limiting (IP당 100req/min)
- [x] nginx 보안 헤더 (HSTS, X-Frame-Options 등)
- [x] nginx 라우팅 (/api → backend, /ai → ai)
- [x] 랜딩 페이지 (nginx/html/index.html)
- [x] PostgreSQL 17 + Flyway 마이그레이션
- [x] Redis 7 + 비밀번호 인증
- [x] DB 포트 localhost 바인딩 (외부 미노출)
- [x] DataGrip SSH 터널 접속
- [x] Jenkins CI/CD (release 브랜치 자동 배포)
- [x] GitLab Webhook 연동
- [x] Mattermost 배포 알림 (성공/실패)
- [x] AWS S3 버킷 생성 (`glucoach-images`)
- [x] S3 IAM 최소 권한 정책
- [x] 백엔드 S3 연동 코드 (Config, Service)

### 남은 항목 ❌

- [ ] **S3 파일 업로드 API** — 백엔드 팀에서 도메인별 Controller 구현 필요
- [ ] **앱 BASE_URL 확인** — `https://k14s309.p.ssafy.io` 로 설정되어 있는지 확인

---

## 13. 자주 쓰는 명령어

```bash
# 전체 컨테이너 재시작
docker compose -f infra/docker-compose.yml down && docker compose -f infra/docker-compose.yml up -d --build

# 특정 컨테이너만 재시작
docker restart s309-backend

# 컨테이너 상태 확인
docker compose -f infra/docker-compose.yml ps

# 로그 실시간 확인
docker compose -f infra/docker-compose.yml logs -f [backend|ai|nginx|postgres|redis]

# 전체 로그 (최근 100줄)
docker compose -f infra/docker-compose.yml logs --tail=100

# DB 직접 접속 (EC2 내부)
docker exec -it s309-postgres psql -U <POSTGRES_USER> -d s309

# Redis 직접 접속 (EC2 내부)
docker exec -it s309-redis redis-cli -a <REDIS_PASSWORD>

# nginx 설정 재로드 (재시작 없이)
docker exec s309-nginx nginx -s reload

# 인증서 수동 갱신
sudo certbot renew
sudo cp /etc/letsencrypt/live/k14s309.p.ssafy.io/fullchain.pem /home/ubuntu/S14P31S309/infra/nginx/ssl/
sudo cp /etc/letsencrypt/live/k14s309.p.ssafy.io/privkey.pem /home/ubuntu/S14P31S309/infra/nginx/ssl/
docker restart s309-nginx
```

---

## 14. 트러블슈팅

### 배포 후 컨테이너가 뜨지 않는 경우

```bash
# 에러 로그 확인
docker compose -f infra/docker-compose.yml logs backend
```

**자주 발생하는 오류:**

| 오류 | 원인 | 해결 |
|---|---|---|
| `Flyway: Found non-empty schema` | 기존 DB에 Flyway 미적용 | `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` 확인 |
| `Connection refused (postgres)` | postgres 헬스체크 전 백엔드 기동 | docker-compose depends_on 확인 |
| `invalid interpolation format` | docker-compose 환경변수 문법 오류 | `${VAR:-default}` 형식 사용 |
| `Port 80 already in use` | 기존 컨테이너가 포트 점유 | `docker ps`로 확인 후 `docker stop` |

### nginx가 뜨지 않는 경우

```bash
# SSL 인증서 파일 확인
ls infra/nginx/ssl/
# fullchain.pem, privkey.pem 있어야 함

# nginx 설정 문법 확인
docker run --rm -v $(pwd)/infra/nginx/nginx.conf:/etc/nginx/nginx.conf nginx:alpine nginx -t
```

### git pull 시 인증 오류

```bash
# EC2에서 자격증명 다시 저장
git config credential.helper store
git pull origin release
# GitLab 아이디 + Personal Access Token 입력
```

### Jenkins 403 오류 (Webhook)

GitLab Webhook에 Secret token이 설정되어 있는지 확인.
Jenkins 파이프라인 → Configure → Build Triggers → Advanced → Secret token
