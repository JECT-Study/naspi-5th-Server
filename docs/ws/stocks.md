# 종목 정보 토픽

리그에 상장된 전체 종목의 현재가와 등락을 실시간으로 받습니다. 종목 목록 화면에서 씁니다.

| **항목** | **값** |
| --- | --- |
| 연결 엔드포인트 | `/ws` ([1. 연결 엔드포인트](#1-연결-엔드포인트)) |
| 프로토콜 | STOMP 1.2 over WebSocket (SockJS 미사용) |
| 인증 | 로그인 세션 쿠키 `SESSION` ([2. 연결 인증](#2-연결-인증)) |
| 구독 | `/topic/leagues/{leagueId}/stocks` |
| 구독 권한 | 그 리그에 입장한 회원 (상장 전 대기자 포함). 종료 시각이 지난 리그는 불가 |
| 메시지 종류 (`type`) | `STOCK` |
| 전달 시점 | 현재가가 바뀌거나 체결될 때 ([전달 시점](#전달-시점)). 구독자가 없으면 보내지 않습니다 |
| 대응 REST | `GET /api/leagues/{leagueId}/stocks` |
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

`SUBSCRIBE /topic/leagues/{leagueId}/stocks`

### 경로 변수

| **변수** | **타입** | **설명** |
| --- | --- | --- |
| leagueId | Long | 리그 ID |

### 구독 권한

그 리그에 입장한 회원만 구독할 수 있습니다. 상장 전인 대기자도 입장한 상태이므로 구독할 수 있습니다. 리그가 없거나(만료·삭제된 대기실 포함) 종료 시각이 지난 리그는 구독할 수 없습니다. REST `GET /api/leagues/{leagueId}/stocks`의 조회 조건과 같습니다.

권한이 없으면 `ERROR` 프레임을 받고 **연결 전체가 끊깁니다**([5. 에러](#5-에러)).

### SUBSCRIBE 프레임

| **헤더** | **값** | **필수** | **비고** |
| --- | --- | --- | --- |
| destination | `/topic/leagues/{leagueId}/stocks` | O |  |
| id | 구독 ID | O | 클라이언트가 정하는 값. STOMP 라이브러리가 자동으로 만듭니다 |

```
SUBSCRIBE
id:sub-0
destination:/topic/leagues/1/stocks

^@
```

### 전달 시점

| **원인** | **전달 시점** | **받는 사람** |
| --- | --- | --- |
| 현재가 변경 | 최대 약 1초 안 | 그 리그의 구독자 전원 |
| 체결 | 커밋 직후 (같은 가격 체결의 거래량 반영) | 그 리그의 구독자 전원 |

### 받는 메시지

모든 토픽 메시지는 같은 형식(`TopicEnvelope`)으로 옵니다. REST API의 `Result`(`code`, `message`, `data`)와 다른 형식이니 주의합니다.

| **필드** | **타입** | **설명** | **비고** |
| --- | --- | --- | --- |
| type | String | 메시지 종류 | 이 토픽은 항상 `STOCK` |
| payload | Object | 데이터 | 아래 표 참고 |
| publishedAt | String | 발행 시각 (KST) | REST 응답의 일시와 같은 형식(오프셋 없음, 밀리초까지), 예: `2026-11-09T14:32:05.123` |

#### payload

REST `GET /api/leagues/{leagueId}/stocks`의 `data` 각 항목에서 `mine`만 빼고, `asOf`를 더해 객체로 감싸 보냅니다. 금액은 원 단위입니다. 시세 필드는 리그 시작 전이면 `null`입니다. 이름·소개·상장자 이름도 매번 함께 보냅니다.

| **필드** | **타입** | **설명** | **비고** |
| --- | --- | --- | --- |
| stocks | Array | 종목 목록 | 리그 전체 종목, 종목 ID 오름차순 |
| stocks[].stockId | Long | 종목 ID | |
| stocks[].name | String | 종목 이름 | |
| stocks[].description | String | 종목 소개 | |
| stocks[].listerName | String | 상장자 이름 | 시작된 리그에서 탈퇴한 상장자면 '탈퇴한 사용자' |
| stocks[].currentPrice | Long | 현재가 (원) | |
| stocks[].basePrice | Long | 기준가 (원) | 직전 거래일 종가, 첫 거래일은 시초가 |
| stocks[].upperLimitPrice | Long | 상한가 (원) | |
| stocks[].lowerLimitPrice | Long | 하한가 (원) | |
| stocks[].changeAmount | Long | 등락액 (원) | 기준가 대비. 하락이면 음수 |
| stocks[].changeRate | Number | 등락률 (%) | `(현재가 - 기준가) / 기준가 × 100`. 소수점 첫째 자리. 하락이면 음수 |
| stocks[].volume | Long | 현재 거래일 거래량 (주) | |
| asOf | String | 값을 계산한 시각 (KST) | REST와 같은 일시 형식 |

```
MESSAGE
destination:/topic/leagues/1/stocks
content-type:application/json
subscription:sub-0
message-id:a1b2c3-0

{
  "type": "STOCK",
  "payload": {
    "stocks": [
      {
        "stockId": 1, "name": "수빈전자", "description": "성실함이 주력 사업입니다.", "listerName": "수빈",
        "currentPrice": 12500, "basePrice": 10000, "upperLimitPrice": 13000, "lowerLimitPrice": 7000,
        "changeAmount": 2500, "changeRate": 25.0, "volume": 5
      },
      {
        "stockId": 2, "name": "민준건설", "description": "튼튼하게 짓습니다.", "listerName": "민준",
        "currentPrice": 9800, "basePrice": 10000, "upperLimitPrice": 13000, "lowerLimitPrice": 7000,
        "changeAmount": -200, "changeRate": -2.0, "volume": 12
      }
    ],
    "asOf": "2026-11-27T21:30:01.123456"
  },
  "publishedAt": "2026-11-27T21:30:01.125"
}^@
```

### 메시지 처리 방법

- 메시지는 바뀐 부분이 아니라 **그 시점의 상태**를 담습니다. 이전 메시지와 비교해 변경분을 계산하지 않습니다.
- 네트워크 상황에 따라 순서가 뒤바뀔 수 있으므로, `publishedAt`이 마지막으로 반영한 메시지보다 이른 메시지는 버립니다.
- 연결이 끊긴 동안의 메시지는 다시 받을 수 없습니다. (재)연결 직후에는 REST API로 현재 상태를 받고, 이후 메시지를 반영합니다.
- 받은 값으로 화면 상태를 통째로 덮어씁니다.
- **`asOf`가 마지막으로 반영한 메시지보다 이른 메시지는 버립니다.** REST 응답에는 `asOf`가 없으므로 REST 값 다음에 오는 첫 메시지는 그대로 반영합니다.
- `stocks`는 리그의 **전체 종목** 목록입니다. 목록을 통째로 바꿉니다. 단, `mine`(내 종목 여부)은 메시지에 없으므로 REST로 받은 값을 `stockId`로 맞춰 유지합니다.

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
message:토픽 구독 권한이 없습니다\c /topic/leagues/1/stocks
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

1. **없는 리그 ID, 입장하지 않은 리그, 종료 시각이 지난 리그로 구독한 경우:** `ERROR` 프레임(`토픽 구독 권한이 없습니다: ...`)을 받고 연결 전체가 끊깁니다.
2. **리그 시작 전:** 구독은 되지만 시세가 바뀌지 않아 메시지가 오지 않습니다. 시작되면 REST로 다시 조회합니다.
3. **거래일 전환(09:00):** 기준가·상하한가·등락률·거래량이 초기화되지만 체결 전까지는 메시지가 오지 않습니다. 09:00를 넘기면 REST로 다시 조회합니다.
4. **종목 소개 수정:** 진행 중인 리그에서 소개를 고쳐도 다음 시세 변경 전까지는 메시지가 오지 않습니다.
5. **연결이 끊긴 동안의 변화:** 다시 받을 수 없습니다. 다시 연결한 직후 `GET /api/leagues/{leagueId}/stocks`로 현재 상태를 받습니다.

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

### 6-2. 종목 정보 구독

```tsx
import { useEffect, useState } from 'react';
import { subscribeTopic } from './stompClient';

/** 토픽 메시지의 종목 항목. REST 종목 항목에서 mine을 뺀 것. 시세 필드는 리그 시작 전이면 null. */
export type Stock = {
  stockId: number;
  name: string;
  description: string;
  listerName: string;
  currentPrice: number | null;
  basePrice: number | null;
  upperLimitPrice: number | null;
  lowerLimitPrice: number | null;
  changeAmount: number | null;
  changeRate: number | null;
  volume: number | null;
};

/** REST GET /api/leagues/{leagueId}/stocks 응답(data)의 항목. */
export type MemberStock = Stock & { mine: boolean };

type StockPayload = { stocks: Stock[]; asOf: string };
```

```tsx
/**
 * 리그 전체 종목을 실시간으로 받는다. initial은 처음 접속할 때 REST로 받은 값이다.
 * asOf가 마지막으로 반영한 메시지보다 이른 메시지는 버리고, mine은 REST 값을 stockId로 맞춰 유지한다.
 */
export function useStocks(leagueId: number, initial: MemberStock[]) {
  const [state, setState] = useState<{ stocks: MemberStock[]; asOf?: string }>({
    stocks: initial,
  });

  useEffect(() => {
    setState({ stocks: initial });
    const mine = new Map(initial.map((s) => [s.stockId, s.mine]));
    return subscribeTopic<StockPayload>(`/topic/leagues/${leagueId}/stocks`, (payload) =>
      setState((prev) =>
        prev.asOf && Date.parse(payload.asOf) < Date.parse(prev.asOf)
          ? prev
          : {
              stocks: payload.stocks.map((s) => ({ ...s, mine: mine.get(s.stockId) ?? false })),
              asOf: payload.asOf,
            },
      ),
    );
  }, [leagueId, initial]);

  return state.stocks;
}
```

> `initial`은 렌더링마다 새 객체를 만들지 않도록 `useMemo`나 상태로 넘깁니다. 매번 새 객체면 구독을 계속 다시 합니다.

```tsx
function StockList({ leagueId, initial }: { leagueId: number; initial: MemberStock[] }) {
  const stocks = useStocks(leagueId, initial);
  return (
    <ul>
      {stocks.map((s) => (
        <li key={s.stockId}>
          {s.name}
          {s.mine && ' (내 종목)'} {s.currentPrice?.toLocaleString() ?? '-'}원 (
          {(s.changeRate ?? 0) > 0 ? '+' : ''}
          {s.changeRate ?? 0}%)
        </li>
      ))}
    </ul>
  );
}
```
