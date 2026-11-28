# [연동] 모듈 간 연동

> 모듈: 여러 모듈 · Jira 유형: **Task**
> 각 모듈 스토리가 만든 application API·이벤트를 다른 모듈의 흐름에 연결하는 작업과, 모듈 경계 규칙 위반 정리. 연결하는 양쪽 스토리를 `목적`·`선행`에 함께 적는다.
> 한 모듈 안에서 끝나는 기능은 해당 모듈 epic의 스토리로, 모든 모듈이 쓰는 기반 코드는 [global.md](global.md)에 둔다.

## 작성 형식

```
### INT-NN <작업 이름>
- Jira: ND-NN
- 목적: 어떤 스토리의 결과를 어느 흐름에 연결하는지 한 줄
- 내용: 호출 순서, 사용할 application API
- 선행: 연결하는 양쪽 스토리
- 완료 조건 (확인 방법)
  - [ ] <조건> — `<확인 명령>` (호출 여부는 grep, 동작은 테스트 클래스 패턴)
```

---

### INT-02 시장가 매수 체결·취소 후 남은 예약금을 asset 해제 API로 푼다
- Jira: ND-38
- 목적: AST-05(ND-32)의 미체크 조건 "시장가 매수: 체결 또는 취소 처리 후 예약금 중 실제 총체결 금액을 제외한 나머지 전액 해제"를 시장가 주문 흐름에 연결한다 (asset ↔ order 연동)
- 배경: asset은 준비돼 있다. 체결분은 `AssetSettlementService`가 `보호최고가 × 체결 수량`을 풀고 `체결가 × 체결 수량`만 차감하며, 잔량은 `releaseCashForBuy(보호최고가, 잔량)`로 푼다 (`AccountTest#releaseRemainingAfterMarketBuyFill`)
- 내용: 시장가 주문 서비스(ORD-05)에서 `보호최고가 × 주문 수량`으로 예약하고 `Order.recordReservedPrice(보호최고가)`를 남긴 뒤, 매칭 후 보호 범위 밖·상대 호가 없음으로 취소된 잔량을 `releaseCashForBuy` / `releaseStockForSell`로 해제한다
- 선행: AST-05, ORD-05 (ND-38)
- 완료 조건 (확인 방법)
  - [x] 시장가 매수가 일부 체결·잔량 취소되면 예약금이 0이고, 예수금 감소분은 실제 총체결 금액과 같다 — `./gradlew test --tests "kr.ject.naspi.order.*Market*"`
  - [x] 체결 가능 수량이 0이라 전량 취소되면 예약금이 전부 해제된다 — 같은 테스트
  - [x] 시장가 매도 잔량 취소 시 예약 주식이 해제된다 — 같은 테스트
  - [x] asset 전제 테스트가 통과한다 — `./gradlew test --tests "kr.ject.naspi.asset.domain.AccountTest"`
  - [x] AST-05 시장가 체크박스를 `[x]`로 바꾼다 — `scripts/find-story.sh ND-32`
- 비고 (2026-11-28): 시장가 매수 예약이 정책서 5.3 계산식(상대 호가 합산 + 부족분 보호최고가)의 금액 단위 예약으로 바뀌었다 (ORD-05 결정). 잔량 해제는 `releaseCashAmountForBuy(예약금 - 총체결 금액)`로 한다. 위 조건은 그대로 성립한다

