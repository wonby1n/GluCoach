# S309 인프라 배포 가이드

EC2 서버 배포 환경 기준 단계별 가이드입니다.

- **서버 도메인**: `k14s309.p.ssafy.io`
- **접속 계정**: `ubuntu`
- **인증 파일**: `K14S309T.pem` (팀 비공개 채널에서 수령, 절대 외부 공유 금지)

---

## 전체 흐름

```
EC2 SSH 접속
    → 기본 패키지 설치 (Docker)
    → UFW 방화벽 설정 (필수)
    → Java 21 + Jenkins 설치 (포트 변경)
    → 프로젝트 clone
    → .env 파일 작성
    → Jenkins 파이프라인 설정
    → GitLab 웹훅 연결
    → HTTPS 인증서 발급
    → 첫 배포 확인
```

---

## 1단계 — EC2 SSH 접속

```fish
# pem 파일을 ~/.ssh/ 로 이동 (최초 1회)
mv ~/Downloads/K14S309T.pem ~/.ssh/K14S309T.pem

# 키파일 권한 설정 (최초 1회, Windows WSL/fish)
icacls $env:USERPROFILE\.ssh\K14S309T.pem /inheritance:r /grant:r (whoami)":R"

# EC2 접속
ssh -i ~/.ssh/K14S309T.pem ubuntu@k14s309.p.ssafy.io
```

---

## 2단계 — 기본 패키지 설치

```bash
sudo apt update && sudo apt upgrade -y

# Docker 설치
sudo apt install -y docker.io docker-compose-plugin

sudo systemctl enable --now docker

# ubuntu 사용자를 docker 그룹에 추가
sudo usermod -aG docker ubuntu

# Java 21 설치 (Jenkins용)
sudo apt install -y openjdk-21-jdk

# 적용을 위해 재로그인
exit
ssh -i K14S309T.pem ubuntu@k14s309.p.ssafy.io

# 확인
docker --version && docker compose version && java -version
```

정상 출력 예시:

```
Docker version 28.1.1, build 4eba377
Docker Compose version v2.35.1
openjdk version "21.0.x" 2024-xx-xx
```

---

## 3단계 — UFW 방화벽 포트 추가

> SSAFY EC2는 UFW가 이미 활성화된 상태로 제공됩니다.
> 기본 개방 포트: 22(SSH), 443(HTTPS)
> `sudo ufw enable`을 다시 실행하지 마세요 — 실수로 22번이 닫히면 접속 불가가 됩니다.

```bash
# 현재 UFW 상태 먼저 확인
sudo ufw status verbose

# HTTP (Nginx)
sudo ufw allow 80

# Jenkins 웹 UI (기본 8080 → 9090으로 변경, SSAFY 기본 포트 변경 요구사항)
sudo ufw allow 9090

# 상태 재확인
sudo ufw status verbose
```

> **주의**: SSH 22번은 이미 열려 있으므로 건드리지 마세요.
> DB(5432), Redis(6379), Backend(8080), AI(8000)는 Docker 내부 네트워크만 사용 → UFW에 추가 불필요

---

## 4단계 — Jenkins 설치 (포트 9090으로 변경)

### 4-1. Jenkins 설치

```bash
curl -fsSL https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key | sudo tee \
  /usr/share/keyrings/jenkins-keyring.asc > /dev/null

echo deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] \
  https://pkg.jenkins.io/debian-stable binary/ | sudo tee \
  /etc/apt/sources.list.d/jenkins.list > /dev/null

sudo apt update && sudo apt install -y jenkins

sudo systemctl enable --now jenkins
```

### 4-2. Jenkins 포트 8080 → 9090 변경

```bash
sudo nano /usr/lib/systemd/system/jenkins.service
```

아래 줄을 찾아 수정:

```
# 수정 전
Environment="JENKINS_PORT=8080"

# 수정 후
Environment="JENKINS_PORT=9090"
```

```bash
sudo systemctl daemon-reload
sudo systemctl restart jenkins

# 확인
sudo systemctl status jenkins
```

### 4-3. 초기 설정 (브라우저)

1. `http://k14s309.p.ssafy.io:9090` 접속
2. 초기 비밀번호 입력:
   ```bash
   sudo cat /var/lib/jenkins/secrets/initialAdminPassword
   ```
3. **Install suggested plugins** 선택
4. admin 계정 생성

### 4-4. 추가 플러그인 설치

Jenkins 관리 → Plugins → Available plugins:

