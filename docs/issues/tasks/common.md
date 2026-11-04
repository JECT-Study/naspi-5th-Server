# [COMMON] 개발 환경·운영 기반 (Task)

> Jira epic: COMMON · 이슈 유형: **Task**
> 기능 개발에 필요한 CI/CD, 배포, 개발 도구 작업. 정책서와 무관하고 정책 버전(v1, v2 ...)에도 묶이지 않는다.
> 애플리케이션 공통 코드(예외, 보안, Clock 등)는 [v1/global.md](../v1/global.md)에 둔다.

## 작성 형식

```
### COM-NN <작업 이름>          ← "~ 구성", "~ 추가" 같은 작업명
- Jira: ND-NN
- 목적: 왜 필요한지 한 줄
- 작업 내용: 무엇을 하는지
- 완료 조건 (확인 방법)
  - [ ] <조건> — `<확인 명령 또는 확인 방법>`
```

---

### COM-01 개발 하네스 구성
- Jira: ND-46
- 목적: 정책서·이슈·ERD를 기준으로 사람과 Claude가 같은 방식으로 기능을 개발하고 검증할 수 있게 한다
- 작업 내용: 정책서 구조화, 이슈 초안, ERD, Claude Code 하네스(CLAUDE.md, 훅, 스킬)와 검증 스크립트 구성
- 완료 조건 (확인 방법)
  - [x] 정책서 원본(`docs/policies/<버전>/policy.md`)과 도메인별 파일이 동기화된다 — `python3 scripts/sync-policy.py --check`
  - [x] 도메인 epic별 이슈 초안이 있다 — `docs/issues/v1/`
  - [x] ERD 도메인별 페이지가 서로 일관된다 — `python3 scripts/erd-summary.py --check`
  - [x] CLAUDE.md에 기술 스택, 모듈 구조, 코드·Git 규칙이 있다 — `CLAUDE.md`
  - [x] 커밋 전 훅이 이슈 기재, 문서 동기화·ERD 일관성, checkstyle을 검사한다 — `.claude/hooks/pre-commit-check.sh`
  - [x] 스킬 `/story`(완료 조건 검증), `/sync-docs`(정책 동기화·새 버전 시작)가 있다 — `.claude/skills/`
  - [x] 정책 버전을 관리한다 — `scripts/policy-version.sh`, `scripts/new-policy-version.sh`