### INT-03 미체결 만료·리그 종료 취소 시 예약 자산을 asset 해제 API로 푼다
- Jira:
- 목적: AST-06(ND-33)의 "취소·만료 시 미체결 잔량 해제"를 만료(ORD-09)와 리그 종료 일괄 취소(ORD-10) 흐름에 연결한다 (asset ↔ order 연동). 사용자 취소(ORD-07)는 `feature/ND-39,ND-40,ND-72`에서 이미 연결 중이라 제외한다
- 내용: `Order.expire()` / `Order.cancel()`로 상태를 바꾼 직후 미체결 잔량을 `releaseCashForBuy(예약 기준 가격, 잔량)` / `releaseStockForSell(잔량)`로 해제한다. 상태 전환이 거절된 주문(이미 체결 완료·취소·만료)은 해제하지 않아 이중 해제가 없다
- 선행: AST-06, ORD-09 (LEA-11 `TradingDayChangeUseCase` 필요), ORD-10
- 결정 필요 (order 담당과 정할 것)
  - (결정됨, LEA-11) 00:00~09:00에 시작한 리그 1일차 주문은 다음 거래일 시작(익일 09:00)에 만료한다. 당일 09:00에는 전환이 없다. 정책 "다음 오전 9시"는 "다음 거래일 시작"으로 해석한다
  - 7일차 09:00 만료와 리그 종료 취소가 겹칠 때 주문 상태 `EXPIRED` / `CANCELED`
  - 리그 종료 처리 중 락: `lockForTrading`은 거래 불가 리그에서 `LEAGUE_NOT_TRADABLE`을 던진다
- 완료 조건 (확인 방법)
  - [ ] 부분 체결 주문이 만료되면 체결분은 유지되고 잔량 예약만 해제된다 — `./gradlew test --tests "kr.ject.naspi.order.*Expir*"`
  - [ ] 리그가 종료되면 모든 미체결 주문이 취소되고 참여자 예약 현금·주식이 0이 된다 — `./gradlew test --tests "kr.ject.naspi.order.*LeagueEnd*"`
  - [ ] order에서 만료·종료 흐름이 해제 API를 호출한다 — `grep -rn "releaseCashForBuy\|releaseStockForSell" src/main/java/kr/ject/naspi/order` 결과에 만료·종료 서비스가 포함됨
  - [ ] asset 해제 테스트가 통과한다 — `./gradlew test --tests "kr.ject.naspi.asset.domain.AccountTest" --tests "kr.ject.naspi.asset.domain.HoldingTest"`

