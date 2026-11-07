# naspi

친구끼리 리그(방)를 만들어 각자 자신을 종목으로 상장하고, 7일간 가상 주식을 거래해 총자산 순위를 겨루는 서비스의 백엔드.

## 기술 스택

| 영역 | 기술 | 비고 |
|---|---|---|
| 언어/프레임워크 | Java 21, Spring Boot 4.0, Spring Web MVC | |
| 빌드 | Gradle | |
| 영속성 | Spring Data JPA, PostgreSQL | 로컬/테스트는 H2 |
| 인증 | Spring Security, 카카오 OAuth | |
| 세션 | Spring Session + Redis | 서버 무상태 유지, 세션은 Redis에 저장 (JWT 사용 안 함) |
| 검증 | Bean Validation | |
| API 문서 | springdoc-openapi (Swagger UI) 3.0.x | `/swagger-ui/index.html` |
| 코드 스타일 | Checkstyle (Google Java Style, 4칸 들여쓰기), Lombok | |
| 로깅 | Logback, Logstash JSON 포맷 | `development`, `production` 프로필에서만 JSON |
| 테스트 | JUnit 5, Spring Boot Test | |
| 배포 | Jib → Docker Hub → k8s (GitOps manifest 레포) | GitHub Actions |

미정: AI 제공자(뉴스 생성), 이미지 저장소, DB 마이그레이션 도구. 새 라이브러리 도입이나 위 스택 변경은 먼저 제안하고 승인받는다.

## 명령어

- 빌드: `./gradlew build`
- 테스트: `./gradlew test` / 단일: `./gradlew test --tests "kr.ject.naspi.order.SomeTest"`
- 린트: `./gradlew checkstyleMain checkstyleTest`
  - Google Java Style + 4칸 들여쓰기 (`config/checkstyle/google-checks-4space.xml`)
  - `maxWarnings = 0`: 경고 1개도 실패
- 로컬 이미지: `./gradlew jibDockerBuild`

작업을 마치기 전에 checkstyle과 관련 테스트가 통과하는지 확인한다. PR CI(`check.yml`, `test.yml`)가 같은 명령을 실행한다.

## 정책서 (필수)

- 현재 구현 대상 버전은 `docs/policies/CURRENT`에 적혀 있다 (`scripts/policy-version.sh`). 이하 `<ver>`.
- 기능 구현 전에 [docs/policies/README.md](docs/policies/README.md)와 해당 모듈의 `docs/policies/<ver>/domain/{모듈}.md`를 읽는다.
- 정책 원본은 `docs/policies/<ver>/policy.md` 하나다. 이전 버전 폴더는 동결되어 수정하지 않는다. `domain/*.md`는 여기서 생성되므로 직접 수정하지 않는다. 정책을 바꾸면 `policy.md`를 고친다. 커밋할 때 훅이 domain 파일을 자동 생성하고, 이슈 초안·ERD 영향 분석이 필요하면 커밋을 멈춘다 → `/sync-docs` 3~7단계 수행 후 `scripts/mark-docs-reviewed.sh`로 기록하고 다시 커밋한다.
- `docs/policies/roadmap.md`는 현재 버전 이후 참고용이므로 구현하지 않는다 (매매봇, 일일 요약 뉴스 등). 새 버전 시작은 `/sync-docs new v<N>`.
- 정책에 없거나 모호한 결정은 임의로 정하지 말고 질문한다.
- 정책과 코드가 어긋나면 정책서가 기준이다. 정책 변경이 필요하면 코드보다 문서를 먼저 고치자고 제안한다.

## 모듈 구조

패키지 기준 모듈러 모놀리스. 루트 패키지 `kr.ject.naspi`.

| 모듈 | 책임 |
|---|---|
| `member` | 카카오 로그인/회원가입, 회원 정보, 탈퇴 |
| `league` | 리그·대기실·참여자, 시작/종료, 거래일 전환 스케줄, 리그 결과 |
| `market` | 종목(상장), 시세(현재가·기준가·종가·거래량·차트), 가격제한폭 |
| `asset` | 현금·보유 주식, 예약/해제/정산, 초기 자산 배분, 자사주 최소 유지분 |
| `order` | 주문 접수, 호가창, 매칭·체결, 미체결 만료/취소 |
| `contents` | 공시, 공시 기반 AI 뉴스, 댓글, 호재/악재 반응 |
| `websocket` | 실시간 메시지 발행 (STOMP `/ws`, `/topic/**`). 도메인 이벤트·주기 조회 결과를 토픽으로 발행 |
| `global` | 애플리케이션 전역 처리: 예외 (`ErrorCode`, `BusinessException`, `GlobalExceptionHandler`) |
| `common` | 여러 모듈이 공통으로 쓰는 코드: 응답 형식 (`CommonResponse`), 보안 설정 (`SecurityConfig`) |

모듈 내부 구조:
```
{module}/
├── api/          Controller (웹 진입점)
│   ├── request/  Request DTO (record)
│   └── response/ Response DTO (record)
├── application/  Service (유스케이스, 트랜잭션 경계)
├── domain/       Entity, 도메인 규칙
└── infra/        Repository, 외부 연동
```

