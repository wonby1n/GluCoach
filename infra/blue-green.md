# GluCoach 인프라 가이드

## 서비스 아키텍처

```
인터넷
  │
  ▼
[nginx] ← SSL 종단, 리버스 프록시, 트래픽 라우팅
  │
  ├── /api, /swagger-ui, /v3/api-docs → [backend] Spring Boot :8080
  │                                          │
  │                                          └── http://ai-{color}:8000
  ├── /ai/                             → [ai] FastAPI :8000
  ├── /n8n/                            → [n8n] :5678
  └── /                                → 정적 파일 (nginx html)

[postgres] ← backend, n8n 공유 DB (PostgreSQL 17)
[redis]    ← backend 세션/캐시 (Redis 7)
```

### 서비스별 역할

| 서비스 | 이미지/런타임 | 포트 | 역할 |
|---|---|---|---|
| `nginx` | nginx:alpine | 80, 443 | SSL 종단, 리버스 프록시, blue/green 트래픽 전환 |
| `backend` | Spring Boot (Gradle) | 8080 | REST API, JWT 인증, DB/Redis 연동 |
| `ai` | FastAPI (Python) | 8000 | AI 추론, n8n 웹훅 처리, OpenAI/Anthropic 연동 |
| `postgres` | postgres:17-alpine | 5432 | 메인 DB (backend 스키마 + n8n 스키마) |
| `redis` | redis:7-alpine | 6379 | 세션, 캐시 |
| `n8n` | n8nio/n8n:latest | 5678 | 워크플로우 자동화 |

### 서비스 간 의존 관계

```
nginx
 └── depends_on: backend(healthy), ai(healthy)

backend
 └── depends_on: postgres(healthy), redis(healthy)
     env: AI_SERVICE_URL=http://ai-{color}:8000

ai
 └── depends_on: redis(healthy)
     env: BACKEND_API_URL=http://backend-{color}:8080

n8n
 └── depends_on: postgres(healthy)
```

---

## Blue-Green 배포

### 핵심 아이디어

두 개의 앱 환경(Blue, Green)을 번갈아 사용한다. 새 버전은 비활성 환경에 올린 뒤 헬스체크를 통과하면 nginx upstream 파일을 교체하고 `nginx -s reload`로 순간 전환한다. `down → up` 없이 nginx reload만으로 전환하기 때문에 다운타임이 거의 없다.

```
인터넷 → nginx → upstream.conf → [backend-blue, ai-blue]  ← 현재 활성
                                  [backend-green, ai-green] ← 대기 (다음 배포 대상)
```

### Blue-Green 대상 서비스

| 서비스 | 대상 여부 | 이유 |
|---|---|---|
| `backend` | ✅ | 무상태(stateless), 배포마다 변경 |
| `ai` | ✅ | 무상태(stateless), 모델/로직 변경 가능 |
| `postgres` | ❌ | 상태(stateful), 공유 DB |
| `redis` | ❌ | 상태(stateful), 세션/캐시 공유 |
| `n8n` | ❌ | 워크플로우 상태 존재, 배포 빈도 낮음 |
| `nginx` | ❌ | 트래픽 전환 주체 자체 |

---

## 파일 구조

```
infra/
  docker-compose.yml              # 기존 단일 compose (참고용 보관)
  docker-compose.infra.yml        # 공유 인프라: nginx, postgres, redis, n8n
  docker-compose.blue.yml         # Blue 앱: backend-blue, ai-blue
  docker-compose.green.yml        # Green 앱: backend-green, ai-green
  .active-color                   # 현재 활성 색상 ("blue" 또는 "green") — git 제외
  .env                            # 환경변수 — git 제외
  nginx/
    nginx.conf                    # upstream은 include로 분리
    upstream.conf                 # 현재 활성 upstream (nginx reload 대상) — git 제외
    upstream.blue.conf            # Blue upstream 템플릿
    upstream.green.conf           # Green upstream 템플릿
    html/
      index.html                  # 정적 파일
    ssl/                          # SSL 인증서 — git 제외
  scripts/
    deploy-bg.sh                  # Blue-Green 배포 실행 스크립트
  postgres/
    init/
      01-create-n8n-schema.sql
  Jenkinsfile
```

---

## 파일 내용

### `docker-compose.infra.yml`

공유 인프라. 배포 시 재시작하지 않는다. 최초 세팅 시 1회만 `up`.

- `ai-models` 볼륨을 여기서 선언(`name: s309-ai-models`)해서 blue/green이 동일 볼륨을 공유한다.
- Docker 네트워크는 `name: s309-internal`로 명시해야 blue/green compose에서 `external: true`로 참조할 수 있다.

### `docker-compose.blue.yml` / `docker-compose.green.yml`

앱 서비스만 포함. 차이점:
- 컨테이너명: `s309-backend-blue` / `s309-backend-green`
- `AI_SERVICE_URL`: `http://ai-blue:8000` / `http://ai-green:8000`
- `BACKEND_API_URL`: `http://backend-blue:8080` / `http://backend-green:8080`
- 네트워크/볼륨은 infra compose가 만든 것을 `external: true`로 참조

### `nginx/nginx.conf`

```nginx
http {
    include /etc/nginx/upstream.conf;  # ← blue/green 전환 대상

    upstream n8n {
        server n8n:5678;
    }
    ...
}
```

`upstream backend`와 `upstream ai`는 `upstream.conf`에서 관리한다. nginx는 재시작 없이 `nginx -s reload`만으로 upstream을 전환한다.

### `nginx/upstream.blue.conf`

