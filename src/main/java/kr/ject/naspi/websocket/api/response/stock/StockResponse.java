package kr.ject.naspi.websocket.api.response.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 종목 정보 메시지. 리그의 전체 종목 시세. 금액은 원 단위.
 *
 * <p>토픽: {@code /topic/leagues/{leagueId}/stocks} (리그에 입장한 회원만 구독 가능)
 *
 * <p>REST {@code GET /api/leagues/{leagueId}/stocks} 응답의 각 항목에서 {@code mine}만 빼고, 계산 시각
 * {@code asOf}를 더해 객체로 감싼 구조다.
 *
 * @param stocks 종목 목록. 종목 ID 순
 * @param asOf 계산 시각 (KST). 표시 중인 값보다 이른 메시지는 무시한다
 */
public record StockResponse(List<Stock> stocks, LocalDateTime asOf) {

    /** 메시지 종류 ({@code TopicEnvelope.type}). */
    public static final String TYPE = "STOCK";

    /**
     * 목록을 복사해 변경할 수 없게 만든다.
     */
    public StockResponse {
        stocks = List.copyOf(stocks);
    }

    /**
     * 종목 하나. 시세 필드는 리그 시작 전이면 {@code null}.
     *
     * @param stockId 종목 ID
     * @param name 종목 이름
     * @param description 종목 소개
     * @param listerName 상장자 이름. 시작된 리그에서 탈퇴한 상장자면 '탈퇴한 사용자'
     * @param currentPrice 현재가
     * @param basePrice 기준가. 직전 거래일 종가, 첫 거래일은 시초가
     * @param upperLimitPrice 상한가
     * @param lowerLimitPrice 하한가
     * @param changeAmount 기준가 대비 등락액
     * @param changeRate 기준가 대비 등락률(%). 소수점 첫째 자리
     * @param volume 현재 거래일 거래량 (주)
     */
    public record Stock(long stockId, String name, String description, String listerName,
            Long currentPrice, Long basePrice, Long upperLimitPrice, Long lowerLimitPrice,
            Long changeAmount, BigDecimal changeRate, Long volume) {
    }
}