### 경계 규칙

- 의존 방향: `member ← league ← market ← asset ← order`, `contents → league, market`. 반대 방향 import 금지.
- `websocket`은 최상위 소비자다 (`websocket → 도메인 모듈`). 어떤 모듈도 `websocket`을 import하지 않는다. 도메인 이벤트를 듣고 각 모듈 `application`으로 조회해 발행한다. 내부 구조는 다른 모듈과 같은 `api/application/domain/infra`이고, 토픽별 구현은 `application/topic/{도메인}/`에 둔다. 새 토픽은 기존 코드 수정 없이 리스너·`TopicSubscriptionAuthorizer` 빈 추가로 만든다. 현재 내장 브로커라 단일 서버 전제이며, 다중 서버 시 `TopicMessagePublisher`를 Redis Pub/Sub 구현으로 교체한다.
- 다른 모듈의 `domain`/`infra`를 직접 참조하지 않고 해당 모듈의 `application` 서비스를 통해 접근한다.
- 역방향 흐름은 Spring 이벤트로 처리한다 (체결, 거래일 전환, 공시 게시, 회원 탈퇴, 리그 종료). 목록은 정책서 README 참고.
- 자산(현금·주식)의 소유자는 `Member`가 아니라 리그 참여자(`Participant`)다. 리그마다 자산이 분리되며, v2의 봇도 참여자로 추가된다.
- `global`과 `common`에는 도메인 로직을 두지 않는다. 여러 모듈이 쓰는 도메인 개념은 소유 모듈에 두고 `application`을 통해 노출한다 (예: 거래일 계산은 `league`, 가격·호가 단위는 `market`).
- 새 공통 코드를 어디에 둘지 애매하면 임의로 정하지 말고 질문한다.
- 버전별 패키지(`v1`, `v2`)를 만들지 않는다.

## 도메인 용어

| 용어 | 의미 |
|---|---|
| 리그 | 방. 3~10명이 참여하는 7일간의 독립된 시장 |
| 방장 / 참여자 / 대기자 | 생성자 / 상장을 마친 사용자(방장 포함) / 입장했지만 상장 전인 사용자 |
| 상장자 | 해당 종목을 상장한 참여자. 자사주 = 상장자가 보유한 자기 종목 |
| 거래일 | 매일 09:00 KST ~ 다음 날 09:00 KST 미만 |
| 시초가 / 기준가 / 종가 | 10,000원 / 직전 거래일 종가 / 거래일 마지막 체결가 |
| 예약 자산 | 미체결 주문에 묶인 현금·주식. 주문 가능 자산 계산에서 제외 |

## 코드 규칙

- 금액·수량은 `long`(원, 주 단위) 사용. 부동소수점 금지. 절사·반올림 규칙은 정책서의 계산식을 그대로 따른다.
- 시간은 KST 기준 정책이 많으므로 `Clock`을 주입받아 사용한다 (테스트에서 시각 고정).
- 엔티티를 API 응답으로 직접 반환하지 않고 DTO(record)로 변환한다.
- 정책 규칙을 검증하는 테스트는 `@DisplayName`에 정책 내용을 한글로 적는다.

## Git

- 브랜치: `feature/*`, `fix/*` → `development/{버전}`으로 PR. CI는 `development/**` 대상 PR에서 실행된다.
- 이슈는 Story(정책 기반 사용자 기능, `docs/issues/<ver>/`)와 Task(기술 작업, 정책 무관은 `docs/issues/tasks/`)로 나눈다. 형식은 `docs/issues/README.md` 참고.
- Jira 이슈 작업 브랜치는 `{타입}/{JIRA-KEY}` (예: `feature/ND-12`, `fix/ND-12`, `refactor/ND-12`). 작업하는 Story·Task의 `Jira:` 칸에 해당 키를 기재한다.
  - Jira 키가 없는 브랜치(예: `feature/json-logging`)는 기재 대상이 아니다.
  - 검증: `scripts/check-issue-doc.sh` (커밋 전 훅에서 자동 실행, 미기재 시 커밋 차단). 키를 기재할 스토리가 불분명하면 질문한다.
  - PR 전에 `/story`로 완료 조건 구현·테스트 여부를 점검한다. 스토리 조회는 `scripts/find-story.sh <스토리 ID | Jira 키>`.
- **커밋 전에 항상 `./gradlew checkstyleMain checkstyleTest`를 실행하고, 통과하지 않으면 커밋하지 않는다.**
- 커밋 메시지는 한국어로 간결하게 작성한다 (예: `주문 체결 로직 추가`).
- 요청 없이 push하지 않는다.

## 하지 말 것

- `gradle.properties`, `.env*` 등 자격 증명 파일 읽기·출력·커밋 금지. `application.yaml`의 접속 정보는 환경 변수 플레이스홀더로만 둔다.
- `./gradlew jib` (Docker Hub push) 실행 금지. CI가 담당한다.
- `.github/workflows/`, `build.gradle`의 jib 설정은 요청이 있을 때만 수정한다.
