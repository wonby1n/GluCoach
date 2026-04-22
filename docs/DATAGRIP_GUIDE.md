# DataGrip으로 DB 연결하기

개발 서버(EC2)의 PostgreSQL, Redis에 DataGrip으로 접속하는 방법입니다.

> 데이터베이스 포트는 외부에 직접 노출되지 않으며, SSH 터널을 통해서만 접근할 수 있습니다.

## 사전 준비

- DataGrip 설치
- EC2 SSH 키 파일 (`.pem`) — 팀원에게 요청
- `.env` 파일의 DB 계정 정보 — 팀원에게 요청

---

## PostgreSQL 연결

### 1. 새 데이터소스 추가

DataGrip 좌측 `Database` 탭 → `+` → `Data Source` → `PostgreSQL`

### 2. SSH/SSL 탭 설정

![SSH 탭 선택 후 아래 항목 입력]

| 항목 | 값 |
|---|---|
| Use SSH tunnel | ✅ 체크 |
| Proxy host | `k14s309.p.ssafy.io` |
| Port | `22` |
| Proxy user | `ubuntu` |
| Auth type | `Key pair (OpenSSH or PuTTY)` |
| Private key file | EC2 SSH 키 `.pem` 파일 경로 |

### 3. General 탭 설정

| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `.env`의 `POSTGRES_DB` 값 |
| User | `.env`의 `POSTGRES_USER` 값 |
| Password | `.env`의 `POSTGRES_PASSWORD` 값 |

### 4. Test Connection → OK

---

## Redis 연결

### 1. 새 데이터소스 추가

DataGrip 좌측 `Database` 탭 → `+` → `Data Source` → `Redis`

### 2. SSH/SSL 탭 설정

PostgreSQL과 동일하게 SSH 터널 설정

| 항목 | 값 |
|---|---|
| Use SSH tunnel | ✅ 체크 |
| Proxy host | `k14s309.p.ssafy.io` |
| Port | `22` |
| Proxy user | `ubuntu` |
| Auth type | `Key pair (OpenSSH or PuTTY)` |
| Private key file | EC2 SSH 키 `.pem` 파일 경로 |

### 3. General 탭 설정

| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `6379` |
| Password | `.env`의 `REDIS_PASSWORD` 값 |

### 4. Test Connection → OK

---

## 주의사항

- **pem 파일**은 Git에 절대 커밋하지 마세요.
- **`.env` 파일**도 Git에 포함되지 않습니다. 계정 정보는 팀 내부 채널에서 공유하세요.
- 연결이 안 될 경우 VPN 또는 방화벽 설정을 확인하세요.
