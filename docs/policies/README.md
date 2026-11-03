# 정책서

**현재 구현 대상: v1**

기능을 구현하기 전에 해당 모듈의 v1 정책 파일을 확인한다.
[roadmap.md](roadmap.md)는 v2 이후(매매봇, 추가 뉴스 유형 등)를 포함한 전체 기획 참고용이며, v1 구현 대상이 아니다.
v1과 roadmap의 내용이 다르면 v1을 따른다.

## v1 모듈별 정책

| 모듈 | 파일 | 범위 |
|---|---|---|
| `member` | [v1/member.md](v1/member.md) | 카카오 로그인/회원가입, 이름·이메일, 회원 탈퇴 |
| `league` | [v1/league.md](v1/league.md) | 리그 생성·초대, 대기실, 시작·운영 기간·종료, 리그 결과 |
| `market` | [v1/market.md](v1/market.md) | 종목 상장, 거래일, 시세(현재가·기준가·종가·거래량), 가격제한폭 |
| `asset` | [v1/asset.md](v1/asset.md) | 초기 자산 배분, 주문 가능 자산·예약·정산, 자사주 최소 유지분 |
| `order` | [v1/order.md](v1/order.md) | 지정가·시장가 주문, 체결 원칙, 자전거래 금지, 미체결 만료·취소 |
| `contents` | [v1/contents.md](v1/contents.md) | 공시, 공시 기반 AI 뉴스, 댓글 |

## 모듈 간 의존 방향

```
member ← league ← market ← asset ← order
                     ↖ contents
```

역방향 흐름은 이벤트로 처리한다.

| 이벤트 | 발행 | 구독 |
|---|---|---|
| 체결 (`TradeExecuted`) | order | market: 현재가·거래량 갱신 |
| 거래일 전환 (`TradingDayChanged`, 매일 09:00 KST) | league | market: 종가 확정·기준가 적용 / order: 미체결 만료 |
| 공시 게시 (`DisclosurePublished`) | contents | contents: AI 뉴스 비동기 생성 |
| 회원 탈퇴 (`MemberWithdrawn`) | member | order: 주문 취소 / asset: 현금 폐기 / market: 종목 연결 해제 / league: 결과 처리 |
| 리그 종료 | league | order: 미체결 전체 취소 / league: 결과 산정 |

## 작성 규칙

- 정책이 비어 있거나 모호하면 구현자가 임의로 정하지 않고 질문한다. 결정되지 않은 사항은 해당 파일의 `미정 사항` 섹션에 기록한다.
- 다른 모듈의 정책은 내용을 복사하지 말고 링크로 참조한다.
- v2 정책은 `v2/` 디렉터리에 같은 모듈 단위로 작성한다.
