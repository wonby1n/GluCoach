## Git 브랜치 전략

### 브랜치 구조
```
master ──────────────────────────────────────► (배포)
│
└─► develop ───────────────────────────────► (개발 통합)
│
├─► fe/feature-login-S309-131
├─► be/fix-auth-S309-045
└─► ...

master ─► hotfix/긴급수정-S309-### ─► master
└─►develop
```

### 브랜치 종류

| 브랜치 | 역할 | 분기 기준 |
|--------|------|-----------|
| `master` | 배포용 | - |
| `develop` | 개발 통합 (실질적인 main) | `master`에서 최초 1회 |
| `{파트}/{유형}-{기능}-{이슈번호}` | 기능 개발 | `develop`에서 분기 |
| `hotfix/{기능}-{이슈번호}` | 운영 긴급 수정 | `master`에서 분기 |

### 브랜치 네이밍 규칙
{파트}/{커밋 유형}-{기능}-{이슈번호}
- **파트**: `fe` / `be` / `ai` / `infra`
- **커밋 유형**: 아래 커밋 컨벤션 참고
- **이슈번호**: `S14P31S309-###` → `S309-###` 로 축약
- **예시**: `fe/feature-login-S309-131`

### 브랜치 보호 규칙

- `master`, `develop` 직접 push 금지 — MR을 통해서만 병합
- MR 승인자 최소 1명 (팀장 또는 파트 리더)
- MR 전 로컬에서 빌드 확인 필수

### WorkFlow

**일반 개발**

1. develop 브랜치 로컬에 연결
    - $ git switch -t origin/develop
2. 기능 브랜치 생성
    - (develop) $ git switch -c fe/feature-login-S309-131
3. 개발 진행 및 커밋
    - (fe/feature-login-S309-131) $ git commit ...
4. develop 브랜치로 MR
    - fe/feature-login-S309-131 → develop
5. 스프린트 종료 시 master로 MR
    - develop → master

**긴급 수정 (HOTFIX)**

1. master에서 hotfix 브랜치 생성
    - (master) $ git switch -c hotfix/login-500-S309-###
2. 수정 및 커밋
3. master와 develop 양쪽으로 MR
    - hotfix/... → master
    - hotfix/... → develop

---

## 커밋 컨벤션

### 커밋 단위 가이드

- 한 커밋 = 한 논리적 변경 (기능 단위로 쪼개서 커밋)
- 하루 작업 종료 시 최소 1회 push

### 커밋 메시지 형식
[{이슈번호}] {작업 유형}: {기능 설명}

### 작업 유형

| 유형 | 설명 |
|------|------|
| `feature` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `refactor` | 코드 리팩토링 (기능 변경 없음) |
| `design` | CSS 등 UI 디자인 변경 |
| `test` | 테스트 코드 작성/수정 |
| `chore` | 패키지 설정, .gitignore 등 기타 |
| `revert` | 이전 커밋 되돌리기 |

### 작성 예시

```
[S309-131] feature: login 컴포넌트 생성
[S309-131] feature: login/logout API 연결
[S309-131] fix: logout 시 인증 토큰 만료되게 수정
[S309-045] revert: login 컴포넌트 롤백
```