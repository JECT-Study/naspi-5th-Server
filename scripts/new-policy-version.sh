#!/usr/bin/env bash
# 새 정책 버전을 시작한다. 현재 버전(docs/policies/CURRENT)을 통째로 복사해 새 버전 폴더를 만들고 CURRENT 를 옮긴다.
#
# 사용법: scripts/new-policy-version.sh v2
#
# 하는 일
#   1. docs/policies/<현재>/ → docs/policies/<새 버전>/ 복사 (policy.md, domain-map.json, domain/)
#   2. domain-map.json 의 문서 제목 "(<현재>)" → "(<새 버전>)"
#   3. docs/issues/<새 버전>/ 생성
#   4. docs/policies/CURRENT 를 새 버전으로 변경
#   5. domain 파일 재생성
# 이전 버전 폴더는 그대로 두며, 이후로는 수정하지 않는다 (동결).
set -euo pipefail
root=$(git rev-parse --show-toplevel)
new="${1:-}"
[[ "$new" =~ ^v[0-9]+$ ]] || { echo "사용법: scripts/new-policy-version.sh v<번호>  (예: v2)" >&2; exit 2; }
cur=$("$root/scripts/policy-version.sh")
[[ "$new" != "$cur" ]] || { echo "$new 는 이미 현재 버전입니다." >&2; exit 2; }
[[ ! -e "$root/docs/policies/$new" ]] || { echo "docs/policies/$new 가 이미 있습니다." >&2; exit 2; }

cp -R "$root/docs/policies/$cur" "$root/docs/policies/$new"
python3 - "$root/docs/policies/$new/domain-map.json" "$cur" "$new" <<'PY'
import sys
p, cur, new = sys.argv[1:]
s = open(p, encoding='utf-8').read().replace(f'({cur})', f'({new})')
open(p, 'w', encoding='utf-8').write(s)
PY
mkdir -p "$root/docs/issues/$new"
cat > "$root/docs/issues/$new/README.md" <<MD
# $new 이슈 초안

정책 원본: [docs/policies/$new/policy.md](../../policies/$new/policy.md)

도메인별 스토리 파일(예: \`member.md\`)을 이 폴더에 추가한다. 형식은 \`docs/issues/$cur/\` 와 같다.
MD
printf '%s\n' "$new" > "$root/docs/policies/CURRENT"
python3 "$root/scripts/sync-policy.py" "$new"

echo
echo "새 버전 $new 를 시작했습니다. ($cur 는 동결)"
echo "다음 작업:"
echo "  - docs/policies/$new/policy.md 에 $new 정책을 반영한 뒤 /sync-docs"
echo "  - docs/policies/README.md 에 $new 모듈 표 추가"
echo "  - docs/issues/$new/ 에 스토리 작성 (Jira Fix Version: $new)"
