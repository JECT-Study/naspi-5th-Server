# [WEBSOCKET] 실시간 토픽 실데이터 전환 (Story)

> Jira epic: WEBSOCKET · 이슈 유형: **Story**
> 지금 웹소켓 토픽은 구독 중인 경로에 고정·랜덤 더미 응답을 주기적으로 보낸다 (`websocket/application/topic/*`의 `DUMMY`, `TODO`). 각 토픽을 실제 도메인 데이터로 바꾼다.
> `tasks/`에 두지만 사용자 화면의 실시간 반영 기능이라 Jira 유형은 Story로 등록한다. 정책 버전에 묶이지 않고, 내보내는 값의 계산 규칙은 해당 도메인 정책과 REST API를 따른다.

## 공통 원칙

- **같은 데이터는 같은 구조**: 대응하는 REST API가 있으면 토픽 메시지(`TopicEnvelope.data`)의 JSON 필드 이름·타입·의미를 REST 응답(`Result.data`)과 같게 맞춘다. 프론트가 REST로 받은 첫 화면 값을 토픽 메시지로 그대로 덮어쓸 수 있어야 한다.
  - 지금 더미 응답 구조(`websocket/api/response/*`)는 바꿔도 된다.
  - 계산은 REST와 같은 application 조회(서비스·뷰 객체)를 재사용한다. websocket이 값을 따로 계산하지 않는다.
  - websocket은 다른 모듈의 `api` 패키지(REST 응답 record)를 import하지 않는다. websocket 응답 record를 같은 필드로 두고, 구조가 같은지는 테스트로 확인한다 (REST 응답 JSON과 토픽 메시지 JSON의 필드 집합 비교).
  - REST에만 있는 조회자별 필드처럼 토픽으로 보낼 수 없는 필드가 있으면 스토리의 `결정 필요`에 적고 정한다.
