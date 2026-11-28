package kr.ject.naspi.market.application;

/**
 * 리그 참여자 누구에게나 같은 종목 표시 정보. {@link MemberStock}에서 조회자별 값(본인 종목 여부)을 뺀 것이다.
 *
 * @param stockId 종목 ID
 * @param name 종목 이름
 * @param description 종목 소개
 * @param listerName 상장자 이름. 시작된 리그에서 탈퇴한 상장자면 '탈퇴한 사용자'
 * @param quote 시세. 리그 시작 전(대기실)에는 {@code null}
 */
public record LeagueStockView(long stockId, String name, String description, String listerName,
        StockQuote quote) {
}
