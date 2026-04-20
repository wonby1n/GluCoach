#!/bin/bash
# =============================================================
# Git Hooks 설치 스크립트
# 프로젝트 clone 후 한 번만 실행하면 됩니다.
#
# 사용법:
#   bash scripts/setup-hooks.sh
# =============================================================

ROOT_DIR="$(git rev-parse --show-toplevel)"
HOOKS_DIR="$ROOT_DIR/.git/hooks"
SCRIPTS_DIR="$ROOT_DIR/scripts"

echo "📦 Git hooks 설치 중..."

# pre-commit hook 복사
cp "$SCRIPTS_DIR/pre-commit" "$HOOKS_DIR/pre-commit"
chmod +x "$HOOKS_DIR/pre-commit"

echo "✅ pre-commit hook 설치 완료"
echo ""
echo "이제 git commit 시 변경된 모듈의 린트가 자동 실행됩니다."
echo "  - backend/ 변경 → Spotless (Google Java Style)"
echo "  - frontend/ 변경 → ktlint + detekt"
echo ""
echo "린트를 건너뛰려면: git commit --no-verify"
echo ""

# commit-msg hook 복사
cp "$SCRIPTS_DIR/commit-msg" "$HOOKS_DIR/commit-msg"
chmod +x "$HOOKS_DIR/commit-msg"

echo "✅ commit-msg hook 설치 완료"
echo ""
echo "이후부터 커밋 메시지에 [S309-131]로 쓰셔도"
echo "자동으로 [S14P31S309-131]로 확장되어"
echo "Jira 연동이 정상 동작합니다."
