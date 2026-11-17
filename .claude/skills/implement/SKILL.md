---
name: implement
description: 스토리(또는 Task)를 planner → executor → tester 서브에이전트 순서로 계획·구현·검증한다. 인자로 Jira 키(ND-99)나 스토리 ID를 받고, 생략하면 현재 브랜치의 키를 쓴다. "/implement ND-33", "ND-33 구현해줘" 요청 시 사용.
argument-hint: "[JIRA-KEY[,JIRA-KEY...]]"
---

# 스토리 계획 → 구현 → 검증

대상: `$ARGUMENTS` (비어 있으면 현재 브랜치 `{타입}/{JIRA-KEY}[,...]`의 키. 키가 없으면 물어본다)

커밋과 push는 이 스킬에서 하지 않는다.

## 1. 계획 (planner)

`planner` 서브에이전트에 대상 키를 넘긴다. 결과를 받으면:

- 계획 파일(`.claude/plans/<키>.md`) 요약과 `결정 필요` 항목을 사용자에게 보여준다.
- `결정 필요`가 있으면 AskUserQuestion으로 묻는다. 정책서 변경이 필요한 결정이면 구현보다 `policy.md` 수정을 먼저 제안하고 멈춘다.
- **사용자가 계획을 승인해야 2단계로 간다.** 수정 요청이 있으면 planner를 다시 부르거나(SendMessage) 계획 파일을 직접 고친다.

## 2. 구현 (executor)

`executor` 서브에이전트에 다음을 넘긴다:
- 계획 파일 경로
- 사용자가 정한 `결정 필요` 답

executor가 멈추고 보고하면(계획 오류, 반복 실패, 라이브러리 필요) 사용자에게 전달하고 멈춘다.

## 3. 검증 (tester)

`tester` 서브에이전트에 **대상 키만** 넘긴다. 계획 파일이나 executor 보고를 넘기지 않는다 (계획에 끌려가지 않는 검증을 위해).

- 모두 ✅이고 경계 위반이 없으면 4단계로 간다.
- `수정 필요`가 있으면 그 목록을 executor에게 넘겨 고치게 하고(SendMessage로 이어서) tester를 다시 부른다. **최대 2회** 반복한다. 그래도 남으면 남은 항목을 보고하고 멈춘다.
- ❓(판단 불가) 항목은 수정 대상이 아니라 사용자에게 묻는다.
- 스토리 모듈에서 구현이 끝났다면 다른 모듈의 미연결(호출처 없음 등)은 문제로 다루지 않는다. tester가 그런 항목을 ❌·🟡로 냈더라도 executor로 되돌리지 않고, 보고에서 "다른 모듈 연결 (참고)"로 한 줄만 적는다.

명령 권한: `cd`, `git`(push·reset·stash 제외), `./gradlew checkstyle*`, `./gradlew test`, `scripts/find-story.sh`는 사용자에게 묻지 않고 실행한다 (`.claude/settings.local.json`에 허용됨).

## 4. 보고

- 변경 파일 목록과 `git diff --stat`
- tester 최종 리포트 요약 (항목별 판정)
- checkstyle·테스트 결과
- 모든 항목이 ✅이면 스토리 완료 조건 체크박스를 `[x]`로 바꿀지, 커밋할지 묻는다 (동의할 때만 수행)
