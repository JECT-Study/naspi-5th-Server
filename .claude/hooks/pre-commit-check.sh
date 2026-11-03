#!/usr/bin/env bash
# Claude 의 git commit 직전 검사 (PreToolUse 훅). 실패하면 exit 2 로 커밋을 차단한다.
#   1. 브랜치의 Jira 이슈가 docs/issues 에 기재되어 있는지 (scripts/check-issue-doc.sh)
#   2. policy.md ↔ domain 정책 파일 동기화, ERD 페이지 간 일관성
#   3. checkstyle (main, test)
cmd=$(jq -r '.tool_input.command // empty')
printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])git[[:space:]]+commit([[:space:]]|$)' || exit 0
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

if ! out=$(scripts/check-issue-doc.sh 2>&1); then
  echo "이슈 문서 검증 실패로 커밋을 차단했습니다." >&2
  printf '%s\n' "$out" >&2
  exit 2
fi

if ! out=$(python3 scripts/sync-policy.py --check 2>&1 && python3 scripts/erd-summary.py --check 2>&1); then
  echo "문서 동기화 검사 실패로 커밋을 차단했습니다. /sync-docs 로 동기화하세요." >&2
  printf '%s\n' "$out" >&2
  exit 2
fi

if ! out=$(./gradlew checkstyleMain checkstyleTest -q 2>&1); then
  echo "checkstyle 실패로 커밋을 차단했습니다. 위반 사항을 수정한 뒤 다시 커밋하세요." >&2
  printf '%s\n' "$out" | tail -30 >&2
  exit 2
fi