- `GitLab` — GitLab 웹훅 트리거
- `SSH Agent` — SSH 키를 사용한 원격 배포
- `Docker Pipeline` — 파이프라인에서 Docker 사용

---

## 5단계 — HTTPS 인증서 발급 (Let's Encrypt)

> 443 포트를 사용하려면 SSL 인증서가 필요합니다.

```bash
# Certbot 설치
sudo apt install -y certbot

# 인증서 발급 (Nginx 중단 없이 standalone 방식)
# docker compose가 실행 중이라면 먼저 nginx 중단
sudo certbot certonly --standalone -d k14s309.p.ssafy.io

# 인증서 위치
# /etc/letsencrypt/live/k14s309.p.ssafy.io/fullchain.pem
# /etc/letsencrypt/live/k14s309.p.ssafy.io/privkey.pem
```

발급 후 `infra/nginx/ssl/` 디렉토리에 복사:

```bash
sudo mkdir -p /home/ubuntu/S14P31S309/infra/nginx/ssl
sudo cp /etc/letsencrypt/live/k14s309.p.ssafy.io/fullchain.pem /home/ubuntu/S14P31S309/infra/nginx/ssl/
sudo cp /etc/letsencrypt/live/k14s309.p.ssafy.io/privkey.pem /home/ubuntu/S14P31S309/infra/nginx/ssl/
```

### nginx.conf HTTPS 설정 추가

인증서 발급 후 `infra/nginx/nginx.conf`를 아래와 같이 교체:

```nginx
events {
    worker_connections 1024;
}

http {
    upstream backend {
        server backend:8080;
    }

    upstream ai {
        server ai:8000;
    }

    # HTTP → HTTPS 리다이렉트
    server {
        listen 80;
        server_name k14s309.p.ssafy.io;
        return 301 https://$host$request_uri;
    }

    server {
        listen 443 ssl;
        server_name k14s309.p.ssafy.io;

        ssl_certificate /etc/nginx/ssl/fullchain.pem;
        ssl_certificate_key /etc/nginx/ssl/privkey.pem;

        location /api {
            proxy_pass http://backend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        location /ai {
            proxy_pass http://ai;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
```

---

## 6단계 — 프로젝트 clone

```bash
cd /home/ubuntu

# EC2 SSH 키 생성 (GitLab 등록용)
ssh-keygen -t ed25519 -C "k14s309-ec2"
cat ~/.ssh/id_ed25519.pub
# → 출력된 공개키를 GitLab 프로필 → SSH Keys에 등록

# clone
git clone git@lab.ssafy.com:s14-final/S14P31S309.git
cd S14P31S309
```

---

## 7단계 — .env 파일 작성

```bash
nano /home/ubuntu/S14P31S309/infra/.env
```

```env
# PostgreSQL
POSTGRES_DB=s309
POSTGRES_USER=s309user
POSTGRES_PASSWORD=여기에_강력한_비밀번호_작성

# Spring Boot
DB_URL=jdbc:postgresql://postgres:5432/s309
DB_USERNAME=s309user
DB_PASSWORD=여기에_강력한_비밀번호_작성
SPRING_PROFILES_ACTIVE=prod

# Redis
REDIS_PASSWORD=여기에_강력한_비밀번호_작성

# AI
AI_DEBUG=false
```

> `postgres`, `redis`는 Docker 서비스명입니다. `localhost` 아님에 주의.

---

## 8단계 — Jenkins Credentials 등록

### SSH Credentials (EC2 배포용)

1. Jenkins 관리 → Credentials → Global → **Add Credentials**
2. Kind: `SSH Username with private key`
3. ID: `ec2-ssh-key`
4. Username: `ubuntu`
5. Private Key → Enter directly → `K14S309T.pem` 내용 전체 붙여넣기
6. Save

---

## 9단계 — Jenkins 파이프라인 생성

1. Jenkins → **New Item** → 이름 입력 → `Pipeline` → OK
2. Pipeline 설정:
   - Definition: `Pipeline script from SCM`
   - SCM: `Git`
   - Repository URL: GitLab 레포 주소
   - Branch: `*/develop`
   - Script Path: `infra/Jenkinsfile`
3. Save

### Jenkinsfile 도메인 수정

`infra/Jenkinsfile`에서 EC2 호스트 설정:

```groovy
EC2_HOST = 'ubuntu@k14s309.p.ssafy.io'
```

---

## 10단계 — GitLab 웹훅 연결

1. GitLab 레포 → Settings → Webhooks
2. URL: `http://k14s309.p.ssafy.io:9090/project/<Jenkins-job-이름>`
3. Trigger: `Push events`, `Merge request events` 체크
4. Add webhook → Test → 200 OK 확인

