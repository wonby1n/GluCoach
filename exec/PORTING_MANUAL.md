# GluCoach 포팅 매뉴얼

## 목차

0. [사용 도구](#0-사용-도구)
1. [서버 환경](#1-서버-환경)
2. [개발 환경 및 IDE 버전](#2-개발-환경-및-ide-버전)
3. [전체 아키텍처](#3-전체-아키텍처)
4. [Docker 컨테이너 구성](#4-docker-컨테이너-구성)
5. [데이터베이스 관리](#5-데이터베이스-관리)
6. [CI/CD 파이프라인](#6-cicd-파이프라인)
7. [nginx 설정](#7-nginx-설정)
8. [AWS S3 파일 저장소](#8-aws-s3-파일-저장소)
9. [외부 서비스 계정 발급 가이드](#9-외부-서비스-계정-발급-가이드)
10. [환경변수 설정](#10-환경변수-설정)
11. [신규 서버 세팅 순서](#11-신규-서버-세팅-순서)
12. [시연 계정](#12-시연-계정)

---

## 0. 사용 도구

| 분류              | 도구           | 비고                                         |
| ----------------- | -------------- | -------------------------------------------- |
| 형상 관리         | GitLab (SSAFY) | `https://lab.ssafy.com/s14-final/S14P31S309` |
| 이슈 관리         | Jira           | 스프린트 단위 이슈 관리, Story Point 기반    |
| 커뮤니케이션      | Mattermost     | 팀 채널 + Jenkins 빌드 알림 봇               |
| 문서              | Notion         | 회의록, 기획서, API 명세                     |
| 디자인            | Figma          | UI/UX 시안 및 디자인 시스템                  |
| 워크플로우 자동화 | n8n            | `/n8n/` 경로로 운영 중                       |

---

## 1. 서버 환경

| 항목      | 내용                                         |
| --------- | -------------------------------------------- |
| 서버      | SSAFY 지급 AWS EC2 (Ubuntu 24.04.4 LTS)      |
| 도메인    | `k14s309.p.ssafy.io`                         |
| Jenkins   | `http://k14s309.p.ssafy.io:9090`             |
| GitLab    | `https://lab.ssafy.com/s14-final/S14P31S309` |

### 외부 노출 포트 (ufw + Docker)

| 포트  | 용도                       | 노출 방식           |
| ----- | -------------------------- | ------------------- |
| 22    | SSH                        | ufw                 |
| 80    | HTTP (→ 443 리다이렉트)    | ufw + Docker        |
| 443   | HTTPS (nginx 종단)         | ufw + Docker        |
| 9090  | Jenkins 웹 UI              | ufw + Docker        |
| 50000 | Jenkins 에이전트 연결 포트 | Docker (`127.0.0.1`만) |
| 5678  | n8n (`/n8n/` 외 직접 접근) | ufw + Docker        |
| 8989  | Apache HTTP Server         | SSAFY EC2 기본 이미지 (GluCoach 외 서비스) |

> PostgreSQL(5432), Redis(6379)는 `127.0.0.1`에만 바인딩되어 외부 노출되지 않으며 SSH 터널로만 접근 가능합니다.

---

## 2. 개발 환경 및 IDE 버전

### Backend (Spring Boot)

| 항목         | 버전                                 |
| ------------ | ------------------------------------ |
| 언어         | Java 21 (Eclipse Temurin)            |
| 프레임워크   | Spring Boot 3.5.0                    |
| 빌드 도구    | Gradle 8.14.4 (Wrapper 포함)         |
| DB           | PostgreSQL 17                        |
| 캐시/세션    | Redis 7                              |
| 마이그레이션 | Flyway                               |
| API 문서     | SpringDoc OpenAPI 2.8.6 (Swagger UI) |
| JWT          | jjwt 0.12.6                          |
| 권장 IDE     | IntelliJ IDEA Ultimate 2024.3+       |

### Frontend (Android)

| 항목                | 버전                                  |
| ------------------- | ------------------------------------- |
| 언어                | Kotlin                                |
| UI 프레임워크       | Jetpack Compose                       |
| DI                  | Hilt                                  |
| 네트워크            | Retrofit 2                            |
| 이미지 로딩         | Coil 3                                |
| 네비게이션          | Navigation Compose                    |
| 빌드 도구           | Gradle (KTS, Version Catalog 사용)    |
| 코드 스타일         | ktlint 12.1.0, detekt 1.23.5          |
| 권장 IDE            | Android Studio Panda 3 (또는 그 이상) |
| 최소 SDK / 타겟 SDK | `frontend/app/build.gradle.kts` 참고  |

### AI (FastAPI)

| 항목       | 버전                                             |
| ---------- | ------------------------------------------------ |
| 언어       | Python 3.12                                      |
| 프레임워크 | FastAPI ≥ 0.115                                  |
| 서버       | Uvicorn ≥ 0.34 (standard)                        |
| ML         | PyTorch ≥ 2.2, torchvision ≥ 0.17, XGBoost ≥ 2.0 |
| CV         | Ultralytics (YOLO) ≥ 8.0, Pillow ≥ 10.0          |
| LLM SDK    | anthropic ≥ 0.40, openai ≥ 1.30                  |
| PDF 리포트 | WeasyPrint ≥ 61.0, Jinja2 ≥ 3.1                  |
| 권장 IDE   | PyCharm Professional 2024.3+ 또는 VS Code        |

### Infra

| 항목    | 버전                                                  |
| ------- | ----------------------------------------------------- |
| OS      | Ubuntu 24.04.4 LTS                                    |
| Docker  | 24.x 이상 (운영 환경: 29.x, Compose 플러그인 v2 이상) |
| Jenkins | jenkins/jenkins:lts                                   |
| Nginx   | nginx:alpine (Let's Encrypt SSL)                      |

---

## 3. 전체 아키텍처

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

## 4. Docker 컨테이너 구성

### Compose 파일 구조

| 파일                             | 역할                                                                 |
| -------------------------------- | -------------------------------------------------------------------- |
| `infra/docker-compose.infra.yml` | 공유 인프라 (nginx, postgres, redis, n8n) — 배포 시 재시작 없음      |
| `infra/docker-compose.blue.yml`  | Blue 앱 서비스 (backend-blue, ai-blue)                               |
| `infra/docker-compose.green.yml` | Green 앱 서비스 (backend-green, ai-green)                            |
| `infra/docker-compose.yml`       | (Legacy) Blue-Green 도입 전 단일 컨테이너 구성. 운영에서는 사용 금지 |

### 실행 중인 컨테이너

| 컨테이너                                   | 이미지               | 포트               | 역할                                   |
| ------------------------------------------ | -------------------- | ------------------ | -------------------------------------- |
| `s309-nginx`                               | `nginx:alpine`       | 80, 443            | 리버스 프록시, HTTPS 종단, 트래픽 전환 |
| `s309-backend-blue` / `s309-backend-green` | 자체 빌드            | 8080 (내부)        | Spring Boot API 서버                   |
| `s309-ai-blue` / `s309-ai-green`           | 자체 빌드            | 8000 (내부)        | FastAPI AI 서버                        |
| `s309-postgres`                            | `postgres:17-alpine` | 5432 (localhost만) | 메인 DB                                |
| `s309-redis`                               | `redis:7-alpine`     | 6379 (localhost만) | 캐시, 세션                             |
| `s309-n8n`                                 | `n8nio/n8n:latest`   | 5678               | 워크플로우 자동화                      |

> PostgreSQL, Redis는 외부에 노출되지 않으며 SSH 터널을 통해서만 접근 가능.

### Docker 볼륨

| 볼륨 이름           | 실제 이름 (`docker volume ls`) | 용도                            |
| ------------------- | ------------------------------ | ------------------------------- |
| `postgres-data`     | `infra_postgres-data`          | PostgreSQL 데이터               |
| `redis-data`        | `infra_redis-data`             | Redis 데이터                    |
| `n8n-data`          | `infra_n8n-data`               | n8n 워크플로우 데이터           |
| `ai-models`         | `s309-ai-models`               | AI 컨테이너의 ML 모델 가중치 캐시 |
| `jenkins_home`      | `jenkins_home`                 | Jenkins 홈 디렉토리             |

> `infra_` 접두사는 docker-compose 프로젝트 이름(`infra/` 디렉토리)에서 자동으로 붙음. `ai-models`는 `docker-compose.infra.yml`에서 `name: s309-ai-models`로 명시되어 접두사가 붙지 않음. 백업/복구 시 위 "실제 이름"을 사용해야 합니다.

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

## 5. 데이터베이스 관리

### PostgreSQL

- **버전**: 17 (Docker)
- **데이터 영속화**: Docker Volume `postgres-data` (실제 이름: `infra_postgres-data`)
- **스키마 관리**: Flyway 마이그레이션 (`backend/src/main/resources/db/migration/`)
    - `V1__init_schema.sql` — 초기 스키마
    - `V2__replace_health_tables_with_daily_summary.sql` — 헬스 데이터 일일 요약 테이블로 통합
    - `V3` ~ `V14` — 헬스 스냅샷, 알림, 혈당 유니크 제약, 에이전트 트리거, 음식 영양 컬럼 확장, 수면 세션, 채팅 메시지, 보호자 알림 채팅 링크, 알림 타입 정리 등 기능 추가/정제
    - `V15` ~ `V18` — `foods.display_name` 컬럼 추가 및 LLM 기반 표시명 정제
    - 전체 18개 (V1~V18). 상세 내역은 `db/migration/` 디렉토리 파일명을 직접 참고
- **특이사항**: `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` 설정 (기존 DB에 Flyway 적용 시 필요)

### Redis

- **버전**: 7 (Docker)
- **데이터 영속화**: Docker Volume `redis-data` (실제 이름: `infra_redis-data`)
- **인증**: 비밀번호 필수 (`REDIS_PASSWORD`)
- **용도**: 세션 저장, API 캐시

### DataGrip으로 접속하는 법

SSH 터널 방식으로만 접근 가능. 자세한 설정은 `docs/DATAGRIP_GUIDE.md` 참고.

|          | PostgreSQL              | Redis       |
| -------- | ----------------------- | ----------- |
| SSH Host | `k14s309.p.ssafy.io:22` | 동일        |
| SSH User | `ubuntu`                | 동일        |
| SSH 인증 | EC2 `.pem` 키 파일      | 동일        |
| DB Host  | `localhost`             | `localhost` |
| DB Port  | `5432`                  | `6379`      |

---

## 6. CI/CD 파이프라인

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

| ID             | 종류                   | 용도                    |
| -------------- | ---------------------- | ----------------------- |
| `gitlab-token` | Username with Password | GitLab HTTPS 체크아웃   |
| `ec2-ssh-key`  | SSH Private Key        | EC2 배포 SSH 접속       |
| `firebase-key` | Secret File            | Firebase 서비스 계정 키 |
| `food-api-key` | Secret Text            | 공공 식품 API 키 (향후 데이터 갱신용으로 보관, 평시 미사용) |

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

## 7. nginx 설정

파일 위치: `infra/nginx/nginx.conf`

### 주요 설정

| 설정          | 내용                                                                 |
| ------------- | -------------------------------------------------------------------- |
| HTTP → HTTPS  | 80 포트 요청을 443으로 301 리다이렉트                                |
| SSL 인증서    | Let's Encrypt (`/etc/nginx/ssl/fullchain.pem`)                       |
| SSL 자동 갱신 | systemd timer (`certbot.timer`) — EC2에서 운영 중                    |
| Rate Limiting | IP당 100req/min, 초과 시 429 응답                                    |
| 업스트림 전환 | `nginx/upstream.conf` include 방식 — `nginx -s reload`로 무중단 전환 |

### Blue-Green upstream 구조

```
nginx.conf
  └── include /etc/nginx-live/upstream.conf   ← 배포 시 교체되는 파일
                                              (compose에서 ./nginx:/etc/nginx-live:ro 로 마운트)

upstream.conf (현재 활성이 blue인 경우):
  upstream backend { server backend-blue:8080; }
  upstream ai      { server ai-blue:8000;      }
```

### 라우팅 규칙

| 경로                          | 대상                                        |
| ----------------------------- | ------------------------------------------- |
| `/`                           | 랜딩 페이지 (`infra/nginx/html/index.html`) |
| `/api/*`                      | Backend (rate limit 적용)                   |
| `/swagger-ui`, `/v3/api-docs` | Backend                                     |
| `/ai/*`                       | AI 서버 (rate limit 적용)                   |
| `/n8n/`                       | n8n 워크플로우 (WebSocket 지원)             |
| `/health`                     | nginx 헬스체크 엔드포인트                   |

---

## 8. AWS S3 파일 저장소

### 구성

| 항목       | 내용                               |
| ---------- | ---------------------------------- |
| 버킷 이름  | `glucoach-images`                  |
| 리전       | `ap-northeast-2` (서울)            |
| IAM 사용자 | `glucoach-s3`                      |
| 접근 방식  | 백엔드 경유 업로드 / Presigned URL |

### IAM 정책 (최소 권한)

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
            "Resource": "arn:aws:s3:::glucoach-images/*"
        }
    ]
}
```

---

## 9. 외부 서비스 계정 발급 가이드

신규 서버 세팅 시 아래 서비스들의 키가 필요합니다. 각 서비스에서 발급받은 키는 `infra/.env` 파일에 입력합니다.

### 9.1 OpenAI API

| 항목     | 내용                                      |
| -------- | ----------------------------------------- |
| 가입 URL | `https://platform.openai.com/signup`      |
| 키 발급  | API Keys 메뉴 → Create new secret key     |
| 사용처   | AI 서버 (영양 상담 LLM, 이미지 인식 보조) |
| 환경변수 | `OPENAI_API_KEY`, `OPENAI_BASE_URL`       |

### 9.2 Anthropic Claude API

| 항목     | 내용                                   |
| -------- | -------------------------------------- |
| 가입 URL | `https://console.anthropic.com`        |
| 키 발급  | Settings → API Keys → Create Key       |
| 사용처   | AI 서버 (Claude 기반 챗봇/리포트 생성) |
| 환경변수 | `ANTHROPIC_API_KEY`                    |

### 9.3 AWS (S3 + IAM)

| 항목      | 내용                                                                                          |
| --------- | --------------------------------------------------------------------------------------------- |
| 가입 URL  | `https://aws.amazon.com`                                                                      |
| 발급 절차 | IAM → 사용자 생성 → 정책 연결(§8 정책 참고) → Access Key 발급                                 |
| 사용처    | 백엔드 (음식 이미지 업로드)                                                                   |
| 환경변수  | `AWS_S3_ACCESS_KEY`, `AWS_S3_SECRET_KEY`, `AWS_S3_REGION`, `AWS_S3_BUCKET`, `AWS_S3_ENDPOINT` |
| 비고      | 버킷은 `ap-northeast-2` (서울) 리전에 생성, 퍼블릭 액세스 차단 권장                           |

### 9.4 Firebase (FCM 푸시 알림)

| 항목     | 내용                                                                  |
| -------- | --------------------------------------------------------------------- |
| 가입 URL | `https://console.firebase.google.com`                                 |
| 사용처   | 백엔드(보호자 푸시 알림 전송), 안드로이드 앱(FCM 토큰 수신 및 알림 표시) |

> 본 프로젝트는 이미 `glucoach-s309` Firebase 프로젝트가 만들어져 있고, 안드로이드용 `google-services.json`은 리포지토리에 커밋되어 있습니다(`frontend/app/google-services.json`). 백엔드용 서비스 계정 키만 Jenkins Credential을 통해 빌드에 주입하면 됩니다. 아래 절차는 **완전히 새로운 Firebase 프로젝트로 옮길 경우**의 가이드입니다.

#### Firebase는 두 종류의 키를 사용 — 반드시 구분

| 키 파일                          | 어디에 쓰이나                                              | 어디에 두나                                                                                            | 비고                              |
| -------------------------------- | ---------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ | --------------------------------- |
| `google-services.json`           | 안드로이드 앱이 FCM 서버에 토큰을 등록할 때 사용 (클라이언트 설정) | `frontend/app/google-services.json` (리포지토리에 커밋)                                                | 패키지명 `com.ssafy.s309`와 일치 필수 |
| `firebase-service-account.json`  | 백엔드가 FCM에 푸시 전송할 때 인증 (서버 측 비밀 키)        | Jenkins Credentials `firebase-key` (Secret File) → 빌드 시 `backend/src/main/resources/`에 복사 후 JAR 포함 | **절대 외부 공개 금지**            |

두 파일은 같은 Firebase 프로젝트에 속하지만 발급 위치도, 노출 정책도 다릅니다.

#### A. 안드로이드용 `google-services.json` 발급 절차

1. `https://console.firebase.google.com` 접속 → "프로젝트 추가" → 프로젝트 이름 입력 (예: `glucoach-s309`)
2. 좌측 상단 톱니바퀴 → **프로젝트 설정**
3. 하단 **내 앱** 섹션 → 안드로이드 아이콘 클릭 ("앱 추가")
4. **Android 패키지 이름**: `com.ssafy.s309` 입력
    - `frontend/app/build.gradle.kts`의 `namespace` / `applicationId`와 정확히 일치해야 합니다. 다르면 앱이 FCM 토큰 등록에 실패합니다.
5. "앱 등록" 클릭 → `google-services.json` 다운로드
6. 다운로드한 파일을 `frontend/app/google-services.json` 위치에 저장 (기존 파일 덮어쓰기)
7. APK 재빌드: `./gradlew assembleRelease`

> `frontend/app/build.gradle.kts:131-133`이 `firebase-bom` + `firebase-messaging`을 의존하고 있고, `libs.versions.toml:135`이 `com.google.gms.google-services 4.4.2` 플러그인을 선언합니다. JSON 파일만 올바르게 배치되면 자동으로 적용됩니다.

#### B. 백엔드용 `firebase-service-account.json` 발급 절차

1. 같은 Firebase Console → **프로젝트 설정** → 상단 탭 **서비스 계정**
2. **Firebase Admin SDK** 섹션 → 언어 **Java** 선택 → **새 비공개 키 생성** 클릭
3. JSON 파일이 다운로드됩니다 — 이 파일이 `firebase-service-account.json` 입니다.
4. Jenkins 웹 UI 접속 → **Credentials** → Add Credentials
5. Kind: **Secret file**, ID: `firebase-key`, File: 위 JSON 업로드
6. 이후 release 브랜치 빌드 시 `Jenkinsfile`이 자동으로:
    - EC2의 `~/firebase-service-account.json` 위치에 `scp`
    - `backend/src/main/resources/firebase-service-account.json` 로 복사
    - `./gradlew bootJar` 빌드 시 JAR 내부에 포함 (런타임 마운트 아님)

#### 키 회전(교체) 시

- `google-services.json` 교체 → 안드로이드 APK 재빌드 후 사용자에게 재배포 (인앱/스토어 업데이트)
- `firebase-service-account.json` 교체 → Jenkins Credential `firebase-key` 파일 교체 → release 브랜치에 빈 커밋 push → 재빌드/재배포

### 9.5 식품안전처 공공 데이터 API

| 항목      | 내용                                                                      |
| --------- | ------------------------------------------------------------------------- |
| 가입 URL  | `https://www.data.go.kr/`                                                 |
| 발급 절차 | 회원가입 → API 신청 → 인증키 발급 (영업일 1~2일 소요)                     |
| 사용처    | DB 적재용 (초기 데이터 사전 적재 완료, 운영 중 외부 호출 없음)            |
| 환경변수  | `FOOD_API_KEY`                                                            |
| 비고      | 식품 영양 데이터는 Flyway 마이그레이션과 별도 시드로 DB에 전부 적재되어 있어 런타임에는 외부 API를 호출하지 않습니다. 키는 향후 식품 DB 갱신 작업이 발생할 경우에만 사용하며, Jenkins Credentials에 등록만 해두고 평소에는 사용하지 않습니다. |

### 9.6 SSAFY 인프라 제공 항목

| 항목           | 내용                              |
| -------------- | --------------------------------- |
| EC2 인스턴스   | SSAFY 지급 (Ubuntu 24.04.4 LTS)   |
| 도메인         | `k14s309.p.ssafy.io` (SSAFY 발급) |
| `.pem` 키 파일 | SSAFY EduRoom에서 다운로드        |
| GitLab 계정    | SSAFY EduRoom 계정                |

---

## 10. 환경변수 설정

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
REDIS_HOST=redis
REDIS_PORT=6379
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

#### 보안/생성 가이드

- **파일 권한**: 시크릿 노출 방지를 위해 반드시 `chmod 600 infra/.env` (소유자 읽기/쓰기 전용)
- **`JWT_SECRET` 생성**: 충분히 긴 무작위 문자열이어야 함. 예시:
  ```bash
  openssl rand -base64 64
  ```
- **`ADMIN_API_KEY` / `AGENT_API_KEY`**: 동일하게 `openssl rand -base64 32` 등으로 무작위 생성
- **`AWS_S3_ENDPOINT`**: AWS 표준 S3를 사용할 경우 **빈 값**으로 두면 됩니다(SDK가 리전 기반 기본 엔드포인트를 사용). MinIO 등 S3 호환 자체 호스팅 스토리지를 쓸 때에만 명시적으로 입력하세요. `application.yml:61`이 `${AWS_S3_ENDPOINT:}` 기본값(빈 문자열)을 허용합니다.
- **`OPENAI_BASE_URL`**: SSAFY 제공 GMS 프록시(`https://gms.ssafy.io/gmsapi/api.openai.com/v1`) 기본 사용. 자체 OpenAI 키를 쓸 경우 `https://api.openai.com/v1`로 변경.
- **`REDIS_HOST` / `REDIS_PORT`**: Docker 네트워크 내부에서는 각각 `redis`, `6379` 고정 (`.env.example` 기본값 그대로 사용).

---

## 11. 신규 서버 세팅 순서

새 서버에 처음 세팅하는 경우 아래 순서대로 진행.

### 사전 준비

> **시작 전 체크 — DNS A 레코드**
> 도메인이 EC2 공인 IP를 가리키도록 매핑되어 있어야 합니다. SSAFY 지급 도메인(`k14s309.p.ssafy.io`)은 자동 연결되지만 자체 도메인을 쓸 경우 먼저 매핑하세요. 매핑 누락 시 SSL 발급 단계에서 ACME 챌린지가 실패합니다.
>
> ```bash
> dig +short k14s309.p.ssafy.io   # 출력이 EC2 공인 IP와 일치해야 함
> ```

```bash
# Docker 설치
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker ubuntu
newgrp docker

# UFW 방화벽 포트 개방
# (SSAFY EC2는 UFW가 활성 상태로 지급되며 기본 22, 443만 열려 있음)
sudo ufw allow 80/tcp     # HTTP → HTTPS 리다이렉트
sudo ufw allow 443/tcp    # HTTPS (이미 열려 있으면 무시됨)
sudo ufw allow 9090/tcp   # Jenkins 웹 UI
sudo ufw allow 5678/tcp   # n8n 직접 접근 (옵션)
sudo ufw status

# 소스 클론
cd /home/ubuntu
git clone https://lab.ssafy.com/s14-final/S14P31S309.git S14P31S309
cd S14P31S309

# .env 파일 생성
cp infra/.env.example infra/.env
chmod 600 infra/.env   # 시크릿 노출 방지 — 소유자만 읽기/쓰기
# infra/.env 파일에 실제 값 입력 (§10 가이드 참고)
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

### SSL 자동 갱신 hook 설정 (중요)

nginx는 `/home/ubuntu/ssl/`을 마운트하지만, certbot은 `/etc/letsencrypt/live/`에 갱신본을 둡니다. 갱신본을 nginx가 인식하려면 deploy hook으로 마운트 경로에 복사하고 nginx를 reload해야 합니다.

```bash
sudo tee /etc/letsencrypt/renewal-hooks/deploy/copy-to-nginx.sh > /dev/null <<'EOF'
#!/bin/bash
set -e
cp /etc/letsencrypt/live/k14s309.p.ssafy.io/fullchain.pem /home/ubuntu/ssl/
cp /etc/letsencrypt/live/k14s309.p.ssafy.io/privkey.pem  /home/ubuntu/ssl/
chmod 644 /home/ubuntu/ssl/fullchain.pem
chmod 600 /home/ubuntu/ssl/privkey.pem
docker exec s309-nginx nginx -s reload
EOF
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/copy-to-nginx.sh

# 검증: dry-run 실행 시 hook도 함께 실행됨
sudo certbot renew --dry-run
```

> 이 hook이 없으면 인증서는 자동 갱신되지만 nginx는 옛 인증서를 계속 사용하게 됩니다 (실서비스에서 HTTPS 만료 → 접속 불가).

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
  -p 127.0.0.1:50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  --name jenkins \
  --restart unless-stopped \
  jenkins/jenkins:lts
```

> 50000은 Jenkins 에이전트 연결 포트입니다. 본 프로젝트는 빌트인 에이전트(master)만 사용하므로 외부 노출이 불필요해 `127.0.0.1`에만 바인딩합니다. 외부 에이전트를 붙일 경우에만 `0.0.0.0:50000:50000`으로 변경하세요.

### Jenkins 초기 설정 (웹 UI)

1. `http://서버IP:9090` 접속
2. 초기 비밀번호 확인: `docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword`
3. 권장 플러그인 설치
4. 추가 플러그인 설치: **GitLab**, **SSH Agent**
5. Credentials 등록:

| ID             | 종류                   | 값                              |
| -------------- | ---------------------- | ------------------------------- |
| `gitlab-token` | Username with Password | GitLab 계정 + 토큰              |
| `ec2-ssh-key`  | SSH Private Key        | EC2 `.pem` 키 내용              |
| `firebase-key` | Secret File            | `firebase-service-account.json` |
| `food-api-key` | Secret Text            | 공공 식품 API 키 (향후 갱신용으로 보관, 평시 미사용) |

6. 파이프라인 생성: Pipeline script from SCM → Git → `infra/Jenkinsfile` 경로 지정
7. GitLab Webhook 설정: `http://서버IP:9090/project/glucoach`

---

## 12. 시연 계정

본 프로젝트는 별도의 관리자 페이지나 보호자 전용 플로우를 운영 화면에 분리하지 않았으므로, 시연은 단일 테스트 계정으로 진행합니다.

| 항목      | 값                       |
| --------- | ------------------------ |
| 이메일    | `hawon@test.com`         |
| 비밀번호  | `12345678`               |
| 접근 경로 | 안드로이드 앱 로그인 화면 |

> 회원가입 후 동일 자격으로 로그인하면 일반 사용자 경험을 모두 확인할 수 있습니다. 보호자 알림 기능은 백엔드/n8n 측에 구현되어 있으나 시연 화면에서는 별도 전환 플로우 없이 백그라운드로 동작합니다.
