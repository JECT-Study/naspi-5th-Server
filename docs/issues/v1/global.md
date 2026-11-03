# [기반] 공통·인프라

> 모듈: `global` · 다른 모든 epic의 선행 작업 · Jira 유형: **Task** (v1 구현에 필요한 애플리케이션 공통 코드)

### GLB-01 클라이언트는 모든 API에서 일관된 형식의 오류 응답을 받는다
- Jira:
- 내용: 에러 코드 enum, 비즈니스 예외 클래스, `@RestControllerAdvice` 전역 예외 핸들러, Bean Validation 실패 응답 형식 통일
- 완료 조건
  - [ ] 에러 응답 포맷(코드, 메시지, 필드 오류) 정의 및 문서화
  - [ ] 검증 실패, 비즈니스 예외, 미처리 예외별 HTTP 상태 매핑 테스트

### GLB-02 비회원은 공개 API 외에는 접근할 수 없고, 회원은 인증 정보로 본인을 식별받는다
- Jira:
- 정책: [member.md#로그인-회원가입](../../policies/v1/domain/member.md#로그인-회원가입) — 서비스는 회원만 이용 가능
- 내용: Spring Session + Redis 기반 세션 인증, 인증 예외(401/403) 응답, 현재 회원 조회용 리졸버
- 완료 조건
  - [ ] 로그인 시 세션이 Redis에 저장되고, 서버 여러 대에서 같은 세션 공유
  - [ ] 비인증 요청은 로그인/약관 등 공개 API 외 모두 401
  - [ ] 컨트롤러에서 현재 회원 ID를 주입받을 수 있음
- 결정 필요: 세션 만료 시간, 세션 쿠키 설정(SameSite, Secure), CSRF 처리 방식

### GLB-03 시스템은 모든 시간 계산을 KST 기준의 주입 가능한 Clock으로 처리한다
- Jira:
- 내용: `Clock` 빈(Asia/Seoul) 등록, 테스트용 고정 Clock 지원
- 완료 조건
  - [ ] 도메인/서비스 코드가 `LocalDateTime.now()` 대신 주입된 `Clock` 사용
  - [ ] 테스트에서 시각 고정 가능

### GLB-04 각 모듈은 도메인 이벤트로 다른 모듈에 변경 사항을 알린다
- Jira:
- 내용: Spring 이벤트 발행/구독 규칙 정립 (트랜잭션 커밋 후 처리, 비동기 처리용 executor)
- 완료 조건
  - [ ] `@TransactionalEventListener` 사용 기준 문서화
  - [ ] 비동기 리스너 실패 시 로그 및 재시도 정책 정의

### GLB-05 사용자는 JPEG/PNG 이미지를 업로드할 수 있다
- Jira:
- 정책: [contents.md#공시](../../policies/v1/domain/contents.md#공시) — JPEG(.jpg/.jpeg), PNG
- 내용: 이미지 업로드/조회 추상화, 확장자·MIME 검증
- 완료 조건
  - [ ] JPEG/PNG 외 형식 거절
  - [ ] 로컬은 파일 시스템, 운영은 외부 저장소로 교체 가능한 구조
- 결정 필요: 저장소(S3, NCP Object Storage 등), 파일 크기 제한

### GLB-06 시스템은 AI 모델을 호출해 텍스트를 생성할 수 있다
- Jira:
- 정책: [contents.md#뉴스---공시-기반-ai-뉴스](../../policies/v1/domain/contents.md#뉴스---공시-기반-ai-뉴스)
- 내용: LLM API 호출 클라이언트, 타임아웃·실패 예외 구분
- 완료 조건
  - [ ] 프롬프트/응답 파싱과 HTTP 호출 분리
  - [ ] 테스트용 Fake 클라이언트 제공
- 결정 필요: AI 제공자 및 모델

### GLB-07 개발자는 모듈 의존 방향을 어기면 테스트 실패로 바로 알 수 있다
- Jira:
- 내용: ArchUnit 또는 Spring Modulith로 모듈 의존 방향 위반 시 테스트 실패
- 완료 조건
  - [ ] `CLAUDE.md`의 의존 방향 규칙을 테스트로 강제

### GLB-08 개발자는 DB 스키마 변경 이력을 버전으로 관리할 수 있다
- Jira:
- 내용: Flyway 등 마이그레이션 도구 도입, 로컬 H2 / 운영 PostgreSQL 호환
- 결정 필요: `ddl-auto` 사용 범위
