# 이슈 초안

Jira 등록 전 epic·story 초안. 등록 후에는 각 스토리의 `Jira:` 칸에 이슈 키(예: `ND-12`)를 기입하고, 이후 상태·담당자 관리는 Jira를 기준으로 한다.

## 규칙

- epic = 도메인 모듈, 버전(v1, v2)은 Jira **Fix Version**으로 구분한다.
- story 하나 = 유스케이스(API 또는 이벤트 처리) 하나. 설명에 정책서 링크를 넣는다.
- 여러 도메인에 걸친 기능은 시작 도메인에 두고, 후속 처리는 해당 도메인 스토리로 분리해 선행 관계로 연결한다.
- `결정 필요` 항목은 구현 전에 정책서에 반영한다.

## v1 Epic

| Epic | 파일 | 스토리 수 |
|---|---|---|
| [기반] 공통·인프라 | [v1/global.md](v1/global.md) | 8 |
| [회원] | [v1/member.md](v1/member.md) | 6 |
| [리그] | [v1/league.md](v1/league.md) | 16 |
| [시장] | [v1/market.md](v1/market.md) | 9 |
| [자산] | [v1/asset.md](v1/asset.md) | 7 |
| [주문] | [v1/order.md](v1/order.md) | 12 |
| [콘텐츠] | [v1/contents.md](v1/contents.md) | 10 |

## 권장 구현 순서

```
1. 기반      GLB-01 ~ GLB-04
2. 회원      MBR-01 ~ MBR-05
3. 대기실    LEA-01 ~ LEA-05, MKT-01, MKT-02, LEA-06 ~ LEA-08, LEA-16
4. 시작      LEA-09, LEA-10, MKT-03, MKT-04, AST-01
5. 거래      AST-03 ~ AST-06, ORD-01 ~ ORD-07, MKT-05, MKT-08, AST-02, ORD-08, ORD-12
6. 거래일    LEA-11, MKT-06, ORD-09
7. 종료      LEA-12, ORD-10, MKT-07, LEA-13, LEA-14
8. 탈퇴      MBR-06, ORD-11, AST-07, MKT-09, LEA-15
9. 병렬 가능 GLB-05 ~ GLB-08, CNT-01 ~ CNT-10 (콘텐츠는 3단계 이후 착수 가능)
```