- **구독 권한**: REST와 같은 조회 조건을 구독 시점에 확인한다 (`TopicSubscriptionAuthorizer`). 리그 단위 토픽은 해당 리그 입장자, 플레이어 단위 토픽은 본인 플레이어만.
- **발행 시점**: 값이 바뀔 수 있을 때 바뀐 대상에게만 발행한다. 구독자가 없는 토픽은 계산하지 않는다 (`TopicSubscriptions`). 리그 하나의 실패가 다른 리그 발행을 막지 않도록 리그 단위로 예외를 잡는다. 기준 설계: [docs/design/asset-realtime.md](../../design/asset-realtime.md)
- **즉시 발행은 커밋 이후**: 도메인 이벤트 리스너는 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 두고, 발행 예외는 기록만 하고 삼킨다.
- 새 토픽·리스너는 기존 코드 수정 없이 빈 추가로 만든다 (CLAUDE.md "경계 규칙" websocket 항목). 다중 서버 발행은 [COM-06](common.md#com-06-웹소켓-발행-이벤트-다중-서버-구성)에서 다룬다.
- 토픽별 스토리를 마치면 해당 클래스의 `DUMMY` 상수와 `TODO` 주석을 지운다.

## 작성 형식

```
### WS-NN <작업 이름>          ← "~ 실데이터 전환" 같은 작업명
- Jira: ND-NN
- 목적: 왜 필요한지 한 줄
- 토픽: 구독 경로
- 대응 REST: 같은 구조를 맞출 API (없으면 "없음")
- 작업 내용: 무엇을 하는지
- 선행: 필요한 스토리·Task
- 완료 조건 (확인 방법)
  - [ ] <조건> — `<확인 명령 또는 확인 방법>`
- 결정 필요: 구현 전에 정할 것
```

---

### WS-01 종목 시세 토픽 실데이터 전환
- Jira: ND-112
- 목적: 종목 목록 화면이 리그 전체 종목의 현재가·등락을 실시간으로 반영하게 한다
- 토픽: `/topic/leagues/{leagueId}/stocks` (`StockTopic`)
- 대응 REST: `GET /api/leagues/{leagueId}/stocks` (`StockLookupController#findAll`, 응답 `List<MemberStockResponse>`)
- 작업 내용
  - 메시지를 REST 응답과 같은 종목 목록 구조로 바꾼다 (`stockId`, `name`, `description`, `listerName`, 시세 필드 `currentPrice`·`basePrice`·`upperLimitPrice`·`lowerLimitPrice`·`changeAmount`·`changeRate`·`volume`). 지금 더미의 `stockName`, 최상위 `stock` 감싸기는 REST에 맞춘다
  - 값은 REST와 같은 market application 조회(`LeagueStockLookupService` / `StockQueryService`, 현재가 캐시)에서 얻는다
  - 주기 더미 발행을 `CurrentPricesChanged`(현재가가 바뀐 리그 ID) 구독으로 바꿔, 바뀐 리그의 구독 경로에만 발행한다
  - `StockTopicAuthorizer`가 해당 리그 입장자만 구독을 허용하게 한다 (지금은 로그인 회원 누구나)
- 선행: MKT-05(현재가·거래량 갱신), 현재가 캐시([asset-realtime.md 3장](../../design/asset-realtime.md#3-현재가-캐시))
- 완료 조건 (확인 방법)
  - [ ] 토픽 메시지 JSON 필드가 `GET /api/leagues/{leagueId}/stocks`의 종목 항목과 같다 (아래 `mine` 결정 반영) — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.stock.*"`
  - [ ] 체결로 현재가가 바뀐 리그의 구독자에게만 발행되고, 바뀌지 않은 리그에는 발행되지 않는다 — 같은 테스트
  - [ ] 리그에 입장하지 않은 회원은 구독할 수 없다 — 같은 테스트
  - [ ] `StockTopic.DUMMY`와 `TODO` 주석이 없다 — `grep -rn "DUMMY\|TODO" src/main/java/kr/ject/naspi/websocket/application/topic/stock`
- 결정 필요
  - REST의 `mine`(조회한 회원 본인 종목 여부)은 조회자마다 달라 리그 단위 토픽 하나로 보낼 수 없다. (a) 토픽에서는 `mine`을 빼고 프론트가 REST로 받은 값을 유지, (b) 플레이어 단위 토픽으로 바꿔 사람마다 발행 중 선택. 권장: (a)
  - 종목 소개·상장자 이름처럼 시세가 아닌 필드도 매번 보낼지, 시세 필드만 보낼지

### WS-02 호가 토픽 실데이터 전환
- Jira: ND-111
- 목적: 종목 상세·주문 화면의 호가창이 주문 접수·취소·체결을 실시간으로 반영하게 한다
- 토픽: `/topic/leagues/{leagueId}/stocks/{stockId}/orderbook` (`OrderBookTopic`)
- 대응 REST: `GET /api/leagues/{leagueId}/stocks/{stockId}/orderbook` (`OrderBookController`, 응답 `OrderBookResponse`)
- 작업 내용
  - 값을 REST와 같은 `OrderBookQueryService`(`OrderBookView`)에서 얻는다. 구조(`currentPrice`, `asks[]`, `bids[]`의 `orderPrice`·`remainingQuantity`, 최대 5단계, 없으면 빈 배열)는 REST와 같다
  - 호가가 바뀌는 시점(지정가 주문 접수, 주문 취소, 체결, 미체결 만료, 리그 종료 일괄 취소)에 해당 종목 구독 경로에만 발행한다. 체결은 `TradeExecuted`를 쓰고, 나머지는 order에 이벤트가 없으므로 추가한다
  - 현재가는 캐시 값이라 늦게 바뀔 수 있으므로 `CurrentPricesChanged`에도 해당 리그의 구독 종목을 다시 발행한다
  - `OrderBookTopicAuthorizer`가 REST와 같은 조건(진행 중인 리그의 참여자)으로 구독을 허용하게 한다
- 선행: ORD-12(호가 조회), ORD-01·ORD-07(주문 접수·취소)
- 완료 조건 (확인 방법)
  - [ ] 토픽 메시지 JSON 필드가 호가 조회 REST 응답과 같다 — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.orderbook.*"`
  - [ ] 지정가 주문 접수·취소·체결 커밋 후 해당 종목 구독자에게 바뀐 호가가 발행된다 — 같은 테스트
  - [ ] 다른 종목·다른 리그의 구독자에게는 발행되지 않는다 — 같은 테스트
  - [ ] 진행 중인 리그의 참여자가 아니면 구독할 수 없다 — 같은 테스트
  - [ ] `OrderBookTopic.DUMMY`와 `TODO` 주석이 없다 — `grep -rn "DUMMY\|TODO" src/main/java/kr/ject/naspi/websocket/application/topic/orderbook`
- 결정 필요
  - 주문 접수·취소 이벤트 이름과 위치 (`order/application`, 예: `OrderPlaced`, `OrderCanceled`). [WS-03](#ws-03-내-자산-토픽-실데이터-전환)의 예약 자산 즉시 발행도 같은 이벤트를 쓴다
  - 체결이 몰릴 때 이벤트마다 발행할지, 종목별로 모아 짧은 주기로 한 번 발행할지

### WS-03 내 자산 토픽 실데이터 전환
- Jira: ND-109
- 목적: 내 자산 화면이 현재가 변동과 내 체결·주문을 실시간으로 반영하게 한다 (정책 [asset.md#자산-조회](../../policies/v1/domain/asset.md#자산-조회) "본인 자산은 실시간 조회 가능")
- 토픽: `/topic/leagues/{leagueId}/players/{playerId}/assets` (`AssetTopic`)
- 대응 REST: `GET /api/leagues/{leagueId}/assets/me` (`MyAssetController`, 응답 `MyAssetResponse`)
- 작업 내용: [asset-realtime.md 6장](../../design/asset-realtime.md#6-구현-순서) 3~5단계
  - 메시지를 REST 응답 구조로 바꾼다 (`playerId`, `deposit`, `orderableDeposit`, `stockValuation`, `totalAsset`, `totalProfit`, `totalReturnRate`, `holdings[]`의 `treasuryStock`·`orderableQuantity`·`averagePrice`·`profit`·`returnRate` 등). 지금 더미의 `reservedDeposit`은 REST의 `orderableDeposit`으로 맞춘다
  - 계산 시각 `asOf`를 추가한다 (설계 5.3). REST 응답에도 같은 필드를 넣어 구조를 맞춘다
  - 주기 더미 발행을 `CurrentPricesChanged` 구독(바뀐 리그만, `AssetQueryService.getPlayerAssets`로 리그당 한 번)으로 바꾼다
  - `TradeExecuted` 커밋 후 매수자·매도자에게 즉시 발행한다
  - 주문 접수·취소, 미체결 만료, 리그 종료 이벤트가 생기면 해당 플레이어(리그 이벤트는 리그 구독자 전원)에게 즉시 발행한다
- 선행: AST-02, ORD-03, [WS-02](#ws-02-호가-토픽-실데이터-전환)의 주문 접수·취소 이벤트
- 완료 조건 (확인 방법)
  - [ ] 토픽 메시지 JSON 필드가 `GET /api/leagues/{leagueId}/assets/me` 응답과 같다 (`asOf` 포함) — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.asset.*"`
  - [ ] 현재가가 바뀐 리그의 구독자에게만 발행되고, 자산이 없는 플레이어(리그 시작 전)는 발행하지 않는다 — 같은 테스트
  - [ ] 체결 커밋 후 매수자·매도자에게 즉시 발행되고, 롤백되면 발행되지 않는다 — 같은 테스트
  - [ ] 즉시 발행도 현재가는 캐시 값을 쓴다 (설계 5.2) — 같은 테스트
  - [ ] 한 리그의 조회 실패가 다른 리그 발행을 막지 않는다 — 같은 테스트
  - [ ] `AssetTopic.DUMMY`와 `TODO` 주석이 없다 — `grep -rn "DUMMY\|TODO" src/main/java/kr/ject/naspi/websocket/application/topic/asset`
  - [ ] [asset-realtime.md](../../design/asset-realtime.md) 상태·구현 순서를 갱신한다
- 결정 필요
  - 프론트와 `asOf` 무시 규칙 합의 (표시 중인 값보다 이른 메시지 무시)

### WS-04 알림 토픽 실데이터 전환
- Jira: ND-108
- 목적: 내 주문 체결, 공시·뉴스 게시, 리그 진행을 해당 플레이어에게 알린다
- 토픽: `/topic/leagues/{leagueId}/players/{playerId}/alerts` (`AlertTopic`)
- 대응 REST: 없음 (알림 목록 API가 생기면 같은 구조로 맞춘다)
- 작업 내용
  - 주기 더미 발행을 지우고 도메인 이벤트 구독(커밋 이후)으로 바꾼다
    - `TRADE_EXECUTED`: `TradeExecuted` → 매수자·매도자
    - `DISCLOSURE`: `DisclosurePublished` → 리그 구독자
    - `NEWS`: 뉴스 생성 완료 이벤트 → 리그 구독자
    - `LEAGUE`: 리그 시작·거래일 전환·종료 이벤트 → 리그 구독자
  - `message`, `url`은 이벤트 값으로 만든다. 종목명 등 표시 정보는 각 모듈 application 조회로 얻는다
  - `AlertTopicAuthorizer`를 `AssetTopicAuthorizer`처럼 `LeagueQueryService.findPlayerId`로 본인 플레이어인지 확인하게 바꾼다 (지금은 `playerId`를 회원 ID와 비교)
- 선행: ORD-03, CNT(공시 게시·AI 뉴스), LEA-09·LEA-11·LEA-12(리그 시작·거래일 전환·종료)
- 완료 조건 (확인 방법)
  - [ ] 체결 커밋 후 매수자·매도자에게 `TRADE_EXECUTED` 알림이 발행된다 — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.alert.*"`
  - [ ] 공시·뉴스 게시 후 해당 리그 구독자에게 알림이 발행된다 — 같은 테스트
  - [ ] 경로의 `playerId`가 로그인 회원의 해당 리그 플레이어가 아니면 구독할 수 없다 — 같은 테스트
  - [ ] 주기 발행(`PeriodicTopic`)이 아니고, `AlertTopic.DUMMY`와 `TODO` 주석이 없다 — `grep -rn "DUMMY\|TODO\|PeriodicTopic" src/main/java/kr/ject/naspi/websocket/application/topic/alert`
- 결정 필요
  - v1 정책서에 알림 정책이 없다. 알림 대상 이벤트, 문구, 이동 경로(`url`)를 정책서에 먼저 정한다 (정책 없으면 구현하지 않는다)
  - 접속하지 않은 동안의 알림 보관·목록 조회 여부 (보관하면 REST 알림 목록 API를 함께 만들고 구조를 맞춘다)
  - 뉴스 생성 완료·리그 시작·거래일 전환·종료 이벤트 이름과 발행 위치

### WS-05 캔들 차트 토픽 실데이터 전환
- Jira: ND-110
- 목적: 종목 상세 차트가 실제 체결 데이터로 봉을 그리게 한다
- 토픽: `/topic/leagues/{leagueId}/stocks/{stockId}/candles` (`CandleChartTopic`)
- 대응 REST: 없음. 과거 캔들 조회 REST API를 함께 만들고 토픽과 같은 구조(`stockName`, `series[]`의 `interval`·`candles[]`)로 맞춘다
- 작업 내용
  - 체결 기록으로 봉 단위(10분·1시간·1일)별 시가·고가·저가·종가·거래량을 집계하는 market 조회를 만든다. 1일봉 구간은 league의 거래일 계산을 쓴다
  - 토픽은 지금처럼 봉 단위별 최근 2개(이전 봉·현재 봉)를 보내고, 체결(`TradeExecuted`)이나 현재가 변경 때 해당 종목 구독자에게 발행한다
  - 랜덤 더미 생성과 메모리 상태(`RecentCandles`)를 지운다
  - `CandleChartTopicAuthorizer`가 해당 리그 입장자만 구독을 허용하게 한다
- 선행: ORD-03(체결 기록), LEA-11(거래일 계산)
- 완료 조건 (확인 방법)
  - [ ] 과거 캔들 REST 응답과 토픽 메시지의 JSON 필드가 같다 — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.chart.*"`
  - [ ] 체결 데이터로 봉 단위별 시·고·저·종가와 거래량이 맞게 집계된다 — `./gradlew test --tests "kr.ject.naspi.market.*Candle*"`
  - [ ] 체결이 없는 구간은 직전 종가로 열린 거래량 0의 봉이다 — 같은 테스트
  - [ ] 리그에 입장하지 않은 회원은 구독할 수 없다 — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.chart.*"`
  - [ ] 랜덤 더미와 `TODO` 주석이 없다 — `grep -rn "Random\|DUMMY\|TODO" src/main/java/kr/ject/naspi/websocket/application/topic/chart`
- 결정 필요
  - 차트는 [roadmap.md 6.4](../../policies/roadmap.md)에 있어 v1 구현 대상이 아니다. v1에서 실데이터로 구현할지(정책서 반영 필요), v2까지 더미로 두거나 토픽을 내릴지 정한다
  - 체결 기록 저장 위치와 집계 방식 (체결마다 봉 테이블 갱신 vs 조회 시 집계). ERD 반영 필요
