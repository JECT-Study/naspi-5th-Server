#!/usr/bin/env bash
# Claude 의 git push 직전 검사 (PreToolUse 훅). 실패하면 exit 2 로 push 를 차단하고 이유를 Claude 에게 전달한다.
#   1. 웹소켓 토픽 메시지와 REST 응답의 필드 이름·타입 일치 (TopicResponseSpecTest). 기준은 REST 응답이다.
#      실패하면 /sync-topic-spec 으로 웹소켓 DTO 를 REST DTO 에 맞춘다.
cmd=$(jq -r '.tool_input.command // empty')
printf '%s' "$cmd" | grep -qE '(^|[;&|[:space:]])git[[:space:]]+push([[:space:]]|$)' || exit 0
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

test_class=kr.ject.naspi.websocket.api.response.TopicResponseSpecTest
if ! out=$(./gradlew test --tests "$test_class" -q 2>&1); then
  echo "웹소켓 토픽 메시지와 REST 응답 스펙이 달라 push 를 차단했습니다. /sync-topic-spec 으로 웹소켓 DTO 를 REST DTO 에 맞추세요." >&2
  report="build/test-results/test/TEST-$test_class.xml"
  if [[ -f "$report" ]]; then
    # 실패 메시지
    python3 -c 'import sys, xml.etree.ElementTree as ET
for f in ET.parse(sys.argv[1]).iter("failure"):
    print(f.get("message"))' "$report" >&2
  else
    printf '%s\n' "$out" | tail -30 >&2
  fi
  exit 2
fi
exit 0
