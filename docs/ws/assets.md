# 내 자산 (WebSocket)

리그에서 내 평가금·예수금·보유 종목을 실시간으로 받습니다. 내 자산 화면과 주문 화면(주문 가능 현금)에서 씁니다.

> **⚠️ 더미 응답입니다.**
> 지금은 실제 데이터 대신 더미 값을 1초마다 보냅니다. 구독 경로와 메시지 형식은 실제 구현에서도 유지할 예정이지만, 구현하면서 바뀔 수 있습니다.
> 서버 코드: `websocket.application.topic.asset.AssetTopic`

| **항목** | **값** |
| --- | --- |
| 연결 엔드포인트 | `/ws` ([1. 연결 엔드포인트](#1-연결-엔드포인트)) |
| 프로토콜 | STOMP 1.2 over WebSocket (SockJS 미사용) |
| 인증 | 로그인 세션 쿠키 `SESSION` ([2. 연결 인증](#2-연결-인증)) |
| 구독 | `/topic/leagues/{leagueId}/players/{playerId}/assets` |
| 구독 권한 | 본인만 (`playerId`가 로그인 회원 ID와 같아야 함) |
| 메시지 종류 (`type`) | `ASSET` |
| 전달 주기 | 1초마다 (더미). 구독자가 없으면 보내지 않습니다 |
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
| playerId | Long | 리그 참여자 ID. **지금은 로그인 회원 ID를 넣습니다** |

### 구독 권한

**본인만** 구독할 수 있습니다. 지금은 리그 참여자 기능이 없어 `playerId`를 **로그인 회원 ID**와 비교합니다. 다르면 구독이 거부됩니다.

> TODO: 리그 모듈이 생기면 `playerId`가 로그인 회원의 해당 리그 참여자 ID인지 확인하도록 바뀝니다. 그때는 `playerId`에 회원 ID가 아니라 참여자 ID를 넣어야 합니다.
>
> TODO: 지금은 프론트가 로그인 회원 ID를 받을 수 있는 REST API가 없습니다. 참여자 조회 API와 함께 추가될 예정입니다.

권한이 없으면 `ERROR` 프레임을 받고 **연결 전체가 끊깁니다**([5. 에러](#5-에러)).

### SUBSCRIBE 프레임

| **헤더** | **값** | **필수** | **비고** |
| --- | --- | --- | --- |
| destination | `/topic/leagues/{leagueId}/players/{playerId}/assets` | O | |
| id | 구독 ID | O | 클라이언트가 정하는 값. STOMP 라이브러리가 자동으로 만듭니다 |

```
SUBSCRIBE
id:sub-0
destination:/topic/leagues/1/players/1/assets

^@
```

### 받는 메시지

모든 토픽 메시지는 같은 형식(`TopicEnvelope`)으로 옵니다. REST API의 `Result`(`code`, `message`, `data`)와 다른 형식이니 주의합니다.

| **필드** | **타입** | **설명** | **비고** |
| --- | --- | --- | --- |
| type | String | 메시지 종류 | 이 토픽은 항상 `ASSET` |
| payload | Object | 데이터 | 아래 표 참고 |
| publishedAt | Number | 발행 시각 | UTC epoch 밀리초, 예: `1794202325123` (= 2026-11-09 14:32:05.123 KST) |

#### payload

금액은 원, 수량은 주 단위입니다.

| **필드** | **타입** | **설명** | **비고** |
| --- | --- | --- | --- |
| totalAsset | Long | 평가금 | `deposit + stockValuation` |
| deposit | Long | 예수금 | 예약금을 포함한 전체 현금 |
| reservedDeposit | Long | 예약금 | 예수금 중 미체결 매수 주문에 묶인 현금 |
| stockValuation | Long | 보유 주식 평가액 합계 | |
| holdings | Array | 보유 종목 | 보유 종목이 없으면 빈 배열 |
| holdings[].stockId | Long | 종목 ID | |
| holdings[].stockName | String | 종목명 | |
| holdings[].currentPrice | Long | 현재가 | |
| holdings[].quantity | Long | 보유 수량 | |
| holdings[].valuation | Long | 평가액 | `currentPrice × quantity` |

> **주문 가능 현금** = `deposit - reservedDeposit`. 따로 필드가 없으니 프론트에서 계산합니다.

```
MESSAGE
destination:/topic/leagues/1/players/1/assets
content-type:application/json
subscription:sub-0
message-id:a1b2c3-0

{
  "type": "ASSET",
  "payload": {
    "totalAsset": 106900000,
    "deposit": 52000000,
    "reservedDeposit": 1250000,
    "stockValuation": 54900000,
    "holdings": [
      { "stockId": 1, "stockName": "진영전자", "currentPrice": 12500, "quantity": 3000, "valuation": 37500000 },
      { "stockId": 2, "stockName": "민영건설", "currentPrice": 12500, "quantity": 1000, "valuation": 12500000 },
      { "stockId": 3, "stockName": "수진식품", "currentPrice": 9800, "quantity": 500, "valuation": 4900000 }
    ]
  },
  "publishedAt": 1794202325123
}^@
```

### 메시지 처리 방법

- 메시지는 바뀐 부분만이 아니라 **그 시점의 전체 상태**를 담습니다. 받은 값으로 화면 상태를 통째로 덮어씁니다.
- 네트워크 상황에 따라 순서가 뒤바뀔 수 있으므로, `publishedAt`이 마지막으로 반영한 메시지보다 이른 메시지는 버립니다.
- 연결이 끊긴 동안의 메시지는 다시 받을 수 없습니다. (재)연결 직후에는 REST API로 현재 상태를 받고, 이후 메시지로 덮어씁니다.
- `holdings`는 **전체 보유 종목**입니다. 목록을 통째로 바꿉니다. 이전 메시지에 있던 종목이 없으면 모두 판 것입니다.

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
message:토픽 구독 권한이 없습니다\c /topic/leagues/1/players/1/assets
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

1. **다른 사람의 `playerId`로 구독한 경우:** `ERROR` 프레임(`토픽 구독 권한이 없습니다: ...`)을 받고 연결 전체가 끊깁니다. 다른 토픽 구독도 함께 끊기므로 `playerId`를 꼭 확인합니다.
2. **없는 리그 ID로 구독한 경우:** 지금은 리그 존재 여부를 확인하지 않아 구독이 되고, 더미 응답을 받습니다.
3. **더미 응답:** 리그와 관계없이 항상 위 예시와 같은 값을 보냅니다. 값도 바뀌지 않습니다.
4. **연결이 끊긴 동안의 자산 변화:** 다시 받을 수 없습니다. 다시 연결한 직후 REST API로 현재 자산을 받습니다. (TODO: 자산 조회 REST API 추가 예정)

---

## 6. FE 연동 예시

[`@stomp/stompjs`](https://stomp-js.github.io/) 7.x를 사용합니다.

```bash
npm install @stomp/stompjs
```

### 6-1. 공통 연결 (`stompClient.ts`)

모든 토픽이 연결 하나를 같이 씁니다. 다른 토픽 문서에서 이미 만들었다면 그대로 재사용합니다.

```ts
import { Client, IMessage } from '@stomp/stompjs';

/** 모든 토픽 메시지의 공통 형식. */
export type TopicEnvelope<T> = {
  type: string;
  payload: T;
  publishedAt: number; // UTC epoch 밀리초
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
    const publishedAt = envelope.publishedAt;
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
  currentPrice: number;
  quantity: number;
  valuation: number;
};

export type Asset = {
  totalAsset: number;
  deposit: number;
  reservedDeposit: number;
  stockValuation: number;
  holdings: Holding[];
};

/** 리그에서 내 자산을 실시간으로 받는다. playerId는 지금은 로그인 회원 ID다. */
export function useMyAsset(leagueId: number, playerId: number) {
  const [asset, setAsset] = useState<Asset | null>(null);

  useEffect(
    () =>
      subscribeTopic<Asset>(`/topic/leagues/${leagueId}/players/${playerId}/assets`, setAsset),
    [leagueId, playerId],
  );

  return asset;
}
```

```tsx
function MyAsset({ leagueId, playerId }: { leagueId: number; playerId: number }) {
  const asset = useMyAsset(leagueId, playerId);
  if (!asset) return null;

  const orderableCash = asset.deposit - asset.reservedDeposit; // 주문 가능 현금
  return (
    <div>
      <p>평가금 {asset.totalAsset.toLocaleString()}원</p>
      <p>주문 가능 {orderableCash.toLocaleString()}원</p>
      <ul>
        {asset.holdings.map((h) => (
          <li key={h.stockId}>
            {h.stockName} {h.quantity}주 · {h.valuation.toLocaleString()}원
          </li>
        ))}
      </ul>
    </div>
  );
}
```

