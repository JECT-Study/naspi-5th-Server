# [콘텐츠] contents

> 정책: [docs/policies/v1/contents.md](../../policies/v1/domain/contents.md)

## 공시

### CNT-01 상장자는 자기 종목에 이미지와 함께 공시를 작성할 수 있다
- Jira: ND-14, ND-16
- 정책: [contents.md#공시](../../policies/v1/domain/contents.md#공시)
- 선행: MKT-01, GLB-05
- 내용: 상장자가 자기 종목에 공시 작성, `DisclosurePublished` 이벤트 발행
- 완료 조건
  - [x] 제목 1~30자, 본문 1~300자, 이미지 JPEG/PNG 최대 4개 (이미지는 선택)
  - [x] 작성 횟수 무제한, 수정 불가
  - [x] 작성 완료 후 `DisclosurePublished` 발행 (AI 뉴스 생성 요청, 구독은 CNT-05)
- 결정: 진행 중인 리그에서만 작성 가능 (대기실·종료 리그는 거절). 작성한 거래일을 `disclosure.trading_day`에 기록
- 결정: 등록은 `POST /api/leagues/{leagueId}/stocks/{stockId}/disclosures` (multipart). 응답은 이미지 ID만 주고 서명 URL은 공시 조회(CNT-02)에서 발급

### CNT-02 참여자는 종목별 공시 목록과 상세 내용을 볼 수 있다
- Jira: ND-15
- 선행: CNT-01
- 완료 조건
  - [x] 종목별 공시 목록, 상세 (제목, 본문, 이미지, 작성 정보, 반응 수)
  - [x] 삭제된 공시 원문은 목록·상세에서 조회 불가
  - [ ] 상세에 연결된 뉴스 (공시 삭제와 관계없이 이미 발행된 뉴스는 조회 가능. CNT-05·CNT-08 구현 시 처리)
- 결정: 리그 전체 피드는 두지 않고 종목별 목록만 제공한다
- 결정: 목록은 `GET /api/leagues/{leagueId}/stocks/{stockId}/disclosures?cursor=&size=` 최신순(ID 내림차순) 커서 페이징 (기본 20, 최대 50). 상세는 `GET .../disclosures/{disclosureId}`
- 결정: 목록에는 첫 이미지 서명 URL과 이미지 수만, 상세에는 전체 이미지 서명 URL (서명 URL 발급마다 IAM 호출이 있어 목록 비용을 줄임)
- 결정: 리그에 입장한 플레이어만 조회하며, 종료된 리그의 공시도 조회할 수 있다
- 결정: 작성 정보는 거래일·작성 일시·작성자 플레이어 ID·내 공시 여부. 작성자 이름은 종목 조회의 상장자 이름을 쓴다 (작성자 = 상장자)
- TODO: 댓글(CNT-09)을 구현하면 목록의 공시마다 댓글이 있을 경우 최대 1개를 함께 담는다

### CNT-03 공시 작성자는 자신의 공시를 삭제할 수 있다
- Jira: ND-18
- 정책: [contents.md#공시](../../policies/v1/domain/contents.md#공시)
- 선행: CNT-01
- 완료 조건
  - [x] 작성자만 삭제 가능
  - [ ] 삭제 후 해당 공시와 공시에 달린 댓글 조회 불가 (상태 `DELETED`로 표시. 조회 차단은 CNT-02·CNT-09 구현 시 `Disclosure#isDeleted`로 처리)
  - [x] 기존 공시 기반 AI 뉴스와 체결된 거래 유지 (공시 행을 지우지 않고 상태만 변경, 거래는 공시와 무관)
  - [ ] 진행 중인 자동 재시도는 계속, 수동 재시도는 불가 (CNT-06·CNT-07 구현 시 처리)
- 결정: 삭제는 `POST /api/leagues/{leagueId}/stocks/{stockId}/disclosures/{disclosureId}/delete`. 공시 행은 남기고 `status`를 `DELETED`로 바꾸며(소프트 삭제), 첨부 이미지는 행과 파일 모두 삭제
- 결정: 종료된 리그의 공시도 삭제할 수 있다 (리그 상태와 무관하게 작성자는 언제든 삭제 가능)

### CNT-04 참여자는 공시에 호재 또는 악재로 반응할 수 있다
- Jira: ND-17
- 정책: [contents.md#공시](../../policies/v1/domain/contents.md#공시)
- 선행: CNT-01
- 완료 조건
  - [x] 호재 또는 악재 반응, 반응 수 집계
  - [x] 1인 1반응 (호재·악재 중 하나만 등록 가능)
  - [x] 반응은 뉴스 생성에 영향 없음
- 결정: 반응은 변경·취소할 수 있다. 버튼 토글 방식 (반응 없음 → 등록, 같은 버튼 → 취소, 다른 버튼 → 변경)
- 결정: 공시 작성자도 본인 공시에 반응할 수 있다
- 결정: 진행 중인 리그에서만 반응·변경·취소할 수 있다 (대기실·종료 리그는 거절)
- 결정: 반응은 `POST /api/leagues/{leagueId}/stocks/{stockId}/disclosures/{disclosureId}/reaction` (`{"type":"LIKE"|"DISLIKE"}`, 누른 버튼. ERD 표기를 따라 좋아요=호재, 싫어요=악재) 하나로 토글한다. 응답은 토글 후 좋아요·싫어요 수(`likeCount`, `dislikeCount`)와 내 반응. 삭제된 공시에는 반응할 수 없다

## 뉴스

### CNT-05 공시가 게시되면 AI가 공시 내용을 바탕으로 뉴스 1개를 생성한다
- Jira:
- 정책: [contents.md#뉴스---공시-기반-ai-뉴스](../../policies/v1/domain/contents.md#뉴스---공시-기반-ai-뉴스)
- 선행: CNT-01, GLB-06
- 내용: `DisclosurePublished` 비동기 구독
- 완료 조건
  - [ ] 공시 1건당 뉴스 1개 (공시와 일대일, 종목과 다대일)
  - [ ] 공시 원문의 사실 기준 작성, 말이 되는 문장이 없으면 미생성
  - [ ] 제목 최대 30자, 본문 최대 100자, 이미지 없음, 삭제 불가
  - [ ] 호재/악재 판정 함께 저장
  - [ ] 생성 실패해도 공시 원문 유지
- 결정 필요: "미생성" 판정을 실패로 볼지 (재시도 대상 여부)

### CNT-06 AI 뉴스 생성에 실패하면 최대 2회까지 자동으로 재시도한다
- Jira:
- 정책: [contents.md#뉴스---공시-기반-ai-뉴스](../../policies/v1/domain/contents.md#뉴스---공시-기반-ai-뉴스)
- 선행: CNT-05
- 완료 조건
  - [ ] 최초 실패 후 최대 2회 자동 재시도, 공시 삭제 여부와 무관
  - [ ] 모두 실패 시 수동 재시도 가능 상태로 전환

### CNT-07 공시 작성자는 자동 재시도까지 실패한 AI 뉴스를 직접 다시 생성할 수 있다
- Jira:
- 정책: [contents.md#뉴스---공시-기반-ai-뉴스](../../policies/v1/domain/contents.md#뉴스---공시-기반-ai-뉴스)
- 선행: CNT-06
- 완료 조건
  - [ ] 공시 작성자만, 자동 재시도 모두 실패한 경우만
  - [ ] 공시가 삭제되지 않았고 리그가 진행 중일 때만
  - [ ] 수동 요청에도 자동 재시도 최대 2회 적용

### CNT-08 참여자는 종목별 뉴스와 호재/악재 판정을 볼 수 있다
- Jira:
- 선행: CNT-05
- 완료 조건
  - [ ] 종목별 뉴스 목록, 호재/악재 표시
- 결정 필요: 리그 전체 뉴스 피드 제공 여부

## 댓글

### CNT-09 참여자는 공시와 뉴스에 댓글을 작성하고 조회할 수 있다
- Jira:
- 정책: [contents.md#댓글-공통](../../policies/v1/domain/contents.md#댓글-공통)
- 선행: CNT-01, CNT-05
- 완료 조건
  - [ ] 공시·뉴스 공통 적용, 최대 100자, 이미지 불가, 대댓글 불가
  - [ ] 작성자 계정 이름 표시

### CNT-10 댓글 작성자는 자신의 댓글을 삭제할 수 있다
- Jira:
- 정책: [contents.md#댓글-공통](../../policies/v1/domain/contents.md#댓글-공통)
- 선행: CNT-09
- 완료 조건
  - [ ] 작성자 본인만 삭제
