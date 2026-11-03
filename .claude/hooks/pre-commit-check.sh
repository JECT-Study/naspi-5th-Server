#!/usr/bin/env bash
# Claude 의 git commit 직전 검사 (PreToolUse 훅). 실패하면 exit 2 로 커밋을 차단하고 이유를 Claude 에게 전달한다.
#   1. 브랜치의 Jira 이슈가 docs/issues 에 기재되어 있는지 (scripts/check-issue-doc.sh)
#   2. 정책 원본 자동 동기화
#      - policy.md / domain-map.json 이 스테이징되어 있으면 domain/*.md 를 다시 생성해 함께 스테이징한다.
#      - policy.md / roadmap.md 가 스테이징되어 있고 아직 영향 분석(이슈 초안·ERD)을 하지 않은 내용이면 커밋을 멈춘다.
#        분석을 마친 뒤 scripts/mark-docs-reviewed.sh 로 기록하면 통과한다.
#   3. domain 정책 파일 동기화 (docs/policies/ 변경 시), ERD 페이지 간 일관성 (docs/erd/ 변경 시)
#   4. checkstyle (main, test)
cmd=$(jq -r '.tool_input.command // empty')
printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])git[[:space:]]+commit([[:space:]]|$)' || exit 0
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

# 1. 이슈 기재
if ! out=$(scripts/check-issue-doc.sh 2>&1); then
  echo "이슈 문서 검증 실패로 커밋을 차단했습니다." >&2
  printf '%s\n' "$out" >&2
  exit 2
fi

# 2. 정책 원본 자동 동기화
staged=$(git diff --cached --name-only --diff-filter=ACMR)
synced=0
if printf '%s\n' "$staged" | grep -qE '^docs/policies/v[0-9]+/(policy\.md|domain-map\.json)$'; then
  if ! out=$(python3 scripts/sync-policy.py 2>&1); then
    echo "정책 원본을 domain 파일로 동기화하지 못해 커밋을 차단했습니다. domain-map.json 을 확인하세요 (/sync-docs 2단계)." >&2
    printf '%s\n' "$out" >&2
    exit 2
  fi
  git add docs/policies/v*/domain
  synced=1
fi
stamp=$(git rev-parse --git-path sync-docs-reviewed)
pending=()
for f in $(printf '%s\n' "$staged" | grep -E '^docs/policies/(roadmap\.md|v[0-9]+/policy\.md)$' || true); do
  blob=$(git rev-parse -q --verify ":$f")
  grep -qx "$f $blob" "$stamp" 2>/dev/null || pending+=("$f")
done
if (( ${#pending[@]} )); then
  {
    echo "정책 문서가 바뀌어 커밋을 잠시 멈췄습니다: ${pending[*]}"
    (( synced )) && echo "domain 정책 파일은 자동으로 다시 생성해 스테이징했습니다."
    echo "커밋 전에 다음을 수행하세요."
    echo "  1. /sync-docs 의 3~6단계에 따라 변경이 이슈 초안(docs/issues)과 ERD(docs/erd/naspi.drawio)에 주는 영향을 분석한다."
    echo "     roadmap.md 변경이면 현재 버전 정책과 충돌하거나 현재 버전으로 옮겨야 할 내용이 있는지만 확인한다."
    echo "  2. 수정이 필요하면 사용자에게 수정안을 보여주고 승인받아 반영한 뒤 git add 한다. 영향이 없으면 그 이유를 사용자에게 알린다."
    echo "  3. scripts/mark-docs-reviewed.sh 를 실행하고 다시 커밋한다."
  } >&2
  exit 2
fi

# 3. 문서 동기화 · ERD 일관성 (관련 파일이 커밋에 포함될 때만)
staged=$(git diff --cached --name-only)
if printf '%s\n' "$staged" | grep -q '^docs/policies/'; then
  if ! out=$(python3 scripts/sync-policy.py --check 2>&1); then
    echo "domain 정책 파일이 policy.md 와 다릅니다. domain/*.md 는 직접 수정하지 말고 policy.md 를 고치세요." >&2
    printf '%s\n' "$out" >&2
    exit 2
  fi
fi
if printf '%s\n' "$staged" | grep -q '^docs/erd/'; then
  if ! out=$(python3 scripts/erd-summary.py --check 2>&1); then
    echo "ERD 페이지 간 테이블 정의가 달라 커밋을 차단했습니다. 도메인 페이지와 전체 페이지를 함께 고치세요." >&2
    printf '%s\n' "$out" >&2
    exit 2
  fi
fi

# 4. checkstyle
if ! out=$(./gradlew checkstyleMain checkstyleTest -q 2>&1); then
  echo "checkstyle 실패로 커밋을 차단했습니다. 위반 사항을 수정한 뒤 다시 커밋하세요." >&2
  printf '%s\n' "$out" | tail -30 >&2
  exit 2
fi
