---
name: sync-topic-spec
description: 웹소켓 토픽 메시지 DTO의 필드 이름·타입을 같은 데이터를 내려주는 REST 응답 DTO에 맞춘다. 기준은 REST다. push 훅이 "웹소켓 토픽 메시지와 REST 응답 스펙이 달라" 차단했을 때, 새 토픽을 추가했을 때, "토픽 스펙 맞춰줘", "웹소켓 응답 REST랑 맞춰줘" 요청 시 사용.
allowed-tools: Read, Grep, Glob, Edit, Bash(./gradlew test:*), Bash(./gradlew checkstyleMain:*), Bash(./gradlew checkstyleTest:*), Bash(git status:*), Bash(git diff:*)
---

# 웹소켓 토픽 스펙을 REST 응답에 맞추기

프론트는 (재)연결 직후 REST로 현재 상태를 받고, 이후 웹소켓 메시지로 덮어쓴다. 그래서 같은 데이터는 두 경로에서 필드 이름과 타입이 같아야 한다. **기준은 REST DTO다. REST DTO는 고치지 않는다.**

검사는 `src/test/java/kr/ject/naspi/websocket/api/response/TopicResponseSpecTest.java`가 한다.

- `PAIRS`: 토픽 메시지와 대응하는 REST 응답 쌍. `topicOnly`는 토픽에만 있는 필드(예: `asOf`), `restOnly`는 REST에만 있는 필드(예: 조회한 회원 기준 값 `mine`)의 경로다. 목록 원소 안의 필드는 `players[].me`처럼 쓴다.
- `WITHOUT_REST`: REST 대응이 없는 토픽 (캔들 차트, 알림).
- 비교 대상은 record 필드 이름과 타입이다. 중첩 record와 `List` 원소까지 비교하고, `long`과 `Long`(nullable 여부)도 다르게 본다.

이 스킬은 수정과 검증만 한다. **커밋·push는 하지 않는다.**

## 1. 차이 확인

```
./gradlew test --tests "kr.ject.naspi.websocket.api.response.TopicResponseSpecTest"
```

- 통과하면 "일치함"을 보고하고 끝낸다.
- 실패 메시지는 `build/test-results/test/TEST-kr.ject.naspi.websocket.api.response.TopicResponseSpecTest.xml`의 `<failure message=...>`에 있다. 항목 형식은 다음과 같다.
  - `{토픽} ↔ {REST}.{경로}: 토픽에 없다` → REST에 있는 필드가 토픽에 없음
  - `... : REST에 없다` → 토픽에만 있는 필드
  - `... : 타입이 다르다 (토픽 X, REST Y)`
- 이름만 바뀐 것으로 보이면(같은 위치에서 하나는 "토픽에 없다", 하나는 "REST에 없다") 이름 변경으로 묶어서 다룬다.

## 2. 차이 분류

각 차이를 다음 중 하나로 분류한다. **판단이 애매하면 고치지 말고 질문한다.**

| 경우 | 처리 |
|---|---|
| REST 필드 이름·타입이 바뀌었는데 토픽이 따라가지 않음 | 3단계에서 토픽을 REST에 맞춘다 |
| REST에 필드가 추가됨 | 토픽에도 추가한다. 토픽 쪽에서 채울 데이터가 없으면 질문한다 |
| 토픽에만 있어야 하는 필드 (계산 시각 등) | 질문해서 확인받은 뒤 `PAIRS`의 `topicOnly`에 등록한다 |
| REST에만 있어야 하는 필드 (조회한 회원 기준 값 등. 토픽은 여러 구독자에게 같은 메시지를 보낸다) | 질문해서 확인받은 뒤 `restOnly`에 등록한다 |
| 새 토픽 메시지가 등록되지 않음 (`PAIRS 또는 WITHOUT_REST 에 등록하지 않은 토픽 메시지`) | 같은 데이터를 내려주는 REST GET API를 찾아 `PAIRS`에 등록한다. 없으면 `WITHOUT_REST`에 등록한다. 어느 쪽인지 불분명하면 질문한다 |

## 3. 토픽 DTO 수정

토픽 DTO는 `src/main/java/kr/ject/naspi/websocket/api/response/{도메인}/`에 있다. REST DTO를 읽고 다음을 맞춘다.

1. **record 필드**: REST와 같은 이름, 같은 타입(`long`/`Long`, `BigDecimal`, 중첩 record, `List<...>`)으로 바꾼다. 필드 순서도 REST와 같게 한다. 허용된 추가 필드(`asOf` 등)는 맨 뒤에 둔다.
2. **javadoc `@param`**: REST DTO의 설명을 그대로 옮긴다. 클래스 javadoc의 `REST {@code GET ...} 응답과 같은 필드에 ...` 문구가 실제 차이와 맞는지 확인한다.
3. **메시지를 만드는 코드**: `src/main/java/kr/ject/naspi/websocket/application/topic/{도메인}/`(`XxxTopic`, `XxxMessageQuery`)에서 바뀐 생성자와 접근자 호출을 고친다. 데이터는 각 모듈 `application`의 조회 결과에서 가져온다 (다른 모듈 `domain`/`infra` 직접 참조 금지).
4. **테스트**: `src/test/java/kr/ject/naspi/websocket/` 아래에서 바뀐 DTO를 쓰는 테스트를 고친다 (`grep -rl {DTO 이름} src/test`).

## 4. 검증

```
./gradlew checkstyleMain checkstyleTest
./gradlew test --tests "kr.ject.naspi.websocket.*"
```

둘 다 통과할 때까지 고친다. `TopicResponseSpecTest`는 `kr.ject.naspi.websocket.*`에 포함된다.

## 5. 보고

- 토픽별로 바뀐 필드를 `이전 → 이후` 형식으로 정리한다.
- 웹소켓 메시지 스펙이 바뀐 것이므로, 프론트에 공유해야 할 변경이라고 적는다.
- `topicOnly`·`restOnly`·`WITHOUT_REST`에 등록한 항목이 있으면 이유와 함께 적는다.
- 커밋은 사용자가 요청할 때 한다.
