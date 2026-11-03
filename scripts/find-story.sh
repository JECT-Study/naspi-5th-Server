#!/usr/bin/env bash
# docs/issues 에서 스토리 섹션을 찾아 출력한다.
#
# 사용법
#   scripts/find-story.sh MBR-03     스토리 ID로 찾기
#   scripts/find-story.sh ND-99      Jira 키로 찾기 ("- Jira: ND-99" 가 적힌 스토리)
#   scripts/find-story.sh            현재 브랜치({타입}/{JIRA-KEY})의 Jira 키로 찾기
#
# 검색 범위: 현재 버전(docs/policies/CURRENT)의 docs/issues/<버전>/ 을 먼저 찾고, 없으면 다른 버전도 찾는다.
# 출력: 첫 줄에 "FILE <경로>:<줄번호>", 이어서 스토리 섹션 본문
# 종료 코드: 0 찾음, 1 못 찾음, 2 인자/브랜치로 대상을 정할 수 없음
set -euo pipefail

root=$(git rev-parse --show-toplevel)
target="${1:-}"
if [[ -z "$target" ]]; then
  branch=$(git rev-parse --abbrev-ref HEAD)
  if [[ "$branch" =~ ^[A-Za-z]+/([A-Za-z]+-[0-9]+)$ ]]; then
    target="${BASH_REMATCH[1]}"
  else
    echo "현재 브랜치($branch)에 Jira 키가 없습니다. 스토리 ID나 Jira 키를 인자로 주세요." >&2
    exit 2
  fi
fi
target=$(printf '%s' "$target" | tr '[:lower:]' '[:upper:]')

cur=$("$root/scripts/policy-version.sh" 2>/dev/null || echo "")
search() {
found=0
for f in "$@"; do
  [[ -f "$f" ]] || continue
  out=$(awk -v t="$target" -v file="${f#"$root"/}" '
    function flush() {
      if (sec != "" && (id == t || jira)) { printf "FILE %s:%d\n%s", file, start, sec; hit = 1 }
      sec = ""; id = ""; jira = 0
    }
    /^### / { flush(); start = NR; split($0, a, " "); id = toupper(a[2]); sec = $0 "\n"; next }
    /^## / || /^# / { flush(); next }
    {
      if (sec != "") {
        sec = sec $0 "\n"
        line = toupper($0)
        if (line ~ /^[[:space:]]*-[[:space:]]*JIRA:/) {
          n = split(line, w, /[^A-Z0-9-]+/)
          for (i = 1; i <= n; i++) if (w[i] == t) jira = 1
        }
      }
    }
    END { flush(); exit(hit ? 0 : 1) }
  ' "$f") && { printf '%s\n' "$out"; found=1; }
done
return 0
}
found=0
if [[ -n "$cur" ]]; then search "$root"/docs/issues/"$cur"/*.md; fi
if [[ $found -eq 0 ]]; then search "$root"/docs/issues/v*/*.md; fi

if [[ $found -eq 0 ]]; then
  echo "$target 에 해당하는 스토리를 docs/issues 에서 찾지 못했습니다." >&2
  exit 1
fi
