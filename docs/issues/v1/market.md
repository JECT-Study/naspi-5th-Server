# [시장] market

> 정책: [docs/policies/v1/market.md](../../policies/v1/domain/market.md)

### MKT-01 대기자는 자신의 종목 이름과 소개를 작성해 리그에 상장할 수 있다
- Jira: ND-60, ND-76
- 정책: [market.md#종목-상장](../../policies/v1/domain/market.md#종목-상장)
- 선행: LEA-03
- 내용: 대기실에서 종목 이름·소개 작성 후 저장, `StockListed` 이벤트 발행
- 완료 조건
  - [x] 리그당 1인 1종목, 이름·소개 필수
  - [x] 이름: 앞뒤 공백 제거 후 최대 12자, 모든 문자 허용 (특수문자만으로 구성 불가), 같은 리그 내 중복 불가
  - [x] 소개: 최대 50자
  - [x] 만료된 대기실(생성 후 7일 경과)에서는 상장 불가, 상장 요청 시점에 만료 여부를 다시 검증 (ND-76)
- 메모: 리그 상태(대기실 여부)·플레이어 ID·참여자 전환은 INT-08에서 league `application`과 연동했다

### MKT-02 참여자는 대기실에서 종목명을, 리그 시작 이후에도 종목 소개를 수정할 수 있다
- Jira: ND-61, ND-66
- 정책: [market.md#종목-상장](../../policies/v1/domain/market.md#종목-상장)
- 선행: MKT-01
- 완료 조건
  - [x] 본인 종목만 수정 가능
  - [x] 종목명은 대기실 상태에서만 수정 가능, 리그 시작 후 불가
  - [x] 종목 소개는 대기실과 리그 시작 이후 모두 수정 가능
  - [x] MKT-01과 동일한 검증
- 결정: 종료된 리그(또는 종료 시각이 지난 리그)에서는 소개를 수정할 수 없다 (league.md "종료 시각부터 결과 페이지만 접근"). 오류 코드 `LEAGUE_ALREADY_ENDED`(212). 입장하지 않은 회원의 상장·수정은 `FORBIDDEN`(004)

### MKT-03 시스템은 기준가 대비 ±30%와 100원 호가 단위로 상·하한가를 계산한다
- Jira: ND-75
- 정책: [market.md#가격제한폭](../../policies/v1/domain/market.md#가격제한폭)
- 완료 조건
  - [x] 가격제한폭 금액 = 기준가 × 30%의 100원 미만 절사
  - [x] 상한가 = 기준가 + 금액, 하한가 = 기준가 - 금액
  - [x] 상·하한가 범위 밖 주문은 접수 거절 (접수는 ORD-02)
  - [x] 경계값 단위 테스트
  - [x] 결과는 100원 단위 정수, 같은 기준가는 항상 같은 결과, 경계 가격은 범위 안 (`PriceLimit.of(기준가)`, `contains`, `clamp`)
- 비고: 계산만 구현했다 (`market/domain/PriceLimit`). 거래일별 기준가·상·하한가는 MKT-06에서 `stock_daily_price`에 저장하고 `StockQueryService#getDailyPriceLimit`로 조회한다. ORD-02(범위 밖 거절)와 ORD-05(보호 가격 보정)가 이 결과를 사용한다.

### MKT-04 리그가 시작되면 모든 종목이 시초가 10,000원으로 거래를 시작한다
- Jira: ND-92
- 정책: [market.md#시장](../../policies/v1/domain/market.md#시장)
- 선행: LEA-09, MKT-03
- 완료 조건
  - [x] 리그 시작 시 모든 종목 시초가 10,000원, 최초 기준가 = 시초가
- 결정: 리그 시작 UseCase(LEA-09)가 초기 자산 배분과 같은 트랜잭션에서 `StockOpeningService#open`을 호출해 `stock.current_price`를 시초가로 정한다. `StockOpeningService#open`이 1일차 `stock_daily_price` 행(기준가 = 시초가)을 함께 만든다 (MKT-06)

### MKT-05 체결이 발생하면 종목의 현재가와 거래량이 갱신된다
- Jira: ND-98
- 정책: [market.md#시장](../../policies/v1/domain/market.md#시장)
- 선행: ORD-03, MKT-04
- 내용: 체결 1건마다 order 체결 흐름(`OrderExecutionSupport`)이 정산·체결 기록과 같은 트랜잭션에서 market `StockExecutionService#applyExecution`을 직접 호출해 `stock.current_price`와 `stock_daily_price.volume`을 갱신한다
- 완료 조건
  - [x] 현재가 = 최신 체결가 갱신
  - [x] 거래일 거래량 = 체결 수량 합계 (체결 1건당 1회 합산)
  - [X] VWAP 계산용 체결 기록 저장 (ND-74, [ORD-03](order.md#ord-03-접수된-주문은-가격시간-우선-원칙에-따라-상대-주문과-체결된다)에서 구현. 종목별·거래일별 합계와 종료 시각 역순 500주 조회 포함)
- 결정: 이벤트(`TradeExecuted`)를 구독하지 않고 order가 상위 모듈인 market을 직접 호출한다 (정책서 README). 호출은 `MANDATORY` 전파라 리그 락을 잡은 체결 트랜잭션 밖에서는 실패한다
  - 거래량은 market `stock_daily_price.volume`에 체결마다 누적한다 (체결 기록과 같은 트랜잭션·같은 거래일 번호로 쓰므로 `volume`은 같은 거래일 `trade` 수량 합계와 같다). 체결 기록 합계 조회(ORD-03)는 VWAP용으로 남는다
  - 거래일은 거래일 번호(`trading_day` int, `trade`·`orders`와 같은 방식)로 두고, `(stock_id, trading_day)` 행은 리그 시작(1일차)과 거래일 전환(다음 거래일)에서 만들고, 체결은 있는 행에 합산한다 (MKT-06에서 변경)
  - 거래가 열리지 않은 종목(`current_price`가 null)에 체결이 반영되려 하면 `IllegalStateException`으로 체결 전체를 롤백한다 (리그 시작 전 거래는 이미 막혀 있어 정상 흐름에서는 발생하지 않는 불변식 위반)

### MKT-06 거래일이 바뀌면 종목의 종가가 확정되고 새 기준가와 가격 범위가 적용된다
- Jira: ND-99
- 정책: [market.md#시장](../../policies/v1/domain/market.md#시장), [#가격제한폭](../../policies/v1/domain/market.md#가격제한폭)
- 선행: LEA-11, MKT-05
- 완료 조건
  - [x] 종가 = 마지막 체결가, 거래량 0이면 기준가
  - [x] 새 기준가 = 직전 종가, 새 상·하한가 적용, 거래량 초기화
  - [x] 거래일 전환과 체결의 순서 보장 (09:00 이전 체결은 이전 거래일)
- 결정: 종가는 전환 시점 `stock.current_price`이고 거래량이 0이면 기준가다. 행은 리그 시작·전환 시 만든다(`StockTradingDayService#changeTradingDay`, 거래일 번호는 league가 넘긴다). 09:00 이후 전환 처리 전 주문 접수는 `213`으로 거절한다(`LeagueTradingService#requireTradingDayOpened`), 취소는 허용한다. 기준가 100~300원이면 가격제한폭 금액이 0이라 상·하한가 = 기준가가 되며 정책 계산식 그대로 구현했다. ND-99와 ND-42(ORD-09)는 같은 배포로 묶는다
- 비고 (MKT-05에서 전달): 체결 시 거래일별 마지막 체결가는 따로 기록하지 않는다. 리그 락 아래에서 전환하면 종가 = 전환 시점의 `stock.current_price`다 (거래량 0인 거래일도 `current_price`가 직전 종가 = 기준가와 같다). 거래량은 `stock_daily_price` 행이 거래일마다 새로 생기므로 0부터 시작한다. 같은 행에 기준가·상·하한가·종가 컬럼을 추가한다

### MKT-07 리그가 종료되면 종목별 종료 평가 가격이 VWAP로 산정된다
- Jira: ND-115
- 정책: [league.md#리그-결과](../../policies/v1/domain/league.md#리그-결과)
- 선행: MKT-05
- 완료 조건
  - [x] 마지막 거래일 VWAP = Σ(체결가 × 수량) / Σ수량
  - [x] 마지막 거래일 체결량 500주 미만이면 종료 시각에 가까운 과거 체결부터 500주까지 확대
  - [x] 마지막으로 추가하는 체결 건은 500주를 채우는 데 필요한 수량만 반영
  - [x] 리그 전체 500주 미만이면 전체 체결, 체결 없으면 시초가
- 결정: VWAP 결과는 원 미만 절사한다. 평가 전용 가격이라 100원 호가 단위로 맞추지 않는다

### MKT-08 참여자는 리그 내 종목의 현재가, 등락률, 거래량 등 시세를 조회할 수 있다
- Jira: ND-105
- 정책: [market.md#시장](../../policies/v1/domain/market.md#시장)
- 선행: MKT-04
- 완료 조건
  - [x] 리그 내 종목 목록과 종목 상세: 이름, 소개, 현재가, 기준가, 상·하한가, 등락액, 등락률, 거래량
  - [x] 등락률 = (현재가 - 기준가) / 기준가 × 100, 소수점 둘째 자리에서 반올림하여 첫째 자리까지 표시 (정수여도 `0.0` 형태)

### MKT-09 상장자가 탈퇴해도 종목은 유지되고 계속 거래할 수 있다
- Jira: ND-67
- 정책: [member.md#회원-탈퇴](../../policies/v1/domain/member.md#회원-탈퇴)
- 선행: MBR-06, MKT-01
- 완료 조건
  - [x] 시작된 리그의 종목: 상장자가 탈퇴해도 그 사용자가 상장한 종목은 삭제되지 않고 정보(이름·소개·시세 등)가 그대로 유지된다 (대기실 종목은 LEA-17에서 폐기)
  - [x] 탈퇴한 상장자의 종목도 다른 참여자가 계속 매수·매도할 수 있다 (탈퇴자 본인의 보유분 거래 불가는 AST-07)

### MKT-10 사용자가 대기실에서 퇴장하면 해당 사용자의 종목이 삭제된다
- Jira: ND-82
- 정책: [league.md#대기실](../../policies/v1/domain/league.md#대기실)
- 선행: MKT-01, LEA-06
- 내용: Application 계층에 `leagueId`, `playerId`를 받는 사용자 종목 삭제 기능을 두고, 퇴장 UseCase가 호출한다. 리그 삭제 시 종목 전체 삭제는 `leagueId`만으로 처리하는 별도 기능이라 범위 밖
- 완료 조건
  - [x] 자진 퇴장, 방장의 참여자·대기자 삭제 시 해당 리그에서 그 사용자가 상장한 종목 삭제
  - [x] 대기실에 있는 회원의 탈퇴로 인한 퇴장도 동일하게 삭제 (LEA-17 구현 시 연동)
  - [x] 상장 전 대기자 퇴장은 삭제할 종목이 없어도 정상 처리
  - [x] 다른 리그의 같은 사용자 종목, 같은 리그의 다른 사용자 종목은 삭제하지 않음
  - [x] 삭제 후 해당 종목 이름은 같은 리그에서 다시 사용 가능
  - [x] 삭제 후 리그의 상장 종목 수·참여자 수 조회에 반영
  - [x] 퇴장 처리와 같은 트랜잭션에서 처리 (퇴장 롤백 시 삭제도 롤백)

### MKT-11 참여자는 리그 내 종목의 이름과 소개를 조회할 수 있다
- Jira: ND-68
- 정책: [market.md#종목-상장](../../policies/v1/domain/market.md#종목-상장)
- 선행: MKT-01, LEA-03
- 내용: 시장 탭의 종목 리스트(내 종목 / 친구 종목)와 종목 상세(소개, 상장자 이름, 내 종목이면 소개 수정 아이콘)에 쓰는 조회 API. 시세(현재가·등락률 등)는 MKT-08 범위
- 완료 조건
  - [x] `GET /api/leagues/{leagueId}/stocks`: 리그 종목 전체의 이름·소개·상장자 이름·내 종목 여부(`mine`) 조회
  - [x] `GET /api/leagues/{leagueId}/stocks/{stockId}`: 종목 하나의 이름·소개·상장자 이름·내 종목 여부 조회
  - [x] 리그에 입장한 회원(대기자 포함)만 조회 가능, 아니면 권한 없음
  - [x] 다른 리그의 종목은 조회되지 않음 (개별 조회는 종목 없음)
- TODO: 종목 조회 시 리그·플레이어 중복 조회 정리 (별도 스토리로 분리 예정). ND-85에서 종료 판정을 붙이며 `LeagueStockLookupService#viewerOf`가 `getPlayers`(리그·플레이어 전체 조회)에 이어 `requireAccessiblePlayer`(플레이어·리그 재조회)를 호출해, 요청마다 같은 데이터를 두 번 읽는다. 결과는 맞고 쿼리만 늘어난다. 권장: league application에 종료 판정을 포함한 리그 화면용 플레이어 조회(예: `getAccessiblePlayers`)를 두고, 본인 플레이어 ID는 `me` 항목에서 얻는다. 시세 조회(MKT-08, ND-105)도 같은 서비스를 쓴다
