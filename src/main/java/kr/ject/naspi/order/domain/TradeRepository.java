package kr.ject.naspi.order.domain;

import java.util.List;

/**
 * 체결 기록 저장소. 구현은 infra의 {@code TradeJpaRepository}가 맡는다. 체결 기록은 추가만 하고 수정·삭제하지
 * 않으므로 저장과 조회만 선언한다.
 */
public interface TradeRepository {

    /**
     * 체결 기록을 저장한다.
     *
     * @param trade 체결 기록
     * @return 저장된 체결 기록
     */
    <S extends Trade> S save(S trade);

    /**
     * 종목의 거래일별 체결 수량과 체결 금액 합계를 구한다.
     *
     * @param stockId 종목 ID
     * @param tradingDay 거래일 번호
     * @return 합계. 체결이 없으면 0이다
     */
    TradeTotal sumByStockIdAndTradingDay(long stockId, int tradingDay);

    /**
     * 종목의 가장 최근 체결부터 최대 {@value Trade#VWAP_MAX_TRADES}건을 조회한다. 체결 수량이 1주 이상이므로 이
     * 건수면 {@value Trade#EVALUATION_MIN_QUANTITY}주를 채우기에 충분하다. 종료 평가(VWAP)가 종료 시각에 가까운
     * 체결부터 수량을 채울 때 쓴다 ({@link Trade#takeRecentQuantity}).
     *
     * @param stockId 종목 ID
     * @return 체결 일시·ID 역순 체결 기록
     */
    List<Trade> findTop500ByStockIdOrderByExecutedAtDescIdDesc(long stockId);

    /**
     * 매수 주문의 체결 기록을 체결 순서로 조회한다.
     *
     * @param buyOrderId 매수 주문 ID
     * @return 체결 일시·ID 오름차순 체결 기록
     */
    List<Trade> findAllByBuyOrderIdOrderByExecutedAtAscIdAsc(long buyOrderId);

    /**
     * 매도 주문의 체결 기록을 체결 순서로 조회한다.
     *
     * @param sellOrderId 매도 주문 ID
     * @return 체결 일시·ID 오름차순 체결 기록
     */
    List<Trade> findAllBySellOrderIdOrderByExecutedAtAscIdAsc(long sellOrderId);
}