### INT-04 order가 market의 domain을 직접 참조하지 않는다
- Jira:
- 목적: "다른 모듈의 domain/infra를 직접 참조하지 않고 application을 통해 접근한다" 경계 규칙 위반을 정리한다
- 배경: `LimitOrderService`가 `kr.ject.naspi.market.domain.PriceLimit`을 import해 상·하한가를 판정한다 (#35)
- 내용: 상·하한가 판정을 market `application` 서비스로 노출하고 order는 그 서비스를 호출한다. 판정 규칙과 오류 코드(`ORDER_PRICE_OUT_OF_LIMIT`)는 바꾸지 않는다
- 선행: 없음 (GLB-07 의존 방향 테스트가 생기면 함께 검증된다)
- 완료 조건 (확인 방법)
  - [ ] order가 다른 모듈의 domain·infra를 import하지 않는다 — `grep -rnE "import kr\.ject\.naspi\.(member|league|market|asset)\.(domain|infra)\." src/main/java/kr/ject/naspi/order` 결과 없음
  - [ ] 상·하한가 밖 지정가 주문 거절이 그대로 동작한다 — `./gradlew test --tests "kr.ject.naspi.order.application.LimitOrderServiceTest" --tests "kr.ject.naspi.market.*"`

### INT-05 리그 종료 결과 산정이 종료 평가 가격으로 asset 총자산 집계를 호출한다
- Jira: ND-86
- 목적: AST-08(ND-62)의 `AssetQueryService#aggregateTotalAssets`를 LEA-13 결과 산정에 연결한다 (league ↔ market ↔ asset 연동)
- 내용: league UseCase가 MKT-07 종료 평가 가격(종목 ID → 가격)을 market에서 받아 `aggregateTotalAssets(leagueId, prices)`에 넘기고, 결과로 순위·스냅샷을 만든다. asset은 하위 모듈이라 Service가 아닌 UseCase에서 호출한다
- 선행: AST-08, MKT-07, LEA-13
- 완료 조건 (확인 방법)
  - [x] league에서 결과 산정이 asset 총자산 집계를 호출한다 — `grep -rn "aggregateTotalAssets" src/main/java/kr/ject/naspi/league` 결과 있음
  - [x] 결과 총자산이 종료 평가 가격 기준이다 — `./gradlew test --tests "kr.ject.naspi.league.*Result*"`

### INT-06 거래일 종료 시 종가로 asset 총자산 집계를 호출한다
- Jira: ND-90
- 목적: AST-08의 집계 API를 LEA-18 거래일별 자산 집계에 연결한다
- 내용: 거래일 전환 UseCase(LEA-11) 처리 중 league UseCase가 market 종가(MKT-06)를 받아 `aggregateTotalAssets`에 넘긴다
- 선행: AST-08, MKT-06, LEA-11, LEA-18
- 완료 조건 (확인 방법)
  - [ ] `grep -rn "aggregateTotalAssets" src/main/java/kr/ject/naspi/league` 결과 있음
  - [ ] 거래일 집계가 종가 기준이다 — `./gradlew test --tests "kr.ject.naspi.league.*TradingDay*"`

### INT-07 league가 asset의 domain을 직접 참조하지 않는다
- Jira: ND-81
- 목적: 경계 규칙 위반 정리. `LeagueStartUseCase`가 `kr.ject.naspi.asset.domain.StockListing`을 import한다 (`LeagueStartUseCase.java:8`)
- 내용: `StockListing`에 해당하는 입력 타입을 asset `application`에 두고 `InitialAssetAllocationService#allocate`가 그것을 받게 한다
- 선행: 없음
- 완료 조건 (확인 방법)
  - [x] `grep -rn "import kr.ject.naspi.asset.domain" src/main/java --exclude-dir=asset` 결과 없음
  - [x] `./gradlew test --tests "kr.ject.naspi.league.application.LeagueStartUseCaseTest" --tests "kr.ject.naspi.asset.application.InitialAssetAllocationServiceTest"`

### INT-08 종목 상장·수정이 league의 플레이어 ID·리그 상태를 쓰고, 상장하면 대기자가 참여자가 된다
- Jira: ND-91
- 목적: MKT-01·MKT-02(market)의 상장·수정을 LEA-05(대기자 → 참여자 전환)와 리그 상태에 연결한다 (market ↔ league 연동). 지금은 상장해도 대기자로 남아 방장 외 참여자가 생기지 않고, 리그를 시작할 수 없다
- 내용
  - `StockListingService#list`·`StockEditService`가 회원 ID를 플레이어 ID 자리에 쓰는 임시 코드를 `LeagueQueryService#findPlayerId(memberId, leagueId)`로 바꾼다 (리그에 입장하지 않은 회원은 거절)
  - 상장 후 같은 트랜잭션에서 `LeagueService#completeListing(playerId)`를 호출한다
  - 상장과 이름·소개 수정은 대기실에서만, 소개만 수정은 대기실·진행 중 리그에서 허용한다 (MKT-02)
  - 만료된 대기실(생성 후 7일 경과)에는 상장할 수 없다 (MKT-01, ND-76)
  - 리그 생성(`LeagueCreationUseCase`)의 방장 상장 호출도 바뀐 시그니처에 맞춘다
  - market → league는 상위 모듈 참조라 Service에서 league `application`을 호출한다
- 선행: LEA-05, MKT-01, MKT-02
- 완료 조건 (확인 방법)
  - [x] market에서 상장 시 참여자 전환을 호출한다 — `grep -rn "completeListing" src/main/java/kr/ject/naspi/market` 결과 있음
  - [x] market에 회원 ID를 플레이어 ID로 쓰는 임시 코드가 없다 — `grep -rn "long listerPlayerId = memberId\|long playerId = memberId" src/main/java/kr/ject/naspi/market` 결과 없음
  - [x] 대기자가 상장하면 참여자가 되고, 참여자 3명·대기자 0명이면 리그를 시작할 수 있다 — `./gradlew test --tests "kr.ject.naspi.market.*" --tests "kr.ject.naspi.league.*"`
  - [x] 시작된 리그에서 상장·이름 수정이 거절되고 소개 수정은 허용된다, 만료된 대기실 상장이 거절된다 — 같은 테스트
