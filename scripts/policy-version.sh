#!/usr/bin/env bash
# 현재 구현 대상 정책 버전을 출력한다 (docs/policies/CURRENT).
# 사용법: scripts/policy-version.sh          → v1
#         scripts/policy-version.sh --dir    → docs/policies/v1 (저장소 루트 기준 경로)
set -euo pipefail
root=$(git rev-parse --show-toplevel)
f="$root/docs/policies/CURRENT"
[[ -f "$f" ]] || { echo "docs/policies/CURRENT 파일이 없습니다." >&2; exit 1; }
v=$(tr -d '[:space:]' < "$f")
[[ "$v" =~ ^v[0-9]+$ ]] || { echo "CURRENT 값이 버전 형식(v1, v2 ...)이 아닙니다: '$v'" >&2; exit 1; }
[[ -f "$root/docs/policies/$v/policy.md" ]] || { echo "docs/policies/$v/policy.md 가 없습니다." >&2; exit 1; }
if [[ "${1:-}" == "--dir" ]]; then echo "docs/policies/$v"; else echo "$v"; fi
