# [WEBSOCKET] 실시간 토픽 실데이터 전환 (Story)

> Jira epic: WEBSOCKET · 이슈 유형: **Story**
> 웹소켓 토픽(`websocket/application/topic/*`)이 보내는 더미 응답(`DUMMY`, `TODO`)을 실제 도메인 데이터로 바꾸는 작업. 정책 버전에 묶이지 않고, 값의 계산 규칙은 해당 도메인 정책과 REST API를 따른다.
> 모든 스토리에 공통으로 적용하는 규칙은 아래 [공통 규칙](#공통-규칙)에 둔다. 기준 설계: [docs/design/asset-realtime.md](../../design/asset-realtime.md)

## 작성 형식

```
### WS-NN <작업 이름>          ← "~ 실데이터 전환" 같은 작업명
- Jira: ND-NN
- 목적: 왜 필요한지 한 줄
- 작업 내용: 토픽 경로, 대응 REST, 선행 작업, 무엇을 하는지
- 완료 조건 (확인 방법)
  - [ ] <조건> — `<확인 명령 또는 확인 방법>`
- 결정 필요: 구현 전에 정할 것 (있을 때만)
```

## 공통 규칙

- 대응하는 REST API가 있으면 토픽 메시지(`TopicEnvelope.payload`)의 JSON 필드 이름·타입·의미를 REST 응답(`Result.data`)과 같게 맞춘다. 계산은 REST와 같은 application 조회를 재사용하고, websocket은 다른 모듈의 `api` 패키지를 import하지 않는다 (구조가 같은지는 필드 집합 비교 테스트로 확인). 토픽으로 보낼 수 없는 조회자별 필드는 `결정 필요`에 적는다.
- 구독 권한은 REST와 같은 조회 조건을 구독 시점에 확인한다 (`TopicSubscriptionAuthorizer`). 리그 단위 토픽은 해당 리그 입장자, 플레이어 단위 토픽은 본인 플레이어만. 종료 시각이 지난 리그는 결과 페이지 외 접근이 막히므로(LEA-12) 입장 확인에 `LeagueQueryService#findAccessiblePlayerId`(`requireAccessiblePlayer`의 예외 없는 버전)를 쓴다. `AssetTopicAuthorizer`는 ND-85에서 바꿨고, 나머지 Authorizer(종목·호가·캔들·알림)는 WS-01·02·04·05에서 참여자 확인을 넣을 때 같은 메서드를 쓴다.
- 값이 바뀔 수 있을 때 바뀐 대상에게만 발행하고, 구독자가 없는 토픽은 계산하지 않는다 (`TopicSubscriptions`). 리그 단위로 예외를 잡아 한 리그의 실패가 다른 리그 발행을 막지 않게 한다.
- 도메인 이벤트 리스너는 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 두고, 발행 예외는 기록만 하고 삼킨다.
- 새 토픽·리스너는 기존 코드 수정 없이 빈 추가로 만든다 (CLAUDE.md "경계 규칙"). 다중 서버 발행은 [COM-06](common.md#com-06-웹소켓-발행-이벤트-다중-서버-구성)에서 다룬다.
- 스토리를 마치면 해당 토픽 클래스의 `DUMMY` 상수와 `TODO` 주석을 지운다.

---

### WS-01 종목 시세 토픽 실데이터 전환
- Jira: ND-112
- 목적: 종목 목록 화면이 리그 전체 종목의 현재가·등락을 실시간으로 반영하게 한다
- 작업 내용: `/topic/leagues/{leagueId}/stocks` (`StockTopic`)를 `GET /api/leagues/{leagueId}/stocks` (`StockLookupController#findAll`, `List<MemberStockResponse>`)와 같은 구조로 바꾼다. 선행: MKT-05, 현재가 캐시([asset-realtime.md 3장](../../design/asset-realtime.md#3-현재가-캐시))
  - 메시지를 REST 종목 목록 구조(`stockId`, `name`, `description`, `listerName`, `currentPrice`·`basePrice`·`upperLimitPrice`·`lowerLimitPrice`·`changeAmount`·`changeRate`·`volume`)로 바꾼다. `mine`은 넣지 않고, 시세가 아닌 필드도 매번 모두 보낸다. 더미의 `stockName`은 REST에 맞춘다
  - 내 자산 토픽과 같이 계산 시각 `asOf`를 WS 메시지에만 붙인다 (REST 응답에는 없음). REST `data`가 배열이므로 메시지는 `{ "stocks": [종목 항목], "asOf": ... }`로 감싼다
  - 값은 `LeagueStockLookupService`에서 얻는다. 지금 조회는 조회 회원(`memberId`)이 필요하므로 조회자 없이 리그 단위로 조회하는 메서드를 추가하고, REST와 종목 변환 로직을 공유한다
  - 주기 더미 발행을 `CurrentPricesChanged` 구독으로 바꿔 바뀐 리그에만 발행한다. 이 이벤트는 트랜잭션 밖(캐시 갱신 스케줄러)에서 발행되므로 `@EventListener`로 받는다
  - 같은 가격 체결은 현재가가 바뀌지 않아 `CurrentPricesChanged`가 나가지 않으므로, `TradeExecuted` 커밋 후에도 해당 리그에 발행해 거래량을 반영한다
  - `StockTopicAuthorizer`가 해당 리그 입장자만 구독을 허용하게 한다 (지금은 로그인 회원 누구나)
- 완료 조건 (확인 방법)
  - [x] 토픽 메시지 `stocks[]` 항목의 JSON 필드가 `GET /api/leagues/{leagueId}/stocks`의 종목 항목에서 `mine`을 뺀 것과 같고, 메시지에 `asOf`가 있다 — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.stock.*"`
  - [x] 체결로 현재가가 바뀐 리그의 구독자에게만 발행되고, 바뀌지 않은 리그에는 발행되지 않는다 — 같은 테스트
  - [x] 같은 가격으로 체결돼도 체결 커밋 후 해당 리그 구독자에게 바뀐 거래량이 발행되고, 롤백되면 발행되지 않는다 — 같은 테스트
  - [x] 리그에 입장하지 않은 회원은 구독할 수 없다 — 같은 테스트
  - [x] `StockTopic.DUMMY`와 `TODO` 주석이 없다 — `grep -rn "DUMMY\|TODO" src/main/java/kr/ject/naspi/websocket/application/topic/stock`
- 결정 (2026-11-27)
  - `mine`: 리그 단위 토픽을 유지하고 토픽에서 뺀다. 리그 안에서 바뀌지 않으므로 프론트는 REST로 받은 값을 유지한다
  - 시세가 아닌 필드(이름·소개·상장자 이름)도 매번 모두 보낸다
  - 거래량: 같은 가격 체결도 반영하도록 `TradeExecuted`(커밋 이후)도 구독한다
  - 비동기 발행 순서 역전은 내 자산과 같이 WS 전용 `asOf`로 클라이언트가 이른 메시지를 버린다
  - 범위 밖: 거래일 전환(09:00) 시 발행(별도 스토리), 종목 소개 수정 즉시 반영

### WS-02 호가 토픽 실데이터 전환
- Jira: ND-111
- 목적: 종목 상세·주문 화면의 호가창이 주문 접수·취소·체결을 실시간으로 반영하게 한다
- 작업 내용: `/topic/leagues/{leagueId}/stocks/{stockId}/orderbook` (`OrderBookTopic`)를 `GET /api/leagues/{leagueId}/stocks/{stockId}/orderbook` (`OrderBookController`, `OrderBookResponse`)와 같은 구조로 바꾼다. 선행: ORD-12, ORD-01·ORD-07
  - 값은 `OrderBookQueryService`(`OrderBookView`)에서 얻는다 (`currentPrice`, `asks[]`·`bids[]`의 `orderPrice`·`remainingQuantity`, 최대 5단계, 없으면 빈 배열)
  - 호가가 바뀌는 시점에 해당 종목 구독 경로에만 발행한다. 체결은 `TradeExecuted`, 지정가 접수는 `OrderPlaced`, 취소·만료·리그 종료 일괄 취소는 `OrderCanceled`(WS-03에서 추가)를 쓴다
  - 현재가는 캐시 값이라 늦게 바뀔 수 있으므로 `CurrentPricesChanged`에도 해당 리그의 구독 종목을 다시 발행한다
  - `OrderBookTopicAuthorizer`가 REST와 같은 조건(진행 중인 리그의 참여자)으로 구독을 허용하게 한다
- 완료 조건 (확인 방법)
  - [x] 토픽 메시지 JSON 필드가 호가 조회 REST 응답과 같다 — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.orderbook.*"`
  - [x] 지정가 주문 접수·취소·체결 커밋 후 해당 종목 구독자에게 바뀐 호가가 발행된다 — 같은 테스트
  - [x] 다른 종목·다른 리그의 구독자에게는 발행되지 않는다 — 같은 테스트
  - [x] 진행 중인 리그의 참여자가 아니면 구독할 수 없다 — 같은 테스트
  - [x] `OrderBookTopic.DUMMY`와 `TODO` 주석이 없다 — `grep -rn "DUMMY\|TODO" src/main/java/kr/ject/naspi/websocket/application/topic/orderbook`
- 결정: 체결이 몰려도 이벤트(`TradeExecuted`·`OrderPlaced`·`OrderCanceled`)마다 바로 발행한다. 같은 커밋에서 같은 종목이 여러 번 발행될 수 있다 (v1 규모에서 허용)

### WS-03 내 자산 토픽 실데이터 전환
- Jira: ND-109
- 목적: 내 자산 화면이 현재가 변동과 내 체결·주문을 실시간으로 반영하게 한다 (정책 [asset.md#자산-조회](../../policies/v1/domain/asset.md#자산-조회) "본인 자산은 실시간 조회 가능")
- 작업 내용: `/topic/leagues/{leagueId}/players/{playerId}/assets` (`AssetTopic`)를 `GET /api/leagues/{leagueId}/assets/me` (`MyAssetController`, `MyAssetResponse`)와 같은 구조로 바꾼다 ([asset-realtime.md 6장](../../design/asset-realtime.md#6-구현-순서) 3~5단계). 선행: AST-02, ORD-03
  - 메시지를 REST 응답 구조(`playerId`, `deposit`, `orderableDeposit`, `stockValuation`, `totalAsset`, `totalProfit`, `totalReturnRate`, `holdings[]` 등)로 바꾼다. 더미의 `reservedDeposit`은 `orderableDeposit`으로 맞춘다
  - 계산 시각 `asOf`(설계 5.3)는 토픽 메시지에만 더한다. REST 응답은 바꾸지 않는다
  - 주기 더미 발행을 `CurrentPricesChanged` 구독(바뀐 리그만, `AssetQueryService.getPlayerAssets`로 리그당 한 번)으로 바꾼다
  - `TradeExecuted` 커밋 후 매수자·매도자에게, `OrderPlaced`·`OrderCanceled` 커밋 후 주문한 플레이어에게 즉시 발행한다
- 완료 조건 (확인 방법)
  - [x] 토픽 메시지 JSON 필드가 `GET /api/leagues/{leagueId}/assets/me` 응답에 `asOf`를 더한 것과 같다 — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.asset.*"`
  - [x] 현재가가 바뀐 리그의 구독자에게만 발행되고, 자산이 없는 플레이어(리그 시작 전)는 발행하지 않는다 — 같은 테스트
  - [x] 체결 커밋 후 매수자·매도자에게 즉시 발행되고, 롤백되면 발행되지 않는다 — 같은 테스트
  - [x] 지정가 주문 접수, 취소·거래일 만료·일괄 취소 커밋 후 주문한 플레이어에게 즉시 발행된다 — 같은 테스트, `./gradlew test --tests "kr.ject.naspi.order.application.LimitOrderServiceTest"`
  - [x] 즉시 발행도 현재가는 캐시 값을 쓴다 (설계 5.2) — 즉시·주기 발행 모두 `AssetQueryService`를 쓴다. `./gradlew test --tests "kr.ject.naspi.asset.application.AssetQueryServiceTest"`
  - [x] 한 리그의 조회 실패가 다른 리그 발행을 막지 않는다 — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.asset.*"`
  - [x] `AssetTopic.DUMMY`와 `TODO` 주석이 없다 — `grep -rn "DUMMY\|TODO" src/main/java/kr/ject/naspi/websocket/application/topic/asset`
  - [x] [asset-realtime.md](../../design/asset-realtime.md) 상태·구현 순서를 갱신한다
- 결정 필요: 프론트와 `asOf` 무시 규칙 합의 (표시 중인 값보다 이른 메시지 무시)

### WS-04 알림 토픽 실데이터 전환
- Jira: ND-108
- 목적: 사용자가 요청하지 않았는데 바뀐 주문 상태(대기 주문 체결, 거래일 갱신에 따른 만료)를 해당 플레이어에게 알린다
- 정책: [order.md#주문-알림](../../policies/v1/domain/order.md#주문-알림)
- 작업 내용: `/topic/leagues/{leagueId}/players/{playerId}/alerts` (`AlertTopic`)의 주기 더미 발행을 도메인 이벤트 구독(커밋 이후)으로 바꾼다. 구조는 내 자산 토픽(`AssetTopic`)과 같게 하고 payload(`AlertResponse`)만 다르다. 대응 REST 없음. 선행: ORD-03, ORD-09
  - `TRADE_EXECUTED`: `TradeExecuted` → 대기 주문 주인 (체결된 대기 주문 1건당 1회, 신규 주문자 제외). 문구 `{종목 이름} {매수|매도} 주문이 체결됐어요.`, `url` `/leagues/{leagueId}/stocks/{stockId}`
  - `ORDER_EXPIRED`: 거래일 전환 이벤트 → 주문이 만료된 구독자. 문구 `새 거래일이 시작돼 대기 중인 주문이 취소됐어요.`, `url` 없음
  - `TRADING_DAY_CHANGED`: 거래일 전환 이벤트 → 만료된 주문이 없는 구독자. 문구 `새 거래일이 시작됐어요.`, `url` 없음
  - 종목명 등 표시 정보는 각 모듈 application 조회로 얻는다
  - `AlertTopicAuthorizer`가 `LeagueQueryService.findAccessiblePlayerId`로 본인 플레이어인지 확인하게 바꾼다 (지금은 `playerId`를 회원 ID와 비교)
  - `DISCLOSURE`·`NEWS`·`LEAGUE` 알림은 정책 확정 후 별도 작업으로 한다
- 완료 조건 (확인 방법)
  - [x] 체결 커밋 후 대기 주문 주인에게만 대기 주문 1건당 `TRADE_EXECUTED` 알림이 1회 발행되고, 신규 주문자에게는 발행되지 않는다 — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.alert.*"`
  - [x] 거래일 전환 커밋 후 주문이 만료된 구독자에게 `ORDER_EXPIRED`, 나머지 구독자에게 `TRADING_DAY_CHANGED` 알림이 1회 발행되고, 리그 종료 처리 중 보정에서는 발행되지 않는다 — 같은 테스트, `./gradlew test --tests "kr.ject.naspi.league.application.TradingDayChangeUseCaseTest"`
  - [x] 경로의 `playerId`가 로그인 회원의 해당 리그 플레이어가 아니면 구독할 수 없다 — 같은 테스트
  - [x] 주기 발행(`PeriodicTopic`)이 아니고, `AlertTopic.DUMMY`와 `TODO` 주석이 없다 — `grep -rn "DUMMY\|TODO\|PeriodicTopic" src/main/java/kr/ject/naspi/websocket/application/topic/alert`
- 결정 필요
  - 공시·뉴스 게시, 리그 시작·종료 알림의 대상·문구 (정책서에 먼저 정한다)
- 결정
  - 알림은 접속(구독) 중인 플레이어에게만 보내고 보관하지 않는다. 알림 목록 REST API는 만들지 않는다
  - 거래일 전환 이벤트는 `league/application/TradingDayChanged`이며 `TradingDayChangeUseCase`가 전환 트랜잭션 안에서 리그당 1회 발행한다
  - 리그 종료 이벤트는 `league/application/LeagueEnded(leagueId, endedAt)`이며 `LeagueEndUseCase`가 종료 트랜잭션 안에서 발행한다(ND-85, 발행만). 커밋 이후 리스너로 구독한다

### WS-05 캔들 차트 토픽 실데이터 전환
- Jira: ND-110
- 목적: 종목 상세 차트가 실제 체결 데이터로 봉을 그리게 한다
- 작업 내용: `/topic/leagues/{leagueId}/stocks/{stockId}/candles` (`CandleChartTopic`)를 체결 데이터 기반으로 바꾼다. 대응 REST: [WS-06](#ws-06-과거-캔들-조회-api-추가) 과거 캔들 조회 — 봉 단위 하나의 `Candle` 배열을 주므로 토픽의 `series[].candles[]` 항목과 필드를 같게 맞춘다. 선행: ORD-03(체결 기록), LEA-11(거래일 계산), WS-06(봉 집계 조회)
  - 체결 기록으로 봉 단위(10분·1시간·1일)별 시가·고가·저가·종가·거래량을 집계하는 market 조회를 만든다. 1일봉 구간은 league의 거래일 계산을 쓴다
  - 봉 단위별 최근 2개(이전 봉·현재 봉)를 보내고, `TradeExecuted`나 현재가 변경 때 해당 종목 구독자에게 발행한다
  - 랜덤 더미 생성과 메모리 상태(`RecentCandles`)를 지운다
  - `CandleChartTopicAuthorizer`가 해당 리그 입장자만 구독을 허용하게 한다
- 완료 조건 (확인 방법)
  - [ ] 과거 캔들 REST 응답 항목과 토픽 메시지 `candles[]` 항목의 JSON 필드가 같다 — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.chart.*"`
  - [ ] 체결 데이터로 봉 단위별 시·고·저·종가와 거래량이 맞게 집계된다 — `./gradlew test --tests "kr.ject.naspi.market.*Candle*"`
  - [ ] 체결이 없는 구간은 직전 종가로 열린 거래량 0의 봉이다 — 같은 테스트
  - [ ] 리그에 입장하지 않은 회원은 구독할 수 없다 — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.chart.*"`
  - [ ] 랜덤 더미와 `TODO` 주석이 없다 — `grep -rn "Random\|DUMMY\|TODO" src/main/java/kr/ject/naspi/websocket/application/topic/chart`
- 결정 필요
  - 차트는 [roadmap.md 6.4](../../policies/roadmap.md)에 있어 v1 구현 대상이 아니다. v1에서 실데이터로 구현할지(정책서 반영 필요), v2까지 더미로 두거나 토픽을 내릴지 정한다
  - 체결 기록 저장 위치와 집계 방식 (체결마다 봉 테이블 갱신 vs 조회 시 집계). ERD 반영 필요

### WS-06 과거 캔들 조회 API 추가
- Jira: ND-113
- 목적: 종목 상세 차트에 처음 들어갈 때 리그 시작부터 지금까지의 봉을 그리고, 이후는 [WS-05](#ws-05-캔들-차트-토픽-실데이터-전환) 토픽으로 이어 그리게 한다
- 작업 내용: `GET /api/leagues/{leagueId}/stocks/{stockId}/candles?interval={MINUTE_10|HOUR_1|DAY_1}`를 새로 만든다. 토픽 없음 (WS-05 `/topic/leagues/{leagueId}/stocks/{stockId}/candles`의 첫 화면 값). 선행: ORD-03(체결 기록), LEA-11(거래일 계산)
  - 요청 `interval`은 웹소켓과 같은 `MINUTE_10`, `HOUR_1`, `DAY_1`. 없거나 다른 값이면 400
  - 응답은 웹소켓 `Candle`과 같은 필드의 배열: `startedAt`(UTC epoch 밀리초), `openPrice`, `highPrice`, `lowPrice`, `closePrice`, `volume`
  - 범위는 리그 시작부터 지금까지(종료된 리그는 종료 시각까지), 오래된 순, 진행 중인 봉 포함
  - 체결 기록(`Trade`)으로 봉별 시가·고가·저가·종가·거래량을 집계한다. 체결이 없는 봉은 직전 봉 종가(첫 봉은 시초가 10,000원)로 시·고·저·종가를 채우고 거래량 0
  - **1일봉 1개 = 거래일 1일**. 구간은 league의 거래일(`trading_day`의 `starts_at`~`ends_at`, `TradingSchedule.periods()`)을 그대로 쓰고 24시간 단위로 자르지 않는다
    - 1일차는 리그 시작 일시부터 다음 날 09:00까지다. 00:00~09:00에 시작하면 그날 09:00 전후가 봉 두 개로 나뉘지 않고 1일차 봉 하나다
    - 1일봉 `startedAt`은 그 거래일의 `starts_at` (1일차는 리그 시작 일시, 이후는 09:00)
    - 1일봉 종가는 정책의 종가(거래일 마지막 체결가, 거래량 0이면 기준가)와 같다
  - 거래일 구간은 league `application` 조회(예: `LeagueQueryService`)로 노출해 받는다 (다른 모듈 domain 직접 참조 금지)
  - 조회 서비스는 체결 기록을 가진 order `application`에 두고, Controller는 호가 조회(`order/api/orderbook`)처럼 `order/api/candle`에 둔다. WS-05 토픽도 이 조회를 재사용한다
- 완료 조건 (확인 방법)
  - [ ] 응답 항목의 JSON 필드가 웹소켓 `Candle`과 같다 — `./gradlew test --tests "kr.ject.naspi.order.*Candle*"`
  - [ ] 체결 데이터로 봉 단위별 시·고·저·종가와 거래량이 맞게 집계되고, 오래된 순이며 진행 중인 봉을 포함한다 — 같은 테스트
  - [ ] 체결이 없는 봉은 직전 봉 종가(첫 봉은 10,000원)로 열린 거래량 0의 봉이다 — 같은 테스트
  - [ ] 1일봉은 거래일마다 1개이고, 00:00~09:00에 시작한 리그의 1일차(시작 일시~다음 날 09:00)도 봉 1개다 — 같은 테스트 (시각은 `Clock` 고정)
  - [ ] 1일봉 종가가 같은 거래일의 확정 종가와 같다 (거래량 0이면 기준가) — 같은 테스트
  - [ ] 리그에 입장하지 않은 회원은 조회할 수 없다 — 같은 테스트
  - [ ] Swagger UI에 API와 `interval` 값이 문서화된다 — `/swagger-ui/index.html`
- 결정 필요
  - 차트는 [roadmap.md 6.4](../../policies/roadmap.md)에 있어 v1 정책 범위 밖이다 (WS-05와 같은 결정). v1에서 구현하면 정책서에 차트 항목을 먼저 반영한다
  - 10분봉·1시간봉 경계: (a) KST 시계 기준(xx:00, xx:10 …)으로 자르고 첫 봉만 리그 시작 일시부터, (b) 리그 시작 일시 기준으로 자름. 권장: (a). (a)일 때 첫 봉 `startedAt`을 봉 경계로 줄지 리그 시작 일시로 줄지
  - 조회 가능 시점: 시작 전 리그(빈 배열 vs 에러), 종료된 리그(정책 "종료 후 결과 페이지만 접근 가능"에 따라 거절할지)
  - 1일봉 외 봉에서 거래일 경계(09:00)를 넘는 봉은 없다(10분·1시간이 09:00을 나누므로). 다만 (b)를 고르면 09:00을 넘는 봉이 생길 수 있어 자를지 정한다

### WS-07 리그 상세 토픽 추가 (대기실 상장 완료 반영)
- Jira: (미발급)
- 목적: 대기실의 사용자가 다른 사용자의 상장 완료(대기자 → 참여자 전환, 리그 시작 가능 여부)를 새로고침 없이 보게 한다
- 정책: [league.md#대기실](../../policies/v1/domain/league.md#대기실) "대기실 실시간 반영"
- 작업 내용: `/topic/leagues/{leagueId}/detail` (`LeagueDetailTopic`)을 새로 만든다. 대응 REST: `GET /api/leagues/{leagueId}/detail` (`LeagueDetailController`, `LeagueDetailResponse`). 선행: LEA-04, LEA-05, MKT-01
  - 메시지는 REST 리그 상세와 같은 구조에서 `players[].me`를 빼고 계산 시각 `asOf`를 붙인다
  - 값은 `LeagueDetailUseCase`에서 얻는다. 지금 조회는 조회 회원(`memberId`)이 필요하므로 조회자 없이 리그 단위로 조회하는 메서드(`getDetailInLeague`)를 추가하고 REST와 변환 로직을 공유한다
  - market의 `StockListed`를 커밋 이후에 구독한다. 이 이벤트는 대기자 → 참여자 전환(`LeagueService#completeListing`)보다 먼저 발행되므로 커밋 이후에 조회해야 전환이 반영된다. 리그 생성 중 방장 종목 상장에도 발행되지만 구독자가 없어 조회하지 않는다
  - `LeagueDetailTopicAuthorizer`가 REST와 같이 해당 리그 입장자(대기자 포함)만 구독을 허용한다
- 완료 조건 (확인 방법)
  - [x] 토픽 메시지의 JSON 필드가 `GET /api/leagues/{leagueId}/detail` 응답에서 `players[].me`를 뺀 것과 같고, 메시지에 `asOf`가 있다 — `./gradlew test --tests "kr.ject.naspi.websocket.api.response.TopicResponseSpecTest" --tests "kr.ject.naspi.websocket.application.topic.league.*"`
  - [x] 상장 커밋 후 그 리그 구독자에게 상장자가 참여자로 바뀐 리그 상세가 발행되고, 롤백되면 발행되지 않는다 — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.league.*" --tests "kr.ject.naspi.league.application.LeagueDetailUseCaseTest"`
  - [x] 구독자가 없는 리그는 조회하지 않고, 발행이 실패해도 상장 트랜잭션은 커밋된다 — `./gradlew test --tests "kr.ject.naspi.websocket.application.topic.league.*"`
  - [x] 리그에 입장하지 않은 회원은 구독할 수 없다 — 같은 테스트
- 결정 (2026-11-28)
  - 토픽 경로와 메시지는 REST 리그 상세(`/detail`)에 맞춘다. 대기실 전용 구조를 따로 만들지 않는다
  - `me`: 구독자마다 다르므로 토픽에서 뺀다. 프론트는 REST로 받은 본인 `playerId`와 비교한다
  - 범위 밖: 입장·퇴장·추방·리그 이름 수정·종목 수정 반영 (정책상 실시간 대상 아님), 리그 시작 반영
