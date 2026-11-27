package kr.ject.naspi.order.domain;

import java.util.Collection;
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
     * 종목의 가장 최근 체결부터 최대 {@code limit}건을 조회한다. 같은 체결 일시면 나중에 저장된 체결이 먼저다.
     *
     * @param stockId 종목 ID
     * @param limit 최대 건수 (1 이상)
     * @return 체결 일시·ID 역순 체결 기록
     */
    List<Trade> findRecentByStockId(long stockId, int limit);

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

    /**
     * 매수 주문별 체결 수량·금액 합계와 최저·최고 체결가를 한 번에 구한다. 목록 조회에서 주문마다 체결을
     * 조회하지 않도록 쓴다.
     *
     * @param buyOrderIds 매수 주문 ID 목록 (비어 있지 않아야 한다)
     * @return 체결이 있는 주문의 합계. 체결이 없는 주문은 포함되지 않는다
     */
    List<OrderExecutionTotal> sumByBuyOrderIds(Collection<Long> buyOrderIds);

    /**
     * 매도 주문별 체결 수량·금액 합계와 최저·최고 체결가를 한 번에 구한다.
     *
     * @param sellOrderIds 매도 주문 ID 목록 (비어 있지 않아야 한다)
     * @return 체결이 있는 주문의 합계. 체결이 없는 주문은 포함되지 않는다
     */
    List<OrderExecutionTotal> sumBySellOrderIds(Collection<Long> sellOrderIds);
}
