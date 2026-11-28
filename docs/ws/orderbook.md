# 호가 토픽

종목 하나의 매도·매수 호가(가격별 미체결 잔량)를 실시간으로 받습니다. 종목 상세 화면의 호가창에서 씁니다.

| **항목** | **값** |
| --- | --- |
| 연결 엔드포인트 | `/ws` ([1. 연결 엔드포인트](#1-연결-엔드포인트)) |
| 프로토콜 | STOMP 1.2 over WebSocket (SockJS 미사용) |
| 인증 | 로그인 세션 쿠키 `SESSION` ([2. 연결 인증](#2-연결-인증)) |
| 구독 | `/topic/leagues/{leagueId}/stocks/{stockId}/orderbook` |
| 구독 권한 | 진행 중인 리그의 참여자 |
| 메시지 종류 (`type`) | `ORDER_BOOK` |
| 전달 시점 | 호가나 현재가가 바뀔 때 ([전달 시점](#전달-시점)). 구독자가 없으면 보내지 않습니다 |
| 대응 REST | `GET /api/leagues/{leagueId}/stocks/{stockId}/orderbook` |
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

`SUBSCRIBE /topic/leagues/{leagueId}/stocks/{stockId}/orderbook`

### 경로 변수

| **변수** | **타입** | **설명** |
| --- | --- | --- |
| leagueId | Long | 리그 ID |
| stockId | Long | 종목 ID |

### 구독 권한

REST 호가 조회(`GET /api/leagues/{leagueId}/stocks/{stockId}/orderbook`)와 같은 조건입니다. 다음을 모두 만족해야 구독할 수 있습니다.

- 로그인한 회원이 그 리그에 입장한 참여자입니다
- 리그가 진행 중입니다 (시작 후, 종료 전)
- `stockId`가 그 리그의 종목입니다

권한이 없으면 `ERROR` 프레임을 받고 **연결 전체가 끊깁니다**([5. 에러](#5-에러)).

### SUBSCRIBE 프레임

| **헤더** | **값** | **필수** | **비고** |
| --- | --- | --- | --- |
| destination | `/topic/leagues/{leagueId}/stocks/{stockId}/orderbook` | O |  |
| id | 구독 ID | O | 클라이언트가 정하는 값. STOMP 라이브러리가 자동으로 만듭니다 |

```
SUBSCRIBE
id:sub-0
destination:/topic/leagues/1/stocks/1/orderbook

^@
```

### 전달 시점

호가나 현재가가 바뀌면 아래 표의 구독자에게 보냅니다.

| **원인** | **받는 사람** |
| --- | --- |
| 체결 | 종목별 구독자 |
| 지정가 주문 접수 | 종목별 구독자 |
| 주문 취소, 거래일 변경으로 미체결 만료 | 종목별 구독자 |
| 현재가 변경 | 리그 내 모든 구독자 |

- 시장가 주문은 호가에 남지 않습니다. 체결된 만큼만 체결 메시지로 반영됩니다.
- 체결이 몰리면 같은 내용의 메시지가 연달아 올 수 있습니다. 받은 값으로 덮어쓰면 됩니다.
- 리그가 종료되면 더 이상 보내지 않습니다.

### 받는 메시지

모든 토픽 메시지는 같은 형식(`TopicEnvelope`)으로 옵니다. REST API의 `Result`(`code`, `message`, `data`)와 다른 형식이니 주의합니다.

| **필드** | **타입** | **설명** | **비고** |
| --- | --- | --- | --- |
| type | String | 메시지 종류 | 이 토픽은 항상 `ORDER_BOOK` |
| payload | Object | 데이터 | 아래 표 참고 |
| publishedAt | String | 발행 시각 (KST) | REST 응답의 일시와 같은 형식(오프셋 없음, 밀리초까지), 예: `2026-11-09T14:32:05.123` |

#### payload

금액은 원, 수량은 주 단위입니다.

REST `GET /api/leagues/{leagueId}/stocks/{stockId}/orderbook`의 `data`와 같은 필드에 `asOf`만 더해 보냅니다.

| **필드** | **타입** | **설명** | **비고** |
| --- | --- | --- | --- |
| currentPrice | Long | 현재가 (원) | 첫 체결 전에는 시초가 |
| asks | Array | 매도 호가 | **낮은 가격(최우선 매도 호가)부터** 최대 5단계. 없으면 빈 배열 |
| asks[].orderPrice | Long | 주문가격 (원) | 100원 단위 |
| asks[].remainingQuantity | Long | 이 가격 주문의 잔량 합계 (주) | |
| bids | Array | 매수 호가 | **높은 가격(최우선 매수 호가)부터** 최대 5단계. 없으면 빈 배열 |
| bids[].orderPrice | Long | 주문가격 (원) | 100원 단위 |
| bids[].remainingQuantity | Long | 이 가격 주문의 잔량 합계 (주) | |
| asOf | String | 값을 계산한 시각 (KST) | REST와 같은 일시 형식 |

```
MESSAGE
destination:/topic/leagues/1/stocks/1/orderbook
content-type:application/json
subscription:sub-0
message-id:a1b2c3-0

{
  "type": "ORDER_BOOK",
  "payload": {
    "currentPrice": 12500,
    "asks": [
      { "orderPrice": 12600, "remainingQuantity": 30 },
      { "orderPrice": 12700, "remainingQuantity": 120 },
      { "orderPrice": 12800, "remainingQuantity": 45 },
      { "orderPrice": 12900, "remainingQuantity": 80 },
      { "orderPrice": 13000, "remainingQuantity": 200 }
    ],
    "bids": [
      { "orderPrice": 12500, "remainingQuantity": 50 },
      { "orderPrice": 12400, "remainingQuantity": 90 },
      { "orderPrice": 12300, "remainingQuantity": 15 },
      { "orderPrice": 12200, "remainingQuantity": 60 },
      { "orderPrice": 12100, "remainingQuantity": 150 }
    ],
    "asOf": "2026-11-09T14:32:05.120"
  },
  "publishedAt": "2026-11-09T14:32:05.123"
}^@
```

### 메시지 처리 방법

- 메시지는 바뀐 부분이 아니라 **그 시점의 상태**를 담습니다. 이전 메시지와 비교해 변경분을 계산하지 않습니다.
- 네트워크 상황이나 서버의 동시 발행으로 순서가 뒤바뀔 수 있으므로, **`asOf`가 마지막으로 반영한 메시지보다 이른 메시지는 버립니다.** REST 응답에는 `asOf`가 없으므로 REST 값 다음에 오는 첫 메시지는 그대로 반영합니다.
  - 이 토픽은 `publishedAt`(발행 시각)이 아니라 `asOf`(계산 시각)로 판단합니다. 먼저 계산했지만 늦게 발행된 메시지는 `publishedAt`이 더 최근이어도 이전 값입니다.
- 연결이 끊긴 동안의 메시지는 다시 받을 수 없습니다. (재)연결 직후에는 REST API로 현재 상태를 받고, 이후 메시지를 반영합니다.
- 받은 값으로 화면 상태를 통째로 덮어씁니다.
- 호가창을 위에서 아래로 높은 가격부터 그린다면, `asks`는 **뒤집어서**(높은 가격부터) 위쪽에, `bids`는 그대로 아래쪽에 놓습니다.
- 다른 종목으로 화면이 바뀌면 이전 종목 구독을 해지하고 새 종목을 구독합니다.

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
message:토픽 구독 권한이 없습니다\c /topic/leagues/1/stocks/1/orderbook
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

1. **구독 권한이 없는 경우:** 입장하지 않은 리그, 시작 전·종료된 리그, 그 리그에 없는 `stockId`로 구독하면 `ERROR` 프레임(`토픽 구독 권한이 없습니다: ...`)을 받고 연결 전체가 끊깁니다. 다른 토픽 구독도 함께 끊기므로 리그가 진행 중일 때만 구독합니다.
2. **호가 단계 수:** 미체결 지정가 주문이 있는 가격만 오므로 0~5단계입니다. 주문이 없는 쪽은 빈 배열입니다.
3. **리그 종료 후:** 메시지가 오지 않습니다. 종료된 리그는 REST 호가 조회도 거절됩니다.
4. **연결이 끊긴 동안의 변화:** 다시 받을 수 없습니다. 다시 연결한 직후 REST API로 현재 호가를 받습니다.

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

### 6-2. 호가 구독

```tsx
import { useEffect, useState } from 'react';
import { subscribeTopic } from './stompClient';

export type OrderBookLevel = { orderPrice: number; remainingQuantity: number };

/** REST GET /orderbook 응답(data). */
export type OrderBook = {
  currentPrice: number;
  asks: OrderBookLevel[]; // 낮은 가격부터
  bids: OrderBookLevel[]; // 높은 가격부터
};

/** 토픽 메시지(payload). REST 응답에 계산 시각만 더한다. */
export type OrderBookMessage = OrderBook & {
  asOf: string;
};
```

```tsx
/**
 * 종목 하나의 호가를 실시간으로 받는다. initial은 처음 접속할 때 REST(GET /orderbook)로 받은 값이다.
 * asOf가 마지막으로 반영한 메시지보다 이른 메시지는 버린다.
 */
export function useOrderBook(leagueId: number, stockId: number, initial: OrderBook) {
  const [orderBook, setOrderBook] = useState<OrderBook | OrderBookMessage>(initial);

  useEffect(() => {
    setOrderBook(initial);
    return subscribeTopic<OrderBookMessage>(
      `/topic/leagues/${leagueId}/stocks/${stockId}/orderbook`,
      (payload) =>
        setOrderBook((prev) =>
          'asOf' in prev && Date.parse(payload.asOf) < Date.parse(prev.asOf) ? prev : payload,
        ),
    );
  }, [leagueId, stockId, initial]);

  return orderBook;
}
```

> `initial`은 렌더링마다 새 객체를 만들지 않도록 `useMemo`나 상태로 넘깁니다. 매번 새 객체면 구독을 계속 다시 합니다.

```tsx
function OrderBookTable({
  leagueId,
  stockId,
  initial,
}: {
  leagueId: number;
  stockId: number;
  initial: OrderBook;
}) {
  const orderBook = useOrderBook(leagueId, stockId, initial);

  return (
    <table>
      <tbody>
        {/* 매도: 높은 가격이 위로 오도록 뒤집는다 */}
        {[...orderBook.asks].reverse().map((l) => (
          <tr key={`ask-${l.orderPrice}`}>
            <td>{l.remainingQuantity}</td>
            <td>{l.orderPrice.toLocaleString()}</td>
            <td />
          </tr>
        ))}
        {orderBook.bids.map((l) => (
          <tr key={`bid-${l.orderPrice}`}>
            <td />
            <td>{l.orderPrice.toLocaleString()}</td>
            <td>{l.remainingQuantity}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
```

