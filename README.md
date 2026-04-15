# S309

SSAFY 14기 2학기 자율 프로젝트

## 팀원

| 이름 | 역할 | GitLab/Mattermost |
|------|------|-------------------|
| 조하원 | 팀장 | @godhw1018 |
| 김정훈 | 팀원 | @kik1232198 |
| 남윤주 | 팀원 | @skadbsnwk |
| 박미영 | 팀원 | @a29279 |
| 손효지 | 팀원 | @hyoji0284 |
| 이도현 | 팀원 | @ehtm01 |

## 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA, Spring Security, PostgreSQL 17 |
| Frontend | Kotlin, Jetpack Compose, Hilt, Retrofit 2, Coil 3, Navigation Compose |
| AI | Python 3.12, FastAPI, Uvicorn |
| Infra | Docker, Nginx, Jenkins, AWS EC2 |

## 디렉토리 구조

```
S14P31S309/
├── backend/       ← Spring Boot (Java) 서버
├── frontend/      ← Android (Kotlin + Compose) 앱
├── ai/            ← FastAPI AI 서버
├── infra/         ← Docker Compose, Nginx, Jenkins
├── docs/          ← 프로젝트 문서
└── exec/          ← SSAFY 산출물 (포팅매뉴얼, 시연시나리오)
```

## 빠른 시작

### Backend

```bash
cd backend
./gradlew bootRun
# http://localhost:8080/api/health → {"status":"UP"}
# http://localhost:8080/swagger-ui.html → API 문서
```

### Frontend

1. Android Studio Panda 3에서 `frontend/` 폴더 열기
2. Gradle Sync 완료 대기
3. 에뮬레이터 또는 실기기에서 Run

### AI

```bash
cd ai
pip install -r requirements.txt
uvicorn app.main:app --reload
# http://localhost:8000/health → {"status":"UP"}
# http://localhost:8000/docs → Swagger UI
```

### Docker Compose (전체)

```bash
cd infra
cp .env.example .env
docker compose up -d
```

## 브랜치 전략

| 브랜치 | 역할 | 분기 기준 |
|--------|------|-----------|
| `master` | 배포용 | - |
| `develop` | 개발 통합 | `master`에서 최초 1회 |
| `{파트}/{유형}-{기능}-{이슈번호}` | 기능 개발 | `develop`에서 분기 |
| `hotfix/{기능}-{이슈번호}` | 운영 긴급 수정 | `master`에서 분기 |

- **파트**: `fe` / `be` / `ai` / `infra`
- **이슈번호**: `S309-###` 형식
- **예시**: `fe/feature-login-S309-131`

## 커밋 컨벤션

```
[S309-###] {유형}: {설명}
```

| 유형 | 설명 |
|------|------|
| `feature` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `refactor` | 코드 리팩토링 |
| `design` | UI 디자인 변경 |
| `test` | 테스트 코드 |
| `chore` | 기타 설정 |
| `revert` | 되돌리기 |

## 환경 설정

자세한 환경 셋업은 [docs/SETUP.md](docs/SETUP.md)를 참고하세요.