---

## 11단계 — 첫 배포 확인

```bash
cd /home/ubuntu/S14P31S309/infra

docker compose --env-file .env up -d --build

# 상태 확인
docker compose ps

# 로그 확인
docker compose logs postgres
docker compose logs backend
docker compose logs ai
docker compose logs nginx

# API 응답 확인
curl http://localhost/api/health
curl https://k14s309.p.ssafy.io/api/health
```

---

## 컨테이너 구조

```
k14s309.p.ssafy.io (EC2)
└── Docker (internal network: s309_internal)
    ├── s309-nginx     → 80, 443 (외부 노출)
    │   ├── /api/**    → s309-backend:8080
    │   └── /ai/**     → s309-ai:8000
    ├── s309-backend   → 내부 8080 (외부 비노출)
    ├── s309-ai        → 내부 8000 (외부 비노출)
    ├── s309-postgres  → 내부 5432 (외부 비노출)
    └── s309-redis     → 내부 6379 (외부 비노출)
```

> DB/Redis는 외부에 포트가 열려있지 않습니다.
> DataGrip 접속은 SSH 터널을 사용하세요 (아래 참고).

---

## DataGrip SSH 터널 연결

### PostgreSQL

1. DataGrip → + → PostgreSQL
2. **SSH/SSL 탭**:
   - Use SSH tunnel: ✅
   - Proxy host: `k14s309.p.ssafy.io`, Port: `22`, User: `ubuntu`
   - Auth type: Key pair → `K14S309T.pem` 선택
3. **General 탭**:
   - Host: `localhost`, Port: `5432`
   - Database: `s309`
   - User/Password: `.env` 값
4. Test Connection → OK

### Redis

1. DataGrip → + → Redis
2. **SSH/SSL 탭**: PostgreSQL과 동일 설정
3. **General 탭**: Host: `localhost`, Port: `6379`
4. Test Connection → OK

---

## 자주 쓰는 명령어

```bash
# 전체 재시작
docker compose down && docker compose --env-file .env up -d --build

# 특정 서비스만 재빌드
docker compose up -d --build backend

# 실시간 로그
docker compose logs -f backend

# 컨테이너 내부 접속
docker exec -it s309-postgres psql -U s309user -d s309
docker exec -it s309-redis redis-cli -a <REDIS_PASSWORD>

# Jenkins 재시작
sudo systemctl restart jenkins

# UFW 상태 확인
sudo ufw status verbose
```

---

## 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| SSH 접속 끊김 | UFW에서 22 차단됨 | SSAFY에 EC2 초기화 요청 (복구 불가) — 절대 22번 건드리지 말것 |
| backend 재시작 반복 | DB 헬스체크 실패 | `docker compose logs postgres`, `.env` 값 확인 |
| Jenkins 접속 안 됨 | UFW 9090 미오픈 | `sudo ufw allow 9090` |
| HTTPS 인증서 오류 | 80포트 점유 중 | `docker compose stop nginx` 후 certbot 실행 |
| `permission denied` (docker) | 그룹 미적용 | `sudo usermod -aG docker ubuntu` 후 재로그인 |
| DataGrip SSH 터널 실패 | 키 파일 형식 | `.pem` OpenSSH 형식인지 확인, 경로에 한글 없는지 확인 |

---

## 체크리스트

```
[ ] SSH 접속 성공 (ssh -i K14S309T.pem ubuntu@k14s309.p.ssafy.io)
[ ] Docker 설치 및 ubuntu 그룹 설정
[ ] UFW 포트 추가 (80, 9090) — 22/443은 기본 오픈 상태
[ ] Java 21 설치
[ ] Jenkins 설치 + 포트 9090으로 변경 + 계정 생성
[ ] Jenkins 플러그인 설치 (GitLab, SSH Agent, Docker Pipeline)
[ ] 프로젝트 git clone
[ ] infra/.env 작성 (강력한 비밀번호 사용)
[ ] HTTPS 인증서 발급 (Let's Encrypt)
[ ] nginx/ssl/ 에 인증서 복사
[ ] nginx.conf HTTPS 설정 업데이트
[ ] Jenkins SSH Credentials 등록
[ ] Jenkins 파이프라인 생성 + Jenkinsfile 도메인 수정
[ ] GitLab 웹훅 연결
[ ] docker compose up 첫 배포 성공
[ ] curl https://k14s309.p.ssafy.io/api/health 응답 확인
[ ] DataGrip SSH 터널 접속 확인
```
