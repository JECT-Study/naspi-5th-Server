# 알림 토픽

내가 요청하지 않았는데 내 주문 상태가 바뀐 경우(대기 주문 체결, 거래일 갱신에 따른 주문 만료)와, 내가 쓴 공시·제보의 AI 뉴스 생성이 끝난 경우(게시, 미생성, 실패)를 실시간으로 받습니다. 토스트에서 씁니다.

내가 넣은 주문의 접수·체결·취소 결과는 이 토픽으로 오지 않습니다. 주문·취소 API 응답으로 토스트를 띄웁니다.

| **항목** | **값** |
| --- | --- |
| 연결 엔드포인트 | `/ws` ([1. 연결 엔드포인트](#1-연결-엔드포인트)) |
| 프로토콜 | STOMP 1.2 over WebSocket (SockJS 미사용) |
| 인증 | 로그인 세션 쿠키 `SESSION` ([2. 연결 인증](#2-연결-인증)) |
| 구독 | `/topic/leagues/{leagueId}/players/{playerId}/alerts` |
| 구독 권한 | 본인만 |
| 메시지 종류 (`type`) | `ALERT` |
| 전달 시점 | 알림이 생길 때 ([전달 시점](#전달-시점)). 구독 중일 때만 받으며 보관하지 않습니다 |
| 대응 REST | 없음 |
| 발행 (클라이언트 → 서버) | 없음 |
| 정책 | [order.md#주문-알림](../policies/v1/domain/order.md#주문-알림), [contents.md#뉴스-생성-알림](../policies/v1/domain/contents.md#뉴스-생성-알림) |

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

`SUBSCRIBE /topic/leagues/{leagueId}/players/{playerId}/alerts`

### 경로 변수

| **변수** | **타입** | **설명** |
| --- | --- | --- |
| leagueId | Long | 리그 ID |
| playerId | Long | **내 플레이어 ID**. 회원 ID가 아닙니다. `GET /api/leagues/{leagueId}/assets/me` 응답의 `playerId`를 씁니다 |

### 구독 권한

로그인한 회원 **본인의 알림**만 구독할 수 있습니다.

권한이 없으면 `ERROR` 프레임을 받고 **연결 전체가 끊깁니다**([5. 에러](#5-에러)).

### SUBSCRIBE 프레임

| **헤더** | **값** | **필수** | **비고** |
| --- | --- | --- | --- |
| destination | `/topic/leagues/{leagueId}/players/{playerId}/alerts` | O |  |
| id | 구독 ID | O | 클라이언트가 정하는 값. STOMP 라이브러리가 자동으로 만듭니다 |

```
SUBSCRIBE
id:sub-0
destination:/topic/leagues/1/players/12/alerts

^@
```

### 전달 시점

| **원인** | **받는 사람** | **alertType** | **횟수** |
| --- | --- | --- | --- |
| 대기 중이던 내 주문이 다른 사용자의 주문으로 일부 또는 전량 체결 | 대기 주문의 주인 | `TRADE_EXECUTED` | 체결된 대기 주문 1건당 1회. 다른 사용자의 주문 1건으로 내 대기 주문 3건이 체결되면 3회 |
| 거래일 갱신(09:00)으로 대기 중이던 내 주문이 만료 | 그 리그의 구독자 중 주문이 만료된 사람 | `ORDER_EXPIRED` | 거래일 갱신 1회당 1회 (여러 건이 만료되어도 1회) |
| 거래일 갱신(09:00), 만료된 내 주문 없음 | 그 리그의 나머지 구독자 | `TRADING_DAY_CHANGED` | 거래일 갱신 1회당 1회 |
| 내 공시·제보로 AI 뉴스가 게시됨 | 공시 작성자 또는 제보자 본인 | `NEWS_PUBLISHED` | 뉴스 생성 결과가 반영될 때마다 1회 |
| 내 공시·제보의 내용이 부족해 AI 뉴스가 생성되지 않음 | 공시 작성자 또는 제보자 본인 | `NEWS_SKIPPED` | 뉴스 생성 결과가 반영될 때마다 1회 |
| 내 공시·제보의 AI 뉴스 생성이 실패 | 공시 작성자 또는 제보자 본인 | `NEWS_FAILED` | 뉴스 생성 결과가 반영될 때마다 1회 |

- 주문을 넣은 사람에게는 체결 알림을 보내지 않습니다. 주문 API 응답으로 체결 결과를 받기 때문입니다.
- 공시 뉴스를 수동으로 다시 생성한 결과도 같은 알림으로 옵니다.
- 리그 종료, 회원 탈퇴로 주문이 취소될 때는 알림을 보내지 않습니다. 리그가 종료되는 시각에는 거래일 갱신 알림도 보내지 않습니다.

### 받는 메시지

모든 토픽 메시지는 같은 형식(`TopicEnvelope`)으로 옵니다. REST API의 `Result`(`code`, `message`, `data`)와 다른 형식이니 주의합니다.

| **필드** | **타입** | **설명** | **비고** |
| --- | --- | --- | --- |
| type | String | 메시지 종류 | 이 토픽은 항상 `ALERT` |
| payload | Object | 데이터 | 아래 표 참고 |
| publishedAt | String | 발행 시각 (KST) | REST 응답의 일시와 같은 형식(오프셋 없음, 밀리초까지), 예: `2026-11-09T14:32:05.123` |

#### payload

| **필드** | **타입** | **설명** | **비고** |
| --- | --- | --- | --- |
| alertType | String | 알림 종류 | 아래 표 참고 |
| message | String | 알림 내용 | 화면에 그대로 보여 줍니다 |
| url | String \| null | 알림을 눌렀을 때 이동할 프론트 경로 | `null`이면 이동할 곳이 없습니다 ('확인하기' 버튼 없음) |
| newsType | String \| null | 뉴스 알림의 원문 종류 | `DISCLOSURE`(공시 기반), `REPORT`(제보 기반). 뉴스 알림이 아니면 `null` |
| newsId | Long \| null | 뉴스 알림의 뉴스 ID | 뉴스 알림이 아니면 `null`. 미생성·실패 알림의 뉴스 ID도 채워집니다 |

| **alertType** | **설명** | **message** | **url** |
| --- | --- | --- | --- |
| TRADE_EXECUTED | 대기 중이던 내 주문이 체결됨 | `{종목 이름} {매수\|매도} 주문이 체결됐어요.` | `/leagues/{leagueId}/stocks/{stockId}` |
| ORDER_EXPIRED | 새 거래일이 시작되어 대기 중이던 내 주문이 만료됨 | `새 거래일이 시작돼 대기 중인 주문이 취소됐어요.` | `null` |
| TRADING_DAY_CHANGED | 새 거래일이 시작됨 (만료된 내 주문 없음) | `새 거래일이 시작됐어요.` | `null` |
| NEWS_PUBLISHED | 내 공시·제보로 AI 뉴스가 게시됨 | 제보: `제보를 바탕으로 뉴스가 게시되었어요.`<br>공시: `공시를 바탕으로 뉴스가 게시되었어요.` | `null` |
| NEWS_SKIPPED | 내 공시·제보의 내용이 부족해 뉴스가 생성되지 않음 | 제보: `제보 내용이 부족해 뉴스를 생성하지 못했어요.\n내용을 보완한 뒤 다시 시도해 주세요.` (두 줄, 줄바꿈 `\n`)<br>공시: `공시 내용이 부족해 뉴스를 생성하지 못했어요.` | `null` |
| NEWS_FAILED | 내 공시·제보의 뉴스 생성이 실패함 | `뉴스를 생성하지 못했어요. 다시 시도해 주세요.` (공시·제보 공통) | `null` |

> `DISCLOSURE`(공시 게시), `NEWS`(뉴스 게시), `LEAGUE`(리그 진행)는 예약된 값으로 지금은 보내지 않습니다. 뉴스 생성 결과는 `NEWS` 가 아니라 `NEWS_PUBLISHED`·`NEWS_SKIPPED`·`NEWS_FAILED`로 옵니다.

```
MESSAGE
destination:/topic/leagues/1/players/12/alerts
content-type:application/json
subscription:sub-0
message-id:a1b2c3-0

{
  "type": "ALERT",
  "payload": {
    "alertType": "TRADE_EXECUTED",
    "message": "진영전자 매도 주문이 체결됐어요.",
    "url": "/leagues/1/stocks/3",
    "newsType": null,
    "newsId": null
  },
  "publishedAt": "2026-11-09T14:32:05.123"
}^@
```

```
MESSAGE
destination:/topic/leagues/1/players/12/alerts
content-type:application/json
subscription:sub-0
message-id:a1b2c3-1

{
  "type": "ALERT",
  "payload": {
    "alertType": "ORDER_EXPIRED",
    "message": "새 거래일이 시작돼 대기 중인 주문이 취소됐어요.",
    "url": null,
    "newsType": null,
    "newsId": null
  },
  "publishedAt": "2026-11-10T09:00:00.412"
}^@
```

```
MESSAGE
destination:/topic/leagues/1/players/12/alerts
content-type:application/json
subscription:sub-0
message-id:a1b2c3-2

{
  "type": "ALERT",
  "payload": {
    "alertType": "NEWS_SKIPPED",
    "message": "제보 내용이 부족해 뉴스를 생성하지 못했어요.\n내용을 보완한 뒤 다시 시도해 주세요.",
    "url": null,
    "newsType": "REPORT",
    "newsId": 31
  },
  "publishedAt": "2026-11-10T10:12:41.208"
}^@
```

### 메시지 처리 방법

- 다른 토픽과 달리 메시지는 상태가 아니라 **사건 하나**입니다. 메시지 하나가 토스트 하나입니다.
- 순서가 뒤바뀌어 도착할 수 있지만, 다른 토픽처럼 `publishedAt`이 이른 메시지를 **버리지 않습니다**. 버리면 알림이 사라집니다.
- 연결이 끊긴 동안의 알림은 다시 받을 수 없고, 서버도 보관하지 않습니다. 필요한 화면 값(자산, 주문 내역)은 (재)연결 직후 REST API로 다시 받습니다.
- 알림을 받았을 때 화면의 자산·호가·주문 내역 값은 각 토픽이나 REST 응답으로 갱신합니다. 알림 메시지에는 수량·가격이 없습니다.
- 알 수 없는 `alertType`이 오면 기본 모양으로 보여 줍니다.

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
message:토픽 구독 권한이 없습니다\c /topic/leagues/1/players/12/alerts
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
3. **리그 종료 후:** 구독할 수 없고 알림도 오지 않습니다.
4. **연결이 끊긴 동안의 알림:** 다시 받을 수 없습니다. 알림 목록 조회 API는 없습니다.

---

## 6. FE 연동 예시

`@stomp/stompjs` 7.x를 사용합니다.

```bash
npm install @stomp/stompjs
```

### 6-1. 공통 연결 (`stompClient.ts`)

모든 토픽이 연결 하나를 같이 씁니다. 다른 토픽 문서에서 이미 만들었다면 그대로 재사용하되, 알림은 늦게 도착한 메시지도 버리면 안 되므로 `subscribeTopic`에 `dropStale` 옵션을 추가합니다.

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
 * dropStale: 상태 토픽(자산·호가 등)은 true(기본), 사건 토픽(알림)은 false.
 */
export function subscribeTopic<T>(
  destination: string,
  onMessage: (payload: T, envelope: TopicEnvelope<T>) => void,
  { dropStale = true }: { dropStale?: boolean } = {},
): () => void {
  let lastPublishedAt = 0;
  const handler = (frame: IMessage) => {
    const envelope: TopicEnvelope<T> = JSON.parse(frame.body);
    const publishedAt = Date.parse(envelope.publishedAt); // 오프셋이 없어 브라우저 시간대로 해석되지만 비교에는 문제없다
    if (dropStale && publishedAt < lastPublishedAt) return; // 늦게 도착한 이전 상태는 버린다
    lastPublishedAt = Math.max(lastPublishedAt, publishedAt);
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

### 6-2. 알림 구독

```tsx
import { useEffect, useState } from 'react';
import { subscribeTopic } from './stompClient';

export type AlertType =
  | 'TRADE_EXECUTED'
  | 'ORDER_EXPIRED'
  | 'TRADING_DAY_CHANGED'
  | 'NEWS_PUBLISHED'
  | 'NEWS_SKIPPED'
  | 'NEWS_FAILED';

export type Alert = {
  alertType: AlertType | string; // 알 수 없는 값은 기본 모양으로 보여 준다
  message: string;
  url: string | null; // null이면 '확인하기' 버튼 없음
  newsType: 'DISCLOSURE' | 'REPORT' | null; // 뉴스 알림만 값이 있다
  newsId: number | null;
};
```

```tsx
/** 리그에서 나에게 오는 알림을 받을 때마다 onAlert를 호출한다. playerId는 그 리그의 내 플레이어 ID다. */
export function useAlerts(leagueId: number, playerId: number, onAlert: (alert: Alert) => void) {
  useEffect(
    () =>
      subscribeTopic<Alert>(
        `/topic/leagues/${leagueId}/players/${playerId}/alerts`,
        (payload) => onAlert(payload),
        { dropStale: false }, // 알림은 늦게 도착해도 버리지 않는다
      ),
    [leagueId, playerId, onAlert],
  );
}
```

```tsx
import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
// toast는 프로젝트에서 쓰는 토스트 라이브러리의 함수로 바꾼다

function AlertToasts({ leagueId, playerId }: { leagueId: number; playerId: number }) {
  const navigate = useNavigate();
  const showAlert = useCallback(
    (alert: Alert) =>
      toast(alert.message, {
        // 체결 알림만 '확인하기'로 종목 화면에 이동한다
        action: alert.url ? { label: '확인하기', onClick: () => navigate(alert.url!) } : undefined,
      }),
    [navigate],
  );

  useAlerts(leagueId, playerId, showAlert);
  return null;
}
```

