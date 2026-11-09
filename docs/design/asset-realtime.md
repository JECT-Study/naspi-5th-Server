# 내 자산 실시간 발행 설계

> 대상 스토리: [AST-02](../issues/v1/asset.md#ast-02-참여자는-자신의-현금-보유-주식-예약-자산-총자산을-조회할-수-있다) (ND-10)
> 정책: [asset.md#자산-조회](../policies/v1/domain/asset.md#자산-조회)
> 상태: 설계 확정, 구현 대기 (league `Player`, market 현재가(MKT-04·05), order 체결 이벤트(ORD-03) 필요)

## 1. 조회 경로

| 경로 | 용도 |
|---|---|
| REST `GET /api/leagues/{leagueId}/assets/me` | 화면 첫 진입, 새로고침, 재접속 시 한 번 조회 |
| 웹소켓 `/topic/leagues/{leagueId}/players/{playerId}/assets` | 화면을 보는 동안 변동 반영 |

- 두 경로 모두 `AssetQueryService.getPlayerAsset`을 써서 계산 로직은 하나다.
- 본인 확인은 서버가 세션의 `memberId`와 `leagueId`로 league에서 `playerId`를 조회해 한다. 클라이언트가 보낸 `playerId`를 믿지 않는다.
  - REST: 경로에 `playerId`를 받지 않는다 (`me`).
  - 웹소켓: `AssetTopicAuthorizer`가 구독 경로의 `playerId`가 조회한 본인 `playerId`와 같은지 확인한다.

## 2. 발행 시점

자산 평가액은 내 체결이 없어도 바뀐다. 다른 참여자끼리 체결돼도 현재가가 바뀌고, 그 종목을 가진 모든 참여자의 평가액이 달라진다.
체결마다 리그의 구독자 전원을 다시 계산하면 거래가 몰릴 때 부하가 커지므로 두 방식을 함께 쓴다.

| 변동 원인 | 방식 | 대상 |
|---|---|---|
| 내 주문 체결 (현금·수량·매입 금액 변동) | `TradeExecuted` 수신 즉시 발행 | 매수자·매도자 플레이어만 |
| 다른 참여자의 체결로 현재가 변동 (평가액 변동) | 주기 발행 (`PeriodicTopic`, `naspi.websocket.topics.assets.interval`) | 구독 중인 플레이어 전원 |

- 주기 발행은 구독자가 있는 토픽만 계산한다 (`TopicScheduler`).
- 주기 발행에서 현재가는 리그당 한 번만 조회해 같은 리그 구독자들의 계산에 함께 쓴다 (플레이어마다 market을 조회하지 않는다).
  - 구독 경로를 리그별로 묶어 `AssetQueryService.getPlayerAssets(leagueId, playerIds)`를 리그당 한 번 호출한다.
  - 자산이 없는 플레이어(리그 시작 전 등)는 결과에서 빠지므로 발행하지 않는다.
- 즉시 발행(매수자·매도자)은 `getPlayerAsset`을 두 번 부르거나 `getPlayerAssets`로 두 명을 함께 조회한다.
- 예외는 리그 단위로 잡는다. `TopicScheduler`는 토픽 단위로만 예외를 잡으므로, `produce()` 안에서 한 리그의 실패(현재가 없음 등)를 잡지 않으면 그 주기의 다른 리그 발행까지 모두 빠진다.

## 3. 값 일관성

두 경로 모두 DB의 현재 상태(현금·보유 수량·예약·매입 금액)와 현재가로 **매번 처음부터 다시 계산**한다.
이전 메시지 값에 변동분을 더하는 누적 방식을 쓰지 않는다. 그래서 즉시 발행된 플레이어를 주기 발행이 다시 계산해도 같은 상태라면 같은 값이 나오고, 그 사이 현재가가 바뀌었다면 바뀐 값이 새로운 정답이다.

## 4. 주의 사항

### 4.1. 즉시 발행은 커밋 이후에 한다

- 체결 이벤트 리스너는 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 둔다.
- 커밋 전에 조회하면 체결 반영 전의 값을 "즉시" 발행하게 된다.
- 발행 실패가 체결 트랜잭션을 되돌리면 안 되므로, 발행 예외는 리스너 안에서 기록만 하고 삼킨다 (다음 주기 발행이 보정한다).
- 참고: 초기 자산 배분(`LeagueStartedListener`)은 반대로 같은 트랜잭션 안에서 동기로 처리해야 한다. 원자성이 필요한 처리와 화면 발행을 구분한다.

### 4.2. 메시지 순서가 뒤집힐 수 있다

주기 발행 계산과 체결 커밋이 겹치면 화면이 잠깐 이전 값으로 돌아갈 수 있다.

1. 주기 발행이 체결 전 DB를 읽기 시작한다.
2. 체결이 커밋되고 즉시 발행이 먼저 나간다 → 화면에 새 값.
3. 1번 계산이 늦게 끝나 발행된다 → 화면이 **체결 전 값으로 되돌아감**.
4. 다음 주기에 다시 새 값으로 돌아온다.

대응:
- 메시지에 계산 시각(DB 조회 시점, 주입받은 `Clock` 기준)을 담는다.
  - 기존 `TopicEnvelope.publishedAt`은 **발행 시각**이라 쓸 수 없다. 3번 단계의 늦은 메시지는 계산은 먼저 했지만 발행이 늦어 `publishedAt`이 더 최근이 되기 때문이다.
  - 자산 메시지(`AssetResponse`)에 계산 시각 필드(예: `asOf`)를 추가한다.
- 클라이언트는 표시 중인 값보다 계산 시각이 이른 메시지를 무시한다. 프론트와 이 규칙을 합의한다.

### 4.3. 재접속

웹소켓이 끊겼다 다시 연결되면 그 사이 변동을 받지 못한다. 재구독 시 REST로 한 번 다시 조회한다.

## 5. 구현 순서

1. league `Player` 구현 후 `memberId` + `leagueId` → `playerId` 조회 연결 (REST 컨트롤러, `AssetTopicAuthorizer`)
2. market 현재가 적용(MKT-04·05) 후 `AssetTopic`의 더미를 `AssetQueryService` 호출로 교체 (리그 단위 현재가 조회 묶기)
3. order 체결(ORD-03)의 `TradeExecuted` 확정 후 websocket에 즉시 발행 리스너 추가
