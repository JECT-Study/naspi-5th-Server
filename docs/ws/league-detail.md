# 리그 상세 토픽

대기실 화면에서 다른 사용자의 상장 완료(대기자 → 참여자 전환)와 리그 시작 가능 여부를 새로고침 없이 반영합니다.

| **항목** | **값** |
| --- | --- |
| 연결 엔드포인트 | `/ws` ([종목 정보 토픽 1. 연결 엔드포인트](stocks.md#1-연결-엔드포인트)와 같음) |
| 프로토콜 | STOMP 1.2 over WebSocket (SockJS 미사용) |
| 인증 | 로그인 세션 쿠키 `SESSION` ([종목 정보 토픽 2. 연결 인증](stocks.md#2-연결-인증)과 같음) |
| 구독 | `/topic/leagues/{leagueId}/detail` |
| 구독 권한 | 그 리그에 입장한 회원 (상장 전 대기자 포함). 종료 시각이 지난 리그는 불가 |
| 메시지 종류 (`type`) | `LEAGUE_DETAIL` |
| 전달 시점 | 그 리그에서 누군가 상장을 마쳤을 때 ([전달 시점](#전달-시점)). 구독자가 없으면 보내지 않습니다 |
| 대응 REST | `GET /api/leagues/{leagueId}/detail` |
| 발행 (클라이언트 → 서버) | 없음 |

---

## 1. 구독 (서버 → 클라이언트)

`SUBSCRIBE /topic/leagues/{leagueId}/detail`

### 경로 변수

| **변수** | **타입** | **설명** |
| --- | --- | --- |
| leagueId | Long | 리그 ID |

### 구독 권한

그 리그에 입장한 회원만 구독할 수 있습니다. 상장 전인 대기자도 입장한 상태이므로 구독할 수 있습니다. 리그가 없거나(만료·삭제된 대기실 포함) 종료 시각이 지난 리그는 구독할 수 없습니다. REST `GET /api/leagues/{leagueId}/detail`의 조회 조건과 같습니다.

권한이 없으면 `ERROR` 프레임을 받고 **연결 전체가 끊깁니다**([종목 정보 토픽 5. 에러](stocks.md#5-에러)와 같음).

```
SUBSCRIBE
id:sub-0
destination:/topic/leagues/1/detail

^@
```

### 전달 시점

| **원인** | **전달 시점** | **받는 사람** |
| --- | --- | --- |
| 대기자의 상장 완료 | 상장 저장(커밋) 직후 | 그 리그의 구독자 전원 (상장한 본인 포함) |

다음 변화로는 메시지가 오지 않습니다. 화면을 다시 불러올 때 REST로 반영합니다.

- 입장, 퇴장, 추방, 리그 이름 수정, 종목 수정
- 리그 시작, 대기실 삭제·만료

### 받는 메시지

모든 토픽 메시지는 같은 형식(`TopicEnvelope`: `type`, `payload`, `publishedAt`)으로 옵니다([종목 정보 토픽 받는 메시지](stocks.md#받는-메시지)와 같음). 이 토픽의 `type`은 항상 `LEAGUE_DETAIL`입니다.

#### payload

REST `GET /api/leagues/{leagueId}/detail`의 `data`에서 `players[].me`만 빼고 `asOf`를 더해 보냅니다. `me`는 구독자마다 다르므로 보내지 않습니다. REST로 받은 본인 `playerId`와 비교해 본인을 찾습니다.

| **필드** | **타입** | **설명** | **비고** |
| --- | --- | --- | --- |
| leagueId | Long | 리그 ID | |
| name | String | 리그 이름 | |
| inviteCode | String | 대기실 초대 코드 | 리그가 시작되었거나 대기실이 만료되었으면 `null` |
| waitingRoomExpiresAt | String | 대기실 만료 일시 (KST) | 대기실이 아니면 `null` |
| startable | Boolean | 리그 시작 가능 여부 | 참여자 3명 이상, 대기자 0명 |
| tradingDay | Object | 현재 거래일과 리그 운영 기간 | 대기실이면 `null`. 필드는 REST와 같음 |
| players | Array | 플레이어 목록 | 참여일시 순 |
| players[].playerId | Long | 플레이어 ID | |
| players[].name | String | 회원 이름 | 시작된 리그에서 탈퇴한 참여자면 '탈퇴한 사용자' |
| players[].host | Boolean | 방장 여부 | |
| players[].listed | Boolean | 상장 완료 여부 | `true` 참여자, `false` 대기자 |
| players[].joinedAt | String | 참여일시 (KST) | |
| players[].listedAt | String | 상장일시 (KST) | 대기자는 `null` |
| players[].stock | Object | 상장한 종목 (`stockId`, `name`, `description`) | 대기자는 `null` |
| asOf | String | 값을 계산한 시각 (KST) | REST와 같은 일시 형식 |

```
MESSAGE
destination:/topic/leagues/1/detail
content-type:application/json
subscription:sub-0
message-id:a1b2c3-0

{
  "type": "LEAGUE_DETAIL",
  "payload": {
    "leagueId": 1, "name": "나스피 리그", "inviteCode": "ABC1234",
    "waitingRoomExpiresAt": "2026-12-04T20:00:00", "startable": true, "tradingDay": null,
    "players": [
      {
        "playerId": 30, "name": "수빈", "host": true, "listed": true,
        "joinedAt": "2026-11-27T20:00:00", "listedAt": "2026-11-27T20:00:00",
        "stock": { "stockId": 99, "name": "수빈전자", "description": "성실함이 주력 사업입니다." }
      },
      {
        "playerId": 31, "name": "민준", "host": false, "listed": true,
        "joinedAt": "2026-11-27T20:01:00", "listedAt": "2026-11-27T21:30:00",
        "stock": { "stockId": 100, "name": "민준건설", "description": "튼튼하게 짓습니다." }
      }
    ],
    "asOf": "2026-11-27T21:30:01.123456"
  },
  "publishedAt": "2026-11-27T21:30:01.125"
}^@
```

### 메시지 처리 방법

- 메시지는 바뀐 부분이 아니라 **그 시점의 리그 상세 전체**입니다. 받은 값으로 화면 상태를 통째로 덮어씁니다.
- **`asOf`가 마지막으로 반영한 메시지보다 이른 메시지는 버립니다.** REST 응답에는 `asOf`가 없으므로 REST 값 다음에 오는 첫 메시지는 그대로 반영합니다.
- 본인 표시(`me`)는 메시지에 없으므로 REST로 받은 본인 `playerId`를 유지하고 `players[].playerId`와 비교합니다.
- 연결이 끊긴 동안의 메시지는 다시 받을 수 없습니다. (재)연결 직후에는 REST로 현재 상태를 받고, 이후 메시지를 반영합니다.
- 메시지가 오지 않는 변화(입장·퇴장 등)가 있으므로, 대기실 화면에 들어올 때와 다시 보일 때는 REST로 조회합니다.

---

## 2. 발행 (클라이언트 → 서버)

**이 토픽은 발행이 없습니다.** [종목 정보 토픽 4. 발행](stocks.md#4-발행-클라이언트--서버)과 같습니다.

---

## 3. 예외

1. **없는 리그 ID, 입장하지 않은 리그, 종료 시각이 지난 리그로 구독한 경우:** `ERROR` 프레임(`토픽 구독 권한이 없습니다: ...`)을 받고 연결 전체가 끊깁니다.
2. **추방·퇴장·대기실 삭제:** 메시지가 오지 않습니다. 다음 REST 조회에서 실패 응답을 받으면 구독을 해지하고 화면을 나갑니다.
3. **리그 시작:** 메시지가 오지 않습니다. 시작 후 화면 전환은 REST 응답을 따릅니다.
4. **연결이 끊긴 동안의 상장:** 다시 받을 수 없습니다. 다시 연결한 직후 `GET /api/leagues/{leagueId}/detail`로 현재 상태를 받습니다.

---

## 4. FE 연동 예시

공통 연결(`stompClient.ts`, `subscribeTopic`)은 [종목 정보 토픽 6-1](stocks.md#6-1-공통-연결-stompclientts)을 그대로 씁니다.

```tsx
import { useEffect, useState } from 'react';
import { subscribeTopic } from './stompClient';

type PlayerStock = { stockId: number; name: string; description: string };

/** 토픽 메시지의 플레이어. REST 플레이어에서 me를 뺀 것. */
export type Player = {
  playerId: number;
  name: string;
  host: boolean;
  listed: boolean;
  joinedAt: string;
  listedAt: string | null;
  stock: PlayerStock | null;
};

type TradingDay = {
  dayNumber: number;
  totalDays: number;
  dayEndsAt: string;
  startedAt: string;
  endsAt: string;
};

/** REST GET /api/leagues/{leagueId}/detail 응답(data). */
export type LeagueDetail = {
  leagueId: number;
  name: string;
  inviteCode: string | null;
  waitingRoomExpiresAt: string | null;
  startable: boolean;
  tradingDay: TradingDay | null;
  players: (Player & { me: boolean })[];
};

type LeagueDetailPayload = Omit<LeagueDetail, 'players'> & { players: Player[]; asOf: string };

/**
 * 리그 상세를 실시간으로 받는다. initial은 처음 접속할 때 REST로 받은 값이다.
 * asOf가 마지막으로 반영한 메시지보다 이른 메시지는 버리고, me는 REST로 받은 본인 playerId로 다시 붙인다.
 */
export function useLeagueDetail(leagueId: number, initial: LeagueDetail) {
  const [state, setState] = useState<{ detail: LeagueDetail; asOf?: string }>({
    detail: initial,
  });

  useEffect(() => {
    setState({ detail: initial });
    const myPlayerId = initial.players.find((p) => p.me)?.playerId;
    return subscribeTopic<LeagueDetailPayload>(`/topic/leagues/${leagueId}/detail`, (payload) =>
      setState((prev) => {
        if (prev.asOf && Date.parse(payload.asOf) < Date.parse(prev.asOf)) return prev;
        const { asOf, players, ...rest } = payload;
        return {
          detail: { ...rest, players: players.map((p) => ({ ...p, me: p.playerId === myPlayerId })) },
          asOf,
        };
      }),
    );
  }, [leagueId, initial]);

  return state.detail;
}
```

> `initial`은 렌더링마다 새 객체를 만들지 않도록 `useMemo`나 상태로 넘깁니다. 매번 새 객체면 구독을 계속 다시 합니다.
