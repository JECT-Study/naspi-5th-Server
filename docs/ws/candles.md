# 캔들 차트 (WebSocket)

종목 하나의 캔들(시가·고가·저가·종가·거래량)을 봉 단위별(10분, 1시간, 1일)로 실시간으로 받습니다. 종목 상세 화면의 차트에서 씁니다.

> **⚠️ 더미 응답입니다.**
> 지금은 실제 데이터 대신 더미 값을 5초마다 보냅니다. 구독 경로와 메시지 형식은 실제 구현에서도 유지할 예정이지만, 구현하면서 바뀔 수 있습니다.
> 서버 코드: `websocket.application.topic.chart.CandleChartTopic`

| **항목** | **값** |
| --- | --- |
| 연결 엔드포인트 | `/ws` ([1. 연결 엔드포인트](#1-연결-엔드포인트)) |
| 프로토콜 | STOMP 1.2 over WebSocket (SockJS 미사용) |
| 인증 | 로그인 세션 쿠키 `SESSION` ([2. 연결 인증](#2-연결-인증)) |
| 구독 | `/topic/leagues/{leagueId}/stocks/{stockId}/candles` |
| 구독 권한 | 로그인 회원 누구나 |
| 메시지 종류 (`type`) | `CANDLE_CHART` |
| 전달 주기 | 5초마다 (더미). 구독자가 없으면 보내지 않습니다 |
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

`SUBSCRIBE /topic/leagues/{leagueId}/stocks/{stockId}/candles`

### 경로 변수

| **변수** | **타입** | **설명** |
| --- | --- | --- |
| leagueId | Long | 리그 ID |
| stockId | Long | 종목 ID |

### 구독 권한

로그인 회원이면 누구나 구독할 수 있습니다.

> TODO: 리그 모듈이 생기면 해당 리그 참여자만 구독할 수 있게 바뀝니다.

권한이 없으면 `ERROR` 프레임을 받고 **연결 전체가 끊깁니다**([5. 에러](#5-에러)).

### SUBSCRIBE 프레임

| **헤더** | **값** | **필수** | **비고** |
| --- | --- | --- | --- |
| destination | `/topic/leagues/{leagueId}/stocks/{stockId}/candles` | O | |
| id | 구독 ID | O | 클라이언트가 정하는 값. STOMP 라이브러리가 자동으로 만듭니다 |

```
SUBSCRIBE
id:sub-0
destination:/topic/leagues/1/stocks/1/candles

^@
```

### 받는 메시지

모든 토픽 메시지는 같은 형식(`TopicEnvelope`)으로 옵니다. REST API의 `Result`(`code`, `message`, `data`)와 다른 형식이니 주의합니다.

| **필드** | **타입** | **설명** | **비고** |
| --- | --- | --- | --- |
| type | String | 메시지 종류 | 이 토픽은 항상 `CANDLE_CHART` |
| payload | Object | 데이터 | 아래 표 참고 |
| publishedAt | Number | 발행 시각 | UTC epoch 밀리초, 예: `1794202325123` (= 2026-11-09 14:32:05.123 KST) |

#### payload

과거 캔들은 처음 접속할 때 REST API로 받고, 이 토픽은 **봉 단위별로 최근 2개 캔들만** 보냅니다.

- **이전 봉** (`candles[0]`): 이미 끝난 구간이라 값이 바뀌지 않습니다.
- **현재 봉** (`candles[1]`): 현재 시각이 속한 구간입니다. 거래가 일어나면 종가·고가·저가·거래량이 바뀝니다.

| **필드** | **타입** | **설명** | **비고** |
| --- | --- | --- | --- |
| stockName | String | 종목명 | |
| series | Array | 봉 단위별 캔들 목록 | `MINUTE_10`, `HOUR_1`, `DAY_1` 세 개 |
| series[].interval | String | 봉 단위 | `MINUTE_10`(10분봉), `HOUR_1`(1시간봉), `DAY_1`(1일봉) |
| series[].candles | Array | 캔들 목록 | 시간 오름차순. `[이전 봉, 현재 봉]` |
| series[].candles[].startedAt | Number | 봉 시작 시각 | UTC epoch 밀리초. 봉 경계는 KST 기준 |
| series[].candles[].openPrice | Long | 시가 (원) | |
| series[].candles[].highPrice | Long | 고가 (원) | |
| series[].candles[].lowPrice | Long | 저가 (원) | |
| series[].candles[].closePrice | Long | 종가 (원) | |
| series[].candles[].volume | Long | 거래량 (주) | |

봉 시작 시각 기준 (KST)

| **봉 단위** | **구간** | **예시 (14:32에 받은 경우)** |
| --- | --- | --- |
| MINUTE_10 | 매시 00, 10, 20, ..., 50분 시작 | 이전 `14:20`, 현재 `14:30` |
| HOUR_1 | 매시 정각 시작 | 이전 `13:00`, 현재 `14:00` |
| DAY_1 | 매일 09:00 시작 (거래일) | 이전 `전날 09:00`, 현재 `오늘 09:00` |

```
MESSAGE
destination:/topic/leagues/1/stocks/1/candles
content-type:application/json
subscription:sub-0
message-id:a1b2c3-0

{
  "type": "CANDLE_CHART",
  "payload": {
    "stockName": "진영전자",
    "series": [
      {
        "interval": "MINUTE_10",
        "candles": [
          { "startedAt": 1794201600000, "openPrice": 10000, "highPrice": 10200, "lowPrice": 9900, "closePrice": 10100, "volume": 100 },
          { "startedAt": 1794202200000, "openPrice": 10100, "highPrice": 10300, "lowPrice": 10000, "closePrice": 10200, "volume": 57 }
        ]
      },
      {
        "interval": "HOUR_1",
        "candles": [
          { "startedAt": 1794196800000, "openPrice": 10000, "highPrice": 10200, "lowPrice": 9900, "closePrice": 10100, "volume": 100 },
          { "startedAt": 1794200400000, "openPrice": 10100, "highPrice": 10300, "lowPrice": 10000, "closePrice": 10200, "volume": 57 }
        ]
      },
      {
        "interval": "DAY_1",
        "candles": [
          { "startedAt": 1794096000000, "openPrice": 10000, "highPrice": 10200, "lowPrice": 9900, "closePrice": 10100, "volume": 100 },
          { "startedAt": 1794182400000, "openPrice": 10100, "highPrice": 10300, "lowPrice": 10000, "closePrice": 10200, "volume": 57 }
        ]
      }
    ]
  },
  "publishedAt": 1794202325123
}^@
```

### 메시지 처리 방법

- 메시지는 바뀐 부분만이 아니라 **그 시점의 전체 상태**를 담습니다. 받은 값으로 화면 상태를 통째로 덮어씁니다.
- 네트워크 상황에 따라 순서가 뒤바뀔 수 있으므로, `publishedAt`이 마지막으로 반영한 메시지보다 이른 메시지는 버립니다.
- 연결이 끊긴 동안의 메시지는 다시 받을 수 없습니다. (재)연결 직후에는 REST API로 현재 상태를 받고, 이후 메시지로 덮어씁니다.
- 받은 캔들 2개를 `startedAt` 기준으로 차트 데이터에 합칩니다. 같은 `startedAt`의 캔들이 있으면 **덮어쓰고**, 없으면 **뒤에 추가**합니다.
- 봉 구간이 넘어가면 진행 중이던 봉이 마지막 값 그대로 이전 봉이 되고, 새 봉이 직전 종가로 열립니다. 위 합치기 규칙대로 처리하면 따로 신경 쓸 필요가 없습니다.
- 화면에 한 봉 단위만 보여 준다면 `series`에서 해당 `interval`만 골라 씁니다.

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
message:토픽 구독 권한이 없습니다\c /topic/leagues/1/stocks/1/candles
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

1. **없는 리그·종목 ID로 구독한 경우:** 지금은 존재 여부를 확인하지 않아 구독이 되고, 더미 응답을 받습니다.
2. **더미 응답:** 종목과 관계없이 종목명은 항상 `진영전자`이고, 모든 구독자가 같은 값을 받습니다. 현재 봉 종가는 보낼 때마다 ±200원 안에서 임의로 움직이고, 거래량은 1~20주씩 늘어납니다.
3. **서버가 다시 시작된 경우:** 더미 캔들이 처음 값(10,000원)부터 다시 시작합니다.
4. **1일봉 구간:** 지금은 매일 09:00(KST)을 거래일 시작으로 계산합니다. (TODO: 리그 모듈의 거래일 계산으로 교체 예정)
5. **연결이 끊긴 동안의 캔들:** 다시 받을 수 없습니다. 다시 연결한 직후 REST API로 과거 캔들을 다시 받습니다. (TODO: 캔들 조회 REST API 추가 예정)

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

### 6-2. 캔들 차트 구독

```tsx
import { useEffect, useState } from 'react';
import { subscribeTopic } from './stompClient';

export type CandleInterval = 'MINUTE_10' | 'HOUR_1' | 'DAY_1';

export type Candle = {
  startedAt: number; // UTC epoch 밀리초
  openPrice: number;
  highPrice: number;
  lowPrice: number;
  closePrice: number;
  volume: number;
};

type CandleChartPayload = {
  stockName: string;
  series: { interval: CandleInterval; candles: Candle[] }[];
};

/** 같은 startedAt이면 덮어쓰고, 없으면 뒤에 추가한다. */
function mergeCandles(chart: Candle[], recent: Candle[]): Candle[] {
  const merged = [...chart];
  for (const candle of recent) {
    const index = merged.findIndex((c) => c.startedAt === candle.startedAt);
    if (index >= 0) merged[index] = candle;
    else merged.push(candle);
  }
  return merged;
}

/**
 * 종목 하나의 캔들 차트. initialCandles는 처음 접속할 때 REST로 받은 과거 캔들이다.
 */
export function useCandleChart(
  leagueId: number,
  stockId: number,
  interval: CandleInterval,
  initialCandles: Candle[] = [],
) {
  const [candles, setCandles] = useState<Candle[]>(initialCandles);

  useEffect(() => {
    setCandles(initialCandles);
    return subscribeTopic<CandleChartPayload>(
      `/topic/leagues/${leagueId}/stocks/${stockId}/candles`,
      (payload) => {
        const series = payload.series.find((s) => s.interval === interval);
        if (series) setCandles((prev) => mergeCandles(prev, series.candles));
      },
    );
  }, [leagueId, stockId, interval, initialCandles]);

  return candles;
}
```

> `initialCandles`는 렌더링마다 새 배열을 만들지 않도록 `useMemo`나 상태로 넘깁니다. 매번 새 배열이면 구독을 계속 다시 합니다.

