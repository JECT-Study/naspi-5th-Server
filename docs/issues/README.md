# 이슈 초안

Jira 등록 전 epic·story·task 초안. 등록 후에는 각 스토리의 `Jira:` 칸에 이슈 키(예: `ND-12`)를 기입하고, 이후 상태·담당자 관리는 Jira를 기준으로 한다.

## Story와 Task

| | Story | Task |
|---|---|---|
| 내용 | 사용자가 할 수 있는 일 (정책 기반) | 기술 작업 (CI/CD, 배포, 개발 도구, 공통 코드) |
| 위치 | `<버전>/` (예: [v1/](v1/)) | 정책과 무관: [tasks/](tasks/) · v1 공통 코드: [v1/global.md](v1/global.md) |
| 제목 | "회원은 ~할 수 있다" | "~ 구성", "~ 추가" |
| 근거 | 정책서 섹션 링크 | 목적 한 줄 |
| 완료 조건 | 정책 규칙 → 테스트로 검증 | 확인 방법(명령·경로)을 함께 적는다 |
| Jira 유형 | Story | Task |

- `tasks/`는 정책 버전에 묶이지 않는다 (v2에서도 그대로 쓴다).
- `v1/global.md`(GLB)는 v1 구현에 필요한 애플리케이션 공통 코드라 버전 폴더에 두지만, Jira 유형은 Task로 등록한다.

## 규칙

- epic = 도메인 모듈, 버전(v1, v2)은 Jira **Fix Version**으로 구분한다.
- story 하나 = 유스케이스(API 또는 이벤트 처리) 하나. 설명에 정책서 링크를 넣는다.
- 여러 도메인에 걸친 기능은 시작 도메인에 두고, 후속 처리는 해당 도메인 스토리로 분리해 선행 관계로 연결한다.
- `결정 필요` 항목은 구현 전에 정책서에 반영한다.
- 버전별 폴더(`v1/`, `v2/` ...)로 나눈다. 현재 구현 대상 버전은 [`docs/policies/CURRENT`](../policies/CURRENT)를 따른다.

## 작업 흐름

| 단계 | 할 일 | 도구 |
|---|---|---|
| 1. 브랜치 | `{타입}/{JIRA-KEY}` 브랜치 생성 (예: `feature/ND-12`, `fix/ND-12`) | |
| 2. 기재 | 작업할 스토리·Task의 `Jira:` 칸에 키 기입 (예: `- Jira: ND-12`) | 커밋 전 훅이 미기재 시 커밋 차단 (`scripts/check-issue-doc.sh`) |
| 3. 조회 | 스토리·Task 내용과 완료 조건 확인 | `scripts/find-story.sh ND-12` (ID `MBR-03`, `COM-01`도 가능) |
| 4. 구현 | 완료 조건과 링크된 정책을 기준으로 구현·테스트 | |
| 5. 검증 | 완료 조건별 구현·테스트 여부 점검 (PR 전) | **`/story`** (아래 참고) |

### `/story` — 완료 조건 검증 (Story·Task)

```
/story ND-12      # Jira 키로
/story MBR-03     # 스토리 ID로
/story            # 현재 브랜치({타입}/{JIRA-KEY})의 키로
```

- 완료 조건과 링크된 정책 규칙마다 구현 코드(`파일:줄`)와 테스트를 찾고, 관련 테스트를 실행한다.
- 판정: ✅ 충족 · 🟡 테스트 없음 · ❌ 미구현 · ❓ 판단 불가(정책 모호·결정 필요)
- Task는 정책 단계를 건너뛰고, 완료 조건에 적힌 확인 방법(읽기 전용 검사 명령, 경로)으로 확인한다.
- 검증과 보고만 하며 코드·문서를 수정하지 않는다. 모두 ✅이면 체크박스를 `[x]`로 바꿀지 묻는다.

### 정책이 바뀌면 — `/sync-docs`

정책은 `docs/policies/<버전>/policy.md`에서만 고친다. 고친 뒤 `/sync-docs`를 실행하면 domain 정책 파일을 다시 생성하고, 이 폴더의 스토리와 ERD에 필요한 수정안을 제시한다. 새 버전 시작은 `/sync-docs new v2`.

## Task (버전 무관)

| Epic | 파일 | Task 수 |
|---|---|---|
| [COMMON] 개발 환경·운영 기반 | [tasks/common.md](tasks/common.md) | 1 |

## v1 Epic

| Epic | 파일 | 스토리 수 |
|---|---|---|
| [기반] 공통·인프라 (Jira 유형: Task) | [v1/global.md](v1/global.md) | 8 |
| [회원] | [v1/member.md](v1/member.md) | 6 |
| [리그] | [v1/league.md](v1/league.md) | 16 |
| [시장] | [v1/market.md](v1/market.md) | 9 |
| [자산] | [v1/asset.md](v1/asset.md) | 7 |
| [주문] | [v1/order.md](v1/order.md) | 12 |
| [콘텐츠] | [v1/contents.md](v1/contents.md) | 10 |

## 권장 구현 순서

```
1. 기반      GLB-01 ~ GLB-04
2. 회원      MBR-01 ~ MBR-05
3. 대기실    LEA-01 ~ LEA-05, MKT-01, MKT-02, LEA-06 ~ LEA-08, LEA-16
4. 시작      LEA-09, LEA-10, MKT-03, MKT-04, AST-01
5. 거래      AST-03 ~ AST-06, ORD-01 ~ ORD-07, MKT-05, MKT-08, AST-02, ORD-08, ORD-12
6. 거래일    LEA-11, MKT-06, ORD-09
7. 종료      LEA-12, ORD-10, MKT-07, LEA-13, LEA-14
8. 탈퇴      MBR-06, ORD-11, AST-07, MKT-09, LEA-15
9. 병렬 가능 GLB-05 ~ GLB-08, CNT-01 ~ CNT-10 (콘텐츠는 3단계 이후 착수 가능)
```
