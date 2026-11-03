#!/usr/bin/env bash
# draw.io ERD 의 모든 페이지를 PNG 로 내보낸다 (draw.io 데스크톱 앱 필요).
# 사용법: scripts/erd-render.sh [drawio 파일] [출력 디렉터리]
#   기본값: docs/erd/naspi.drawio, build/erd
set -euo pipefail
root=$(git rev-parse --show-toplevel)
src="${1:-$root/docs/erd/naspi.drawio}"
out="${2:-$root/build/erd}"
bin=""
for c in /Applications/draw.io.app/Contents/MacOS/draw.io "$(command -v drawio || true)" "$(command -v draw.io || true)"; do
  [[ -n "$c" && -x "$c" ]] && { bin="$c"; break; }
done
[[ -z "$bin" ]] && { echo "draw.io 데스크톱 앱을 찾지 못했습니다. https://www.drawio.com 에서 설치하세요." >&2; exit 1; }
mkdir -p "$out"
n=$(grep -o '<diagram ' "$src" | wc -l | tr -d ' ')
for ((i = 0; i < n; i++)); do
  "$bin" --export --format png --scale 1 --page-index "$i" --output "$out/page-$i.png" "$src" >/dev/null 2>&1
done
ls "$out"/page-*.png
