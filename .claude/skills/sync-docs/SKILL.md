---
name: sync-docs
description: 정책서 원본(docs/policies/<현재 버전>/policy.md)이 바뀌었을 때 domain 정책 파일, 이슈 초안, ERD(docs/erd/naspi.drawio)를 원본 기준으로 동기화한다. 새 정책 버전(v2 등) 시작도 담당한다. "정책 바뀌었어", "문서 싱크", "ERD 반영", "v2 시작" 요청이나 policy.md 수정 직후 사용.
argument-hint: "[--check | new <버전>] (생략하면 동기화 실행)"
allowed-tools: Read, Edit, Write, Grep, Glob, Bash(scripts/mark-docs-reviewed.sh:*), Bash(git add:*), Bash(scripts/policy-version.sh:*), Bash(scripts/new-policy-version.sh:*), Bash(python3 scripts/sync-policy.py:*), Bash(python3 scripts/erd-summary.py:*), Bash(scripts/erd-render.sh:*), Bash(scripts/find-story.sh:*), Bash(git diff:*), Bash(git status:*), Bash(git log:*)
---

# 정책 문서 동기화

## 0. 대상 버전 확인

```
scripts/policy-version.sh        # 예: v1
```

이하 `<ver>`는 이 값이다. 동기화 대상은 **현재 버전 하나**이며, 이전 버전 폴더는 동결되어 수정하지 않는다.
기준(원본)은 **`docs/policies/<ver>/policy.md` 하나**다. 다른 문서는 여기에 맞춘다.

| 대상 | 방식 |
|---|---|
| `docs/policies/<ver>/domain/*.md` | `scripts/sync-policy.py`가 **자동 생성** (직접 수정 금지) |
| `docs/issues/<ver>/*.md` | 영향받는 스토리를 찾아 **수정안 제시 → 승인 후 반영** |
| `docs/erd/naspi.drawio` | 영향받는 엔티티·관계를 찾아 **수정안 제시 → 승인 후 반영** |

> **자동 실행**: `policy.md`나 `roadmap.md`를 커밋하려 하면 커밋 전 훅이 domain 파일을 자동 생성·스테이징하고, 영향 분석이 끝나지 않았으면 커밋을 멈춘다. 훅 메시지를 받으면 이 스킬의 3~7단계를 수행한다.

인자 `$ARGUMENTS`에 `--check`가 있으면 1~2단계의 확인만 하고 아무것도 수정하지 않는다.
인자가 `new <버전>`이면 맨 아래 "새 버전 시작"만 수행한다.

## 1. 무엇이 바뀌었나

```
git diff HEAD -- docs/policies/<ver>/policy.md
git log -3 --format='%h %s' -- docs/policies/<ver>/policy.md
```

- 작업 트리 변경이 없으면 마지막 커밋의 변경(`git diff HEAD~1 -- ...`)을 본다.
- 바뀐 내용을 **규칙 단위**로 정리한다 (예: "종목 소개 최대 50자 → 60자", "5.7 거래 수수료 섹션 신설").

## 2. domain 파일 동기화

```
python3 scripts/sync-policy.py --check <ver>
python3 scripts/sync-policy.py <ver>
```

- 종료 코드 2(매핑 오류)이면 출력된 섹션을 보고 `docs/policies/<ver>/domain-map.json`을 고친다.
  - 새 섹션: 어느 모듈(member, league, market, asset, order, contents)에 넣을지 **사용자에게 묻는다**. 제안과 이유를 함께 제시한다.
  - 제목 변경: 기존 매핑의 제목만 새 제목으로 바꾼다.
  - 삭제: 매핑에서 제거한다.
- 다시 실행해 `동기화됨`이 될 때까지 반복한다.
- `domain/*.md`를 손으로 고치지 않는다. 바꿔야 할 내용이 있으면 `policy.md`를 고친다.

## 3. 이슈 초안 영향 분석

1단계에서 정리한 규칙 변경마다 `docs/issues/<ver>/*.md`에서 관련 스토리를 찾는다 (정책 링크의 섹션, 완료 조건 문구, 키워드로 검색).

스토리별로 다음을 판단해 수정안을 만든다.
- 완료 조건 문구가 바뀐 규칙과 다르면 → 문구 수정
- 새 규칙이 어느 완료 조건에도 없으면 → 완료 조건 추가, 또는 새 스토리 제안 (ID는 해당 파일의 마지막 번호 다음)
- `결정 필요` 항목이 이번 변경으로 결정됐으면 → 해당 항목 제거
- 이미 `- [x]`로 체크된 조건이 바뀌면 → `- [ ]`로 되돌리고 "정책 변경으로 재확인 필요"라고 알린다

