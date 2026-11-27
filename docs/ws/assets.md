# 내 자산 토픽

리그에서 내 보유 현금, 보유 종목별 평가 금액·손익, 총자산을 실시간으로 받습니다. 리그 화면의 "내 자산" 영역(보유 현금, 보유 주식 현황)에서 씁니다.

| **항목** | **값** |
| --- | --- |
| 연결 엔드포인트 | `/ws` ([1. 연결 엔드포인트](#1-연결-엔드포인트)) |
| 프로토콜 | STOMP 1.2 over WebSocket (SockJS 미사용) |
| 인증 | 로그인 세션 쿠키 `SESSION` ([2. 연결 인증](#2-연결-인증)) |
| 구독 | `/topic/leagues/{leagueId}/players/{playerId}/assets` |
| 구독 권한 | 본인만 |
| 메시지 종류 (`type`) | `ASSET` |
| 전달 시점 | 내 자산이 바뀔 때 ([전달 시점](#전달-시점)). 구독자가 없으면 보내지 않습니다 |
| 대응 REST | `GET /api/leagues/{leagueId}/assets/me` |
| 발행 (클라이언트 → 서버) | 없음 |

---

## 1. 연결 엔드포인트

| **환경** | **URL** |
| --- | --- |
| 로컬 | `ws://localhost:8080/ws` |
| 운영 | `wss://api.naspi.my/ws` |

- 프로토콜은 **STOMP 1.2 over WebSocket**입니다. SockJS를 쓰지 않으므로 `http://` 주소나 `/ws/info` 같은 SockJS 경로는 쓰지 않습니다.
- HTTPS 페이지에서는 반드시 `wss://`로 연결합니다. `ws://`로 연결하면 브라우저가 막습니다.
- 서버 heartbeat 간격은 **10초**입니다(`naspi.websocket.heartbeat`).
- 모든 토픽이 같은 엔드포인트를 씁니다. 여러 토픽을 구독할 때도 **연결 하나**에서 구독만 추가합니다.

---

## 2. 연결 인증

웹에서 **카카오 로그인**을 마쳤다면 따로 인증 작업 없이 바로 연결하면 됩니다.

1. 카카오 로그인(`GET /oauth2/authorization/kakao`)을 마치면 로그인 세션 쿠키(`SESSION`)가 브라우저에 저장됩니다.
2. `/ws`로 연결하면 브라우저가 이 쿠키를 자동으로 붙이고, 서버는 로그인 회원으로 인증합니다.

STOMP CONNECT 프레임에는 토큰이나 인증 헤더를 넣지 않습니다.

> 로그인하지 않았거나 세션이 만료되면 연결이 실패합니다. 연결이 계속 실패하면 카카오 로그인부터 다시 하도록 안내합니다.

---

## 3. 구독 (서버 → 클라이언트)

`SUBSCRIBE /topic/leagues/{leagueId}/players/{playerId}/assets`

### 경로 변수

| **변수** | **타입** | **설명** |
| --- | --- | --- |
| leagueId | Long | 리그 ID |
| playerId | Long | **내 플레이어 ID**. 회원 ID가 아닙니다. `GET /api/leagues/{leagueId}/assets/me` 응답의 `playerId`를 씁니다 |

### 구독 권한

로그인한 회원 **본인의 자산**만 구독할 수 있습니다.

권한이 없으면 `ERROR` 프레임을 받고 **연결 전체가 끊깁니다**([5. 에러](#5-에러)).

### SUBSCRIBE 프레임

| **헤더** | **값** | **필수** | **비고** |
| --- | --- | --- | --- |
| destination | `/topic/leagues/{leagueId}/players/{playerId}/assets` | O |  |
| id | 구독 ID | O | 클라이언트가 정하는 값. STOMP 라이브러리가 자동으로 만듭니다 |

```
SUBSCRIBE
id:sub-0
destination:/topic/leagues/1/players/12/assets

^@
```

### 전달 시점

| **원인** | **받는 사람** |
| --- | --- |
| 현재가 변경 | 그 리그의 구독자 전원 |
| 체결 | 매수자·매도자 |
| 주문 접수·취소·만료 (예약금·예약 수량 변동) | 주문한 플레이어 |

### 받는 메시지

모든 토픽 메시지는 같은 형식(`TopicEnvelope`)으로 옵니다. REST API의 `Result`(`code`, `message`, `data`)와 다른 형식이니 주의합니다.

| **필드** | **타입** | **설명** | **비고** |
| --- | --- | --- | --- |
| type | String | 메시지 종류 | 이 토픽은 항상 `ASSET` |
| payload | Object | 데이터 | 아래 표 참고 |
| publishedAt | String | 발행 시각 (KST) | REST 응답의 일시와 같은 형식(오프셋 없음, 밀리초까지), 예: `2026-11-09T14:32:05.123` |

#### payload

금액은 원, 수량은 주 단위입니다. 수익률(%)은 소수점 첫째 자리까지 반올림합니다.

REST `GET /assets/me`의 `data`와 같은 필드에 `asOf`만 더해 보냅니다.

| **필드** | **타입** | **설명** | **비고** |
| --- | --- | --- | --- |
| playerId | Long | 내 플레이어 ID |  |
| deposit | Long | 보유 현금 (원) | 대기 중인 매수 주문에 묶인 예약금 포함 |
| orderableDeposit | Long | 주문 가능 현금 (원) | `deposit - 매수 예약금` |
| stockValuation | Long | 보유 주식 평가 금액 합계 (원) | `Σ holdings[].valuation` |
| totalAsset | Long | 총자산 (원) | `deposit + stockValuation` |
| totalProfit | Long | 총자산 평가 손익 (원) | `totalAsset - 100,000,000`. 음수 가능 |
| totalReturnRate | Number | 총자산 수익률 (%) | `totalProfit ÷ 100,000,000 × 100`. 소수점 첫째 자리까지 (예: `0.6`, `-12.4`) |
| asOf | String | 값을 계산한 시각 (KST) | REST와 같은 일시 형식 |
| holdings | Array | 보유 종목 | **종목 ID 오름차순**. 보유 수량이 1주 이상인 종목만. 없으면 빈 배열 |
| holdings[].stockId | Long | 종목 ID |  |
| holdings[].stockName | String | 종목 이름 | 상장자 이름 |
| holdings[].treasuryStock | Boolean | 자사주(내 종목) 여부 |  |
| holdings[].quantity | Long | 보유 수량 (주) | 매도 주문에 묶인 수량 포함 |
| holdings[].orderableQuantity | Long | 매도 가능 수량 (주) | 일반 종목: `quantity - 매도 예약 수량`. 자사주: `max(0, quantity - 매도 예약 수량 - 1,000)` |
| holdings[].currentPrice | Long | 현재가 (원) |  |
| holdings[].averagePrice | Long | 평균 매입가 (원) | 이동평균법, `매입 금액 ÷ quantity`, 원 미만 절사. 표시용 |
| holdings[].valuation | Long | 평가 금액 (원) | `quantity × currentPrice` |
| holdings[].profit | Long | 평가 손익 (원) | `valuation - 매입 금액`. 음수 가능 |
| holdings[].returnRate | Number | 수익률 (%) | `profit ÷ 매입 금액 × 100`. 소수점 첫째 자리까지 |

```
MESSAGE
destination:/topic/leagues/1/players/12/assets
content-type:application/json
subscription:sub-0
message-id:a1b2c3-0

{
  "type": "ASSET",
  "payload": {
    "playerId": 12,
    "deposit": 49010000,
    "orderableDeposit": 48040000,
    "stockValuation": 51615000,
    "totalAsset": 100625000,
    "totalProfit": 625000,
    "totalReturnRate": 0.6,
    "asOf": "2026-11-25T21:30:01.123456",
    "holdings": [
      {
        "stockId": 5, "stockName": "수빈", "treasuryStock": true,
        "quantity": 2400, "orderableQuantity": 900, "currentPrice": 10300,
        "averagePrice": 10000, "valuation": 24720000, "profit": 720000, "returnRate": 3.0
      },
      {
        "stockId": 6, "stockName": "민준", "treasuryStock": false,
        "quantity": 1250, "orderableQuantity": 1250, "currentPrice": 9800,
        "averagePrice": 10000, "valuation": 12250000, "profit": -250000, "returnRate": -2.0
      },
      {
        "stockId": 7, "stockName": "지아", "treasuryStock": false,
        "quantity": 1450, "orderableQuantity": 1450, "currentPrice": 10100,
        "averagePrice": 10027, "valuation": 14645000, "profit": 105000, "returnRate": 0.7
      }
    ]
  },
  "publishedAt": "2026-11-25T21:30:01.125"
}^@
```

### 메시지 처리 방법

- 메시지는 바뀐 부분이 아니라 **그 시점의 상태**를 담습니다. 이전 메시지와 비교해 변경분을 계산하지 않습니다.
- 네트워크 상황이나 서버의 동시 발행으로 순서가 뒤바뀔 수 있으므로, **`asOf`가 마지막으로 반영한 메시지보다 이른 메시지는 버립니다.** REST 응답에는 `asOf`가 없으므로 REST 값 다음에 오는 첫 메시지는 그대로 반영합니다.
  - 이 토픽은 `publishedAt`(발행 시각)이 아니라 `asOf`(계산 시각)로 판단합니다. 먼저 계산했지만 늦게 발행된 메시지는 `publishedAt`이 더 최근이어도 이전 값입니다.
- 연결이 끊긴 동안의 메시지는 다시 받을 수 없습니다. (재)연결 직후에는 REST API로 현재 상태를 받고, 이후 메시지를 반영합니다.
- 받은 값으로 화면 상태를 통째로 덮어씁니다.
- `holdings`는 **전체 목록**입니다. 목록을 통째로 바꿉니다. 없어진 종목은 전량 매도한 것입니다.
- 다른 리그로 화면이 바뀌면 이전 구독을 해지하고, 그 리그의 `playerId`로 새로 구독합니다.

---

## 4. 발행 (클라이언트 → 서버)

**이 토픽은 발행이 없습니다.** 서버가 보내는 메시지를 구독만 합니다.

- 클라이언트는 `/app/**`으로만 `SEND`할 수 있습니다. 다른 목적지(예: `/topic/**`)로 보내면 `ERROR` 프레임을 받고 연결이 끊깁니다.
- 지금은 `/app/**`을 처리하는 서버 기능이 없어, `/app/**`으로 보내도 아무 일도 일어나지 않습니다.

---

## 5. 에러

STOMP 단계의 오류는 `ERROR` 프레임으로 옵니다. **`ERROR` 프레임을 받으면 서버가 곧바로 연결을 끊고, 같은 연결의 다른 토픽 구독도 모두 끊깁니다.** 다시 연결한 뒤 다시 구독해야 합니다.

`ERROR` 프레임은 본문 없이 `message` 헤더에 사유만 담습니다(JSON 아님).

```
ERROR
message:토픽 구독 권한이 없습니다\c /topic/leagues/1/players/13/assets
content-length:0

^@
```

> 헤더 값의 `:`은 STOMP 규칙에 따라 `\c`로 바뀌어 전송됩니다. STOMP 라이브러리가 원래대로 되돌려 줍니다.

| **상황** | **`message` 헤더** | **결과** |
| --- | --- | --- |
| 구독 권한 없음, 또는 경로 형식이 틀림 | `토픽 구독 권한이 없습니다: {경로}` | `ERROR` 후 연결 끊김 |
| `/topic/`이 아닌 경로 구독 | `구독할 수 없는 토픽입니다: {경로}` | `ERROR` 후 연결 끊김 |
| `/app/`이 아닌 목적지로 `SEND` | `메시지를 보낼 수 없는 목적지입니다: {경로}` | `ERROR` 후 연결 끊김 |
| 로그인 정보 없이 CONNECT | `로그인이 필요합니다` | `ERROR` 후 연결 끊김. 보통은 그 전에 핸드셰이크가 `401`로 막힙니다 |

> `message` 문구는 바뀔 수 있으니 화면 분기에 쓰지 말고 로그 확인용으로만 씁니다.

### 예외

1. **구독 권한이 없는 경우:** `ERROR` 프레임(`토픽 구독 권한이 없습니다: ...`)을 받고 연결 전체가 끊깁니다. 다른 토픽 구독도 함께 끊기므로 `playerId`에 **회원 ID가 아니라 그 리그의 내 플레이어 ID**를 넣었는지 꼭 확인합니다.
2. **없는 리그 ID, 입장하지 않은 리그로 구독한 경우:** 1과 같이 `ERROR` 후 연결이 끊깁니다.
3. **리그 시작 전:** 구독은 되지만 자산이 없어 메시지가 오지 않습니다. 리그가 시작되면 REST로 다시 조회합니다.
4. **리그 종료 후:** 메시지가 오지 않습니다. 최종 결과는 리그 결과 화면에서 받습니다.
5. **연결이 끊긴 동안의 변화:** 다시 받을 수 없습니다. 다시 연결한 직후 `GET /assets/me`로 현재 상태를 받습니다.

---

## 6. FE 연동 예시

`@stomp/stompjs` 7.x를 사용합니다.

```bash
npm install @stomp/stompjs
```

### 6-1. 공통 연결 (`stompClient.ts`)

모든 토픽이 연결 하나를 같이 씁니다. 다른 토픽 문서에서 이미 만들었다면 그대로 재사용합니다.

```tsx
import { Client, IMessage } from '@stomp/stompjs';

/** 모든 토픽 메시지의 공통 형식. */
export type TopicEnvelope<T> = {
  type: string;
  payload: T;
  publishedAt: string;
};

export const stompClient = new Client({
  brokerURL: import.meta.env.VITE_WS_URL, // 로컬: ws://localhost:8080/ws, 운영: wss://api.naspi.my/ws
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
  reconnectDelay: 3000, // 끊기면 3초 뒤 다시 연결
});

/** 구독 중인 토픽. 다시 연결되면 모두 다시 구독한다. */
const topics = new Map<string, (frame: IMessage) => void>();

stompClient.onConnect = () => {
  topics.forEach((handler, destination) =>
    stompClient.subscribe(destination, handler, { id: destination }),
  );
};

// 구독 권한 없음 등. 서버가 곧 연결을 끊고, reconnectDelay 뒤 다시 연결한다.
stompClient.onStompError = (frame) => {
  console.error('STOMP ERROR:', frame.headers.message);
};

// 핸드셰이크 실패(401 등)도 여기로 온다. 상태 코드는 알 수 없으므로 필요하면 REST로 로그인 상태를 확인한다.
stompClient.onWebSocketClose = (event) => {
  console.warn('WebSocket 연결 종료:', event.code);
};

/**
 * 토픽을 구독한다. 반환된 함수를 호출하면 구독을 해지한다.
 * 같은 경로는 한 곳에서만 구독한다고 가정한다.
 */
export function subscribeTopic<T>(
  destination: string,
  onMessage: (payload: T, envelope: TopicEnvelope<T>) => void,
): () => void {
  let lastPublishedAt = 0;
  const handler = (frame: IMessage) => {
    const envelope: TopicEnvelope<T> = JSON.parse(frame.body);
    const publishedAt = Date.parse(envelope.publishedAt); // 오프셋이 없어 브라우저 시간대로 해석되지만 비교에는 문제없다
    if (publishedAt < lastPublishedAt) return; // 늦게 도착한 이전 메시지는 버린다
    lastPublishedAt = publishedAt;
    onMessage(envelope.payload, envelope);
  };

  topics.set(destination, handler);
  if (stompClient.connected) {
    stompClient.subscribe(destination, handler, { id: destination });
  }
  if (!stompClient.active) {
    stompClient.activate();
  }

  return () => {
    topics.delete(destination);
    if (stompClient.connected) {
      stompClient.unsubscribe(destination);
    }
    if (topics.size === 0) {
      void stompClient.deactivate();
    }
  };
}
```

### 6-2. 내 자산 구독

```tsx
import { useEffect, useState } from 'react';
import { subscribeTopic } from './stompClient';

export type Holding = {
  stockId: number;
  stockName: string;
  treasuryStock: boolean;
  quantity: number;
  orderableQuantity: number;
  currentPrice: number;
  averagePrice: number;
  valuation: number;
  profit: number;
  returnRate: number;
};

/** REST GET /assets/me 응답(data). */
export type MyAsset = {
  playerId: number;
  deposit: number;
  orderableDeposit: number;
  stockValuation: number;
  totalAsset: number;
  totalProfit: number;
  totalReturnRate: number;
  holdings: Holding[]; // 종목 ID 오름차순
};

/** 토픽 메시지(payload). REST 응답에 계산 시각만 더한다. */
export type MyAssetMessage = MyAsset & {
  asOf: string;
};
```

```tsx
/**
 * 내 자산을 받는다. initial은 처음 접속할 때 REST(GET /assets/me)로 받은 값이다.
 * asOf가 마지막으로 반영한 메시지보다 이른 메시지는 버린다.
 */
export function useMyAsset(leagueId: number, initial: MyAsset) {
  const [asset, setAsset] = useState<MyAsset | MyAssetMessage>(initial);

  useEffect(() => {
    setAsset(initial);
    return subscribeTopic<MyAssetMessage>(
      `/topic/leagues/${leagueId}/players/${initial.playerId}/assets`,
      (payload) =>
        setAsset((prev) =>
          'asOf' in prev && Date.parse(payload.asOf) < Date.parse(prev.asOf) ? prev : payload,
        ),
    );
  }, [leagueId, initial]);

  return asset;
}
```

> `initial`은 렌더링마다 새 객체를 만들지 않도록 `useMemo`나 상태로 넘깁니다. 매번 새 객체면 구독을 계속 다시 합니다.

```tsx
function MyAssetView({ leagueId, initial }: { leagueId: number; initial: MyAsset }) {
  const asset = useMyAsset(leagueId, initial);

  return (
    <section>
      <h2>내 자산</h2>
      <p>
        총자산 {asset.totalAsset.toLocaleString()}원 (
        {asset.totalProfit >= 0 ? '+' : ''}
        {asset.totalProfit.toLocaleString()}원, {asset.totalReturnRate}%)
      </p>
      <p>보유 현금 {asset.deposit.toLocaleString()}원 · 주식 평가 {asset.stockValuation.toLocaleString()}원</p>
      <ul>
        {asset.holdings.map((h) => (
          <li key={h.stockId}>
            {h.stockName}
            {h.treasuryStock && ' (내 종목)'} {h.quantity}주 · {h.returnRate}%
          </li>
        ))}
      </ul>
    </section>
  );
}
```