```nginx
upstream backend {
    server backend-blue:8080;
}

upstream ai {
    server ai-blue:8000;
}
```

### `nginx/upstream.green.conf`

```nginx
upstream backend {
    server backend-green:8080;
}

upstream ai {
    server ai-green:8000;
}
```

### `nginx/upstream.conf`

서버에서만 관리하는 파일 (git 미추적). 현재 활성 색상의 upstream을 담는다. `deploy-bg.sh`가 `upstream.{color}.conf`를 복사해서 덮어쓴다.

### `scripts/deploy-bg.sh`

배포 흐름:
1. `.active-color` 읽어서 현재/다음 색상 결정
2. 다음 색상 컨테이너 빌드 및 시작
3. `docker inspect` 헬스체크 최대 3분 폴링
4. 헬스체크 통과 → `upstream.{next}.conf` 복사 → `nginx -s reload` (트래픽 전환)
5. `.active-color` 갱신
6. 60초 grace period 후 이전 색상 컨테이너 stop/rm

헬스체크 타임아웃 시 새 컨테이너를 정리하고 `exit 1`로 배포 실패 처리한다.

### `Jenkinsfile`

Deploy to EC2 stage에서 `docker compose down && up` 대신 `deploy-bg.sh`를 호출한다:

```groovy
chmod +x infra/scripts/deploy-bg.sh &&
infra/scripts/deploy-bg.sh
```

---

## 초기 전환 절차 (기존 → Blue-Green, 1회)

EC2 서버에서 순서대로 실행한다. **이 1회만 다운타임이 발생한다.**

```bash
cd /home/ubuntu/S14P31S309

# 1. 기존 서비스 전체 중단
docker compose -f infra/docker-compose.yml down

# 2. 코드 최신화
git pull origin release

# 3. 공유 인프라 시작 (네트워크 s309-internal, 볼륨 s309-ai-models 생성)
docker compose -f infra/docker-compose.infra.yml --env-file infra/.env up -d

# 4. blue를 초기 활성으로 설정
echo "blue" > infra/.active-color
cp infra/nginx/upstream.blue.conf infra/nginx/upstream.conf

# 5. blue 앱 시작
docker compose \
  -f infra/docker-compose.infra.yml \
  -f infra/docker-compose.blue.yml \
  --env-file infra/.env \
  up -d --build

# 6. nginx upstream 반영
docker exec s309-nginx nginx -s reload
```

이후 배포는 Jenkins가 `deploy-bg.sh`를 자동 호출한다.

---

## 롤백

### 트래픽 전환 후 60초 이내 (old 컨테이너 살아있을 때)

```bash
PREV="blue"   # 또는 green
cp infra/nginx/upstream.${PREV}.conf infra/nginx/upstream.conf
docker exec s309-nginx nginx -s reload
echo "${PREV}" > infra/.active-color
```

### 60초 이후 (old 컨테이너 정리된 후)

```bash
PREV="blue"   # 또는 green

docker compose \
  -f infra/docker-compose.infra.yml \
  -f infra/docker-compose.${PREV}.yml \
  --env-file infra/.env \
  up -d

cp infra/nginx/upstream.${PREV}.conf infra/nginx/upstream.conf
docker exec s309-nginx nginx -s reload
echo "${PREV}" > infra/.active-color
```

---

## 배포 전후 비교

| 항목 | 기존 | Blue-Green |
|---|---|---|
| 배포 다운타임 | 수십 초 ~ 수 분 | 거의 0 (nginx reload) |
| 롤백 속도 | 이전 버전 재빌드 | upstream 파일 교체 + reload (수초) |
| 배포 방식 | `compose down → up` | 새 컨테이너 기동 후 트래픽 전환 |
| DB/Redis | 공유 | 공유 (변경 없음) |
| 전환 중 리소스 | 단일 세트 | 잠깐 컨테이너 2세트 동시 실행 |

---

## DB 마이그레이션 주의사항

Blue/Green이 **같은 DB를 공유**하므로 하위 호환 마이그레이션이 필수다.

| 작업 | 규칙 |
|---|---|
| 컬럼 추가 | nullable로 추가 → blue/green 모두 읽기 가능 |
| 컬럼 삭제 | 코드에서 먼저 제거(green 배포) → 이후 컬럼 삭제 마이그레이션 별도 실행 |
| 테이블 이름 변경 | 새 테이블 추가 → 데이터 마이그레이션 → 코드 전환 → 구 테이블 삭제 순서 |

---

## 트러블슈팅

### 네트워크 연결 안 됨

blue/green compose가 `external: true`로 `s309-internal` 네트워크를 찾지 못하는 경우, infra compose가 먼저 떠 있는지 확인:

```bash
docker network ls | grep s309-internal
```

없으면 `docker compose -f infra/docker-compose.infra.yml --env-file infra/.env up -d` 실행.

### 헬스체크 타임아웃

Spring Boot 기동이 40초, ai 서비스 기동이 최대 120초로 설정되어 있다. 배포 스크립트 최대 대기는 3분이므로 빌드 포함 시간이 길면 타임아웃이 발생할 수 있다. 로그 확인:

```bash
docker logs s309-backend-green --tail 50
docker logs s309-ai-green --tail 50
```

### nginx reload 실패

```bash
docker exec s309-nginx nginx -t   # 설정 문법 검사
docker exec s309-nginx nginx -s reload
```

### 현재 활성 색상 확인

```bash
cat infra/.active-color
docker ps --format "table {{.Names}}\t{{.Status}}" | grep s309
```