## 4. ERD 영향 분석

```
python3 scripts/erd-summary.py --check
python3 scripts/erd-summary.py --table <관련 테이블>
```

규칙 변경이 데이터 구조에 영향을 주는지 판단한다. **영향이 있는 것만** 수정안을 만든다.

| 영향 있음 (ERD 수정) | 영향 없음 (ERD 그대로) |
|---|---|
| 새 엔티티, 엔티티 간 관계·카디널리티 변경 | 글자 수 제한, 계산식, 시간 기준 같은 값 규칙 |
| 저장해야 할 새 상태나 값 (예: 수수료 금액) | 화면 표시 방식 |
| 유니크 제약이 바뀌는 규칙 (예: 1인 1종목 → 1인 2종목) | |

컬럼은 "필요할 때 추가" 방침이므로, 규칙 변경으로 **반드시 저장해야 하는 값**이 생길 때만 컬럼을 제안한다.

## 5. 수정안 확인

3, 4단계의 수정안을 한 번에 보여주고 승인받는다.

```
## 정책 변경 요약
- <규칙 변경 1>

## domain 파일 (자동 반영 완료)
- 갱신: <파일 목록>

## 이슈 초안 수정안
| 스토리 | 변경 | 내용 |

## ERD 수정안
| 페이지 | 테이블 | 변경 | 내용 |
(ERD 영향 없음이면 "영향 없음"과 이유 한 줄)
```

## 6. 승인된 수정 반영

**이슈 초안**: Edit으로 수정한다.

**ERD** (`docs/erd/naspi.drawio`, XML을 Edit으로 직접 수정):
- 같은 테이블이 **도메인 페이지와 "8. 전체 (참고)" 페이지 양쪽에 정의**되어 있다. 반드시 둘 다 고친다.
- 컬럼 추가: 같은 테이블의 기존 컬럼 셀(`id="<page>_<table>_c<n>"`)을 복사해 `id`는 다음 번호, `value`는 새 컬럼, `y`는 마지막 컬럼 `y + 22`로 둔다. 테이블(swimlane) `height`를 22 늘린다.
- 변경 표시: 새로 바뀐 컬럼 값 끝에 `★`를 붙이고 스타일에 `fontColor=#B85450;`을 넣는다 (기존 표기 규칙).
- 테이블 추가나 관계 추가처럼 배치가 필요한 변경은 빈 공간에 두고, 겹치면 사용자에게 draw.io에서 위치 조정을 요청한다.
- 다른 페이지의 테이블을 참조할 때는 PK만 있는 점선 참조 도형(제목에 `→ <페이지명>`)을 쓴다.

수정 후 검증:
```
python3 scripts/erd-summary.py --check
scripts/erd-render.sh
```
`build/erd/page-<n>.png` 중 수정한 페이지를 Read로 열어 선이 테이블을 관통하거나 도형이 겹치지 않는지 확인한다.

## 7. 마무리 보고

영향 분석과 반영(또는 영향 없음 확인)이 끝나면 수정한 파일을 `git add` 하고 검토 완료를 기록한다. 기록하지 않으면 커밋 전 훅이 다시 멈춘다.

```
scripts/mark-docs-reviewed.sh
```

보고 내용:

- 동기화된 파일, 수정한 스토리와 ERD 항목
- `python3 scripts/sync-policy.py --check`, `python3 scripts/erd-summary.py --check` 결과
- 사용자 판단이 필요해 반영하지 않은 항목

## 새 버전 시작 (`new <버전>`)

현재 버전을 복사해 새 버전을 만든다. 버전마다 정책서 **전체본**을 두며, 변경분만 따로 두지 않는다.

1. 사용자에게 확인한다: "<현재 버전>을 동결하고 <새 버전>을 시작합니다. 이후 <현재 버전> 문서는 수정하지 않습니다."
2. 실행:
   ```
   scripts/new-policy-version.sh <새 버전>
   ```
   현재 버전 폴더 복사, domain 제목 버전 변경, `docs/issues/<새 버전>/` 생성, `docs/policies/CURRENT` 변경, domain 재생성을 한다.
3. `docs/policies/README.md`에 새 버전 모듈 표를 이전 버전 표 위에 추가하고, "현재 구현 대상" 안내를 확인한다.
4. `docs/policies/roadmap.md`에서 새 버전 범위에 해당하는 내용이 있으면 사용자에게 알리고, `policy.md`로 옮길지 묻는다.
5. ERD는 하나(`docs/erd/naspi.drawio`)를 유지한다. 새 버전 정책을 `policy.md`에 반영한 뒤 일반 동기화(1~7단계)로 ERD를 고친다. 이전 버전 시점의 ERD는 Git 이력으로 확인한다.
