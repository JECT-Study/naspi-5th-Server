#!/usr/bin/env bash
# 브랜치의 Jira 이슈 키가 docs/issues 에 기재되어 있는지 검증한다.
#
# 규칙
#   - 브랜치 이름이 {타입}/{JIRA-KEY} 형식이면 검증한다 (대소문자 무관).
#       예: feature/ND-12, fix/nd-12, refactor/ND-12
#   - "/" 뒤가 Jira 키가 아니면 검증하지 않는다.
#       예: feature/json-logging, development/1.0.0, main
#   - 검증 대상이면 해당 키가 docs/issues 아래 스토리의 "- Jira:" 줄에 적혀 있어야 한다.
#       예: "- Jira: ND-12"
#
# 사용법: scripts/check-issue-doc.sh [브랜치명]   (생략하면 현재 브랜치)
# 종료 코드: 0 통과 또는 검증 대상 아님, 1 실패
set -euo pipefail

branch="${1:-$(git rev-parse --abbrev-ref HEAD)}"

# {타입}/{JIRA-KEY} 형식이 아니면 검증하지 않는다.
if ! [[ "$branch" =~ ^[A-Za-z]+/([A-Za-z]+-[0-9]+)$ ]]; then
  exit 0
fi
key=$(printf '%s' "${BASH_REMATCH[1]}" | tr '[:lower:]' '[:upper:]')

root=$(git rev-parse --show-toplevel)
if grep -rqiE "^[[:space:]]*-[[:space:]]*Jira:.*(^|[^A-Za-z0-9-])${key}([^0-9]|$)" \
    "$root/docs/issues" --include='*.md' 2>/dev/null; then
  exit 0
fi

echo "$key 이슈가 docs/issues 에 기재되어 있지 않습니다." >&2
echo "구현 중인 스토리의 'Jira:' 칸에 $key 를 적어주세요 (예: - Jira: $key)." >&2
exit 1
