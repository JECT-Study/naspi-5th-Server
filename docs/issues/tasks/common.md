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

### COM-02 웹 요청 로깅 추가
- Jira: ND-64
- 목적: 운영 중 요청 흐름과 오류 원인을 로그로 추적할 수 있게 한다
- 작업 내용: 트레이스 시작 필터 다음, 다른 필터보다 먼저 실행되는 요청 로그 필터를 등록해 요청 메서드·URI·본문을 남긴다
- 완료 조건 (확인 방법)
  - [ ] 인증 실패 등으로 막힌 요청도 로그에 남는다 — `./gradlew test --tests "kr.ject.naspi.global.logging.*"`

### COM-03 하위 모듈 참조용 UseCase 규칙 추가
- Jira:
- 목적: 하위 모듈을 참조해야 하는 예외적인 경우를 Service와 구분해, 의존 방향을 지키면서도 하위 모듈을 참조할 수 있게 한다
- 작업 내용: `application` 계층의 Service는 상위 모듈만 참조하고, 하위 모듈을 참조해야 하는 경우는 UseCase로 작성하도록 규칙 추가
- 완료 조건 (확인 방법)
  - [x] `application`의 Service는 상위 모듈만 참조할 수 있다 — `CLAUDE.md` "경계 규칙", `docs/policies/README.md` "모듈 간 의존 방향"
  - [x] 하위 모듈을 참조해야 하는 경우는 UseCase(`{module}/application/XxxUseCase`)로 작성하며, UseCase는 상위·하위 모듈을 모두 참조할 수 있다 — `CLAUDE.md` "경계 규칙", `docs/policies/README.md` "모듈 간 의존 방향"

### COM-04 관측성(헬스체크·메트릭·트레이스) 추가
- Jira: ND-70
- 목적: GKE 에서 Grafana Alloy 가 수집해 Grafana Cloud 로 보낼 수 있도록 헬스체크·메트릭·트레이스를 노출한다
- 작업 내용: actuator 를 management 포트(9090)로 분리하고 health·prometheus 만 노출, 메인 포트에 /livez·/readyz 추가, OTLP/HTTP 트레이스 export
- 완료 조건 (확인 방법)
  - [ ] 메인 포트(8080)에서 `/livez`, `/readyz` 가 인증 없이 200 을 반환한다 — `curl localhost:8080/livez`
  - [ ] actuator 엔드포인트는 8080 으로 노출되지 않고 9090 에서만 접근된다 — `curl localhost:9090/actuator/prometheus`
  - [ ] readiness 에 DB·Redis 상태가 포함된다 — `curl localhost:9090/actuator/health/readiness`
  - [ ] 모든 메트릭에 `application=naspi-server` 태그가 붙는다 — `curl localhost:9090/actuator/prometheus`
  - [ ] JSON 로그에 traceId, spanId 가 포함된다 — 요청 후 로그 확인
  - [ ] `/actuator/**`, `/livez`, `/readyz` 요청은 트레이스에서 제외된다 — `./gradlew test`
  - [ ] DB 쿼리가 JDBC 스팬으로 남는다 — `./gradlew test --tests "kr.ject.naspi.global.config.ObservationConfigTest"`
  - [ ] 요청 안의 Redis 명령(세션 조회·저장)은 요청 트레이스에 스팬으로 남고, 헬스체크의 Redis 상태 확인처럼 부모 없는 Redis 스팬은 내보내지 않는다 — `./gradlew test --tests "kr.ject.naspi.global.config.ObservationConfigTest"`

### COM-05 PR 빌드 검증 워크플로 추가
- Jira: ND-79
- 목적: 머지 후 `build.yml`에서야 드러나던 컴파일·이미지 빌드 실패를 PR 단계에서 확인한다
- 작업 내용: `development/**` 대상 PR에서 컴파일(main·test), bootJar, Jib 이미지 생성(push 없이 tar)을 실행하는 `verify.yml` 추가
- 완료 조건 (확인 방법)
  - [x] PR에서 컴파일과 실행 jar 생성을 검증한다 — `.github/workflows/verify.yml`, `./gradlew assemble testClasses`
  - [x] PR에서 Docker Hub push 없이 `build.yml`과 같은 Jib 설정으로 이미지를 만든다 — `./gradlew jibBuildTar`
  - [ ] 저장소 브랜치 보호에서 `verify`를 `development/**` 필수 상태 검사로 지정한다 — GitHub Settings → Branches
