# Git 컨벤션 — S14P31S309

> 프로젝트: S14P31S309 (SSAFY 14기 2학기 자율 프로젝트)
> 최종 수정: 2026-04-20
> 적용 범위: 전 파트 공통 (FE/BE/AI/INFRA)

## Git 브랜치 전략

### 브랜치 구조

```
master ──────────────────────────────────────► (배포)
│
└─► develop ───────────────────────────────► (개발 통합)
│
├─► fe/feature-login-S14P31S309-131
├─► be/fix-auth-S14P31S309-045
└─► ...

master ─► hotfix/긴급수정-S14P31S309-### ─► master
└─►develop
```

### 브랜치 종류

| 브랜치                            | 역할                      | 분기 기준             |
| --------------------------------- | ------------------------- | --------------------- |
| `master`                          | 배포용                    | -                     |
| `develop`                         | 개발 통합 (실질적인 main) | `master`에서 최초 1회 |
| `{파트}/{유형}-{기능}-{이슈번호}` | 기능 개발                 | `develop`에서 분기    |
| `hotfix/{기능}-{이슈번호}`        | 운영 긴급 수정            | `master`에서 분기     |

### 브랜치 네이밍 규칙

{파트}/{커밋 유형}-{기능}-{이슈번호}

- **파트**: `fe` / `be` / `ai` / `infra`
- **커밋 유형**: 아래 커밋 컨벤션 참고
- **이슈번호**: `S14P31S309-###`
- **예시**: `fe/feature-login-S14P31S309-131`

### 브랜치 보호 규칙

- `master`, `develop` 직접 push 금지 — MR을 통해서만 병합
- MR 승인자 최소 1명 (팀장 또는 파트 리더)
- MR 전 로컬에서 빌드 확인 필수

### Workflow

**일반 개발**

1. develop 브랜치 로컬에 연결

```bash
   git switch -t origin/develop
```

2. 기능 브랜치 생성

```bash
   (develop) $ git switch -c fe/feature-login-S14P31S309-131
```

3. 개발 진행 및 커밋

```bash
   (fe/feature-login-S14P31S309-131) $ git commit -m "[S14P31S309-131] feature: 로그인 폼 구현"
```

4. develop 브랜치로 MR
    - `fe/feature-login-S14P31S309-131` → `develop`

5. 스프린트 종료 시 master로 MR
    - `develop` → `master`

**긴급 수정 (HOTFIX)**

1. master에서 hotfix 브랜치 생성

```bash
   (master) $ git switch -c hotfix/login-500-S14P31S309-###
```

2. 수정 및 커밋

3. master와 develop 양쪽으로 MR
    - `hotfix/...` → `master`
    - `hotfix/...` → `develop`

4. MR 생성 시 템플릿 드롭다운에서 **`hotfix` 템플릿 선택** 필수

---

## 커밋 컨벤션

### 커밋 단위 가이드

- 한 커밋 = 한 논리적 변경 (기능 단위로 쪼개서 커밋)
- 하루 작업 종료 시 최소 1회 push

### 커밋 메시지 형식

[{이슈번호}] {작업 유형}: {기능 설명}

- 단축 키(`#XXX`)로 입력해도 **commit-msg hook에 의해 자동으로 풀 키로 확장**됩니다
    - 예: `[#131] feature: 로그인` → 커밋 시 `[S14P31S309-131] feature: 로그인`으로 변환
    - 단, 이 기능은 `scripts/setup-hooks.sh`를 실행한 환경에서만 작동합니다
    - 저장소 클론 직후 반드시 setup 스크립트를 실행해주세요

### 작업 유형

| 유형       | 설명                            |
| ---------- | ------------------------------- |
| `feature`  | 새로운 기능 추가                |
| `fix`      | 버그 수정                       |
| `docs`     | 문서 수정                       |
| `refactor` | 코드 리팩토링 (기능 변경 없음)  |
| `design`   | CSS 등 UI 디자인 변경           |
| `test`     | 테스트 코드 작성/수정           |
| `chore`    | 패키지 설정, .gitignore 등 기타 |
| `revert`   | 이전 커밋 되돌리기              |

### 작성 예시

```
[#131] feature: login 컴포넌트 생성
[#131] feature: login/logout API 연결
[#131] fix: logout 시 인증 토큰 만료되게 수정
[#45] revert: login 컴포넌트 롤백
```

---

## Merge Request (MR) 규칙

### MR 제목 형식

[S14P31S309-###] {작업 유형}: {기능 설명}

- 마지막 커밋 메시지가 MR 제목으로 자동 입력됩니다 (GitLab 기본 동작)
- 커밋 메시지를 풀 키로 작성해뒀다면 수정할 필요 없이 그대로 제출

### MR 템플릿

`.gitlab/merge_request_templates/` 하위 템플릿 중 상황에 맞는 것을 선택:

| 템플릿    | 사용 상황                       |
| --------- | ------------------------------- |
| `default` | 일반 기능 개발, 버그 수정       |
| `hotfix`  | 운영 긴급 수정 (master 대상 MR) |

MR 생성 페이지 상단의 **Description template** 드롭다운에서 선택하세요.

### MR 머지 원칙

- 승인 없이 머지 금지 — 최소 1명의 승인 필요
- 로컬 빌드 실패 상태에서 MR 생성 금지
- HOTFIX는 master 머지 후 **반드시 develop으로도 별도 MR 생성**

---

## FAQ

**Q. 단축 키(`131`)로 커밋해도 되나요?**
A. 네. commit-msg hook이 자동으로 풀 키로 확장합니다. 다만 **브랜치명과 MR 제목은 hook이 개입하지 못하므로 반드시 풀 키를 직접 입력**해주세요.

**Q. setup-hooks.sh를 실행 안 한 상태로 커밋하면 어떻게 되나요?**
A. 커밋은 정상적으로 됩니다만 단축 키가 그대로 남아 Jira 연동이 안 됩니다. 저장소 클론 직후 반드시 실행해주세요.

**Q. 브랜치를 이미 단축 키로 만들었는데 어떻게 하나요?**
A. 리모트에 push하기 전이면 로컬에서 rename 가능:

```bash
git branch -m fe/feature-login-S309-131 fe/feature-login-S14P31S309-131
```

이미 push 후라면 새 브랜치로 옮겨서 작업하세요 (단축 키 브랜치는 삭제).

**Q. 혼자 개발하는 개인 실험 브랜치도 규칙 따라야 하나요?**
A. 리모트에 push할 거면 따라야 합니다. 로컬에서만 쓰는 브랜치면 자유롭게 이름 지으셔도 됩니다.
