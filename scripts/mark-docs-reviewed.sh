#!/usr/bin/env bash
# 정책 원본(policy.md, roadmap.md)의 영향 분석(이슈 초안·ERD)을 마쳤음을 기록한다.
# 커밋 전 훅은 스테이징된 정책 파일 내용이 기록과 다르면 커밋을 멈추고 영향 분석을 요청한다.
# 기록 위치: .git/sync-docs-reviewed (로컬 전용, 커밋되지 않음)
set -euo pipefail
root=$(git rev-parse --show-toplevel)
cd "$root"
stamp=$(git rev-parse --git-path sync-docs-reviewed)
tmp=$(mktemp)
[[ -f "$stamp" ]] && cp "$stamp" "$tmp"
for f in docs/policies/roadmap.md docs/policies/v*/policy.md; do
  [[ -f "$f" ]] || continue
  blob=$(git rev-parse -q --verify ":$f" 2>/dev/null || git hash-object "$f")
  grep -v "^$f " "$tmp" > "$tmp.n" || true
  echo "$f $blob" >> "$tmp.n"
  mv "$tmp.n" "$tmp"
done
mv "$tmp" "$stamp"
echo "영향 분석 완료로 기록했습니다:"
sed 's/^/  /' "$stamp"
