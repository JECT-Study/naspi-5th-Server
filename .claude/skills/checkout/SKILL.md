---
name: checkout
description: 작업 브랜치로 체크아웃하고 development 기준 브랜치의 최신 내용을 pull 받는다. 브랜치가 없으면 기준 브랜치에서 새로 만든다. "/checkout feature/ND-30", "ND-30 브랜치로 체크아웃" 요청 시 사용.
argument-hint: "{타입}/{JIRA-KEY}[,{JIRA-KEY}...]"
allowed-tools: Bash(git fetch:*), Bash(git status:*), Bash(git branch:*), Bash(git checkout:*), Bash(git switch:*), Bash(git pull:*), Bash(git log:*), Bash(git rev-parse:*), Bash(git show-ref:*), Bash(git ls-remote:*), Bash(git rev-list:*)
---

# 브랜치 체크아웃 + 기준 브랜치 pull

대상 브랜치: `$ARGUMENTS`

이 스킬은 브랜치 전환과 pull만 한다. **push, stash, reset, 강제 체크아웃(`-f`), 커밋은 하지 않는다.**

## 1. 인자 확인

- 비어 있으면 브랜치 이름을 물어본다.
- 형식은 `{타입}/{JIRA-KEY}[,{JIRA-KEY}...]` (예: `feature/ND-30`, `fix/ND-12`, `feature/ND-8,ND-28`) 또는 키 없는 `{타입}/{이름}` (예: `feature/json-logging`).
- 타입은 `feature`, `fix`, `refactor` 중 하나다. 오타로 보이면(예: `feautre`) 고친 이름을 제안하고 확인받은 뒤 진행한다. 임의로 고쳐서 진행하지 않는다.

## 2. 사전 점검

1. `git status --porcelain`으로 커밋하지 않은 변경이 있는지 확인한다. 있으면 변경 파일 목록을 보여주고 어떻게 할지 묻고 멈춘다 (임의로 stash하거나 버리지 않는다).
2. `git fetch origin --prune`
3. 기준 브랜치를 정한다: `git branch -r --list 'origin/development/*'`
   - 하나면 그것을 쓴다 (현재 `development/1.0.0`).
   - 여러 개면 어떤 것을 기준으로 할지 묻는다.
   - 없으면 보고하고 멈춘다.

이하 기준 브랜치를 `<base>`(예: `development/1.0.0`), 대상 브랜치를 `<branch>`로 쓴다.

## 3. 체크아웃

브랜치 존재 여부를 확인한다:
- 로컬: `git show-ref --verify --quiet refs/heads/<branch>`
- 원격: `git show-ref --verify --quiet refs/remotes/origin/<branch>`

| 상황 | 동작 |
|---|---|
| 로컬에 있음 | `git checkout <branch>` |
| 원격에만 있음 | `git checkout -b <branch> --track origin/<branch>` |
| 둘 다 없음 | `git checkout -b <branch> --no-track origin/<base>` (기준 브랜치를 upstream으로 추적하지 않게 한다. development로 잘못 push되는 것을 막기 위함) |

## 4. 기준 브랜치 pull

`git pull origin <base>`

- 새로 만든 브랜치는 이미 최신이므로 결과가 "Already up to date"여도 정상이다.
- 충돌이 나면 충돌 파일 목록(`git status`)을 보여주고 멈춘다. 임의로 해결하거나 abort하지 않고 사용자에게 어떻게 할지 묻는다.
- 원격에 같은 이름의 브랜치가 있고 로컬이 뒤처져 있으면(`git rev-list --count <branch>..origin/<branch>` > 0) 그 사실도 보고한다. 자동으로 pull하지 않는다.

## 5. 보고

간결하게 알린다:
- 체크아웃한 브랜치와 새로 만들었는지 여부
- 기준 브랜치와 pull 결과 (들어온 커밋 수 또는 최신 상태)
- 현재 HEAD (`git log --oneline -1`)
- 새로 만든 브랜치면: 처음 push할 때 `git push -u origin <branch>`로 연결하면 된다는 안내
