# [COMMON] 개발 환경·운영 기반

> 기능 개발 전에 필요한 CI/CD, 배포, 개발 도구 구성 작업. 특정 도메인 모듈에 속하지 않는다.
> 애플리케이션 공통 코드(예외, 보안, Clock 등)는 [global.md](global.md)에 둔다.

### COM-01 개발자는 정책서·이슈 초안·검증 도구를 기준으로 일관된 방식으로 기능을 개발할 수 있다
- Jira: ND-7
- 내용: 정책서 구조화, 이슈 초안, ERD, Claude Code 하네스(CLAUDE.md, 훅, 스킬)와 검증 스크립트 구성
- 완료 조건
  - [x] 정책서 원본(`docs/policies/v1/policy.md`)과 도메인별 파일 분리, 원본 기준 동기화 (`scripts/sync-policy.py`)
  - [x] 도메인 epic별 이슈 초안 (`docs/issues/v1/`)
  - [x] ERD 도메인별 페이지 구성 (`docs/erd/naspi.drawio`), 페이지 간 일관성 검사 (`scripts/erd-summary.py`)
  - [x] CLAUDE.md (기술 스택, 모듈 구조, 코드·Git 규칙)
  - [x] 커밋 전 검사 훅: 이슈 기재, 문서 동기화·ERD 일관성, checkstyle
  - [x] 스킬: `/story` (완료 조건 검증), `/sync-docs` (정책 동기화·새 버전 시작)
  - [x] 정책 버전 관리 (`docs/policies/CURRENT`, `scripts/new-policy-version.sh`)
