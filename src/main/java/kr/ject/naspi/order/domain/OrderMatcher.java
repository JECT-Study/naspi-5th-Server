package kr.ject.naspi.order.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 신규 주문을 호가창의 상대 주문과 매칭한다.
 *
 * <ul>
 *   <li>체결 조건: 매수 가격이 매도 가격 이상이면 체결한다.</li>
 *   <li>우선순위: 상대 호가창의 가격·시간 우선순위 순서대로 체결한다 ({@link OrderBook}).</li>
 *   <li>체결 가격: 먼저 접수되어 대기 중이던 주문의 가격이다.</li>
 *   <li>체결 수량: 전량 또는 일부이며, 한 건의 체결 수량은 두 주문의 잔여 수량 중 작은 쪽이다.</li>
 *   <li>자전거래 금지: 같은 플레이어의 주문과 체결될 차례가 되면 신규 주문의 잔여 수량을 취소하고
 *       멈춘다. 기존 대기 주문과 이미 체결된 부분은 유지한다. 사유는
 *       {@link CancelReason#SELF_TRADE_PREVENTED}이며 시장가보다 우선한다.</li>
 *   <li>시장가: 보호 범위 안에서 체결 가능한 수량을 체결하고 잔여 수량은 취소한다. 체결 가능
 *       수량이 0이면 전량 취소한다. 사유는 일부 체결이면 {@link CancelReason#MARKET_PARTIALLY_FILLED},
 *       체결이 없으면 {@link CancelReason#MARKET_NOT_FILLED}이다.</li>
 * </ul>
 *
 * <p>매칭은 주문 엔티티의 상태를 바꾼다. 정산(현금·주식 이동)과 저장은 호출하는 쪽이 맡는다.
 */
public final class OrderMatcher {

    private OrderMatcher() {
    }

    /**
     * 신규 주문을 매칭한다.
     *
     * @param book 신규 주문 접수 직전의 호가창
     * @param incoming 새로 접수된 대기 상태의 주문
     * @param boundPrice 체결 가능한 가격 한도. 지정가는 주문 가격, 시장가는 보호 가격이다.
     *     매수는 이 가격 이하의 매도 호가와, 매도는 이 가격 이상의 매수 호가와 체결한다
     * @return 체결 목록과 자전거래 취소 여부
     * @throws IllegalArgumentException 호가창과 다른 종목의 주문이거나 체결 대기 상태가 아닌 경우
     */
    public static MatchResult match(OrderBook book, Order incoming, long boundPrice) {
        if (incoming.getStockId() != book.stockId()) {
            throw new IllegalArgumentException("호가창과 다른 종목의 주문입니다: " + incoming.getStockId());
        }
        if (!incoming.getStatus().isOpen()) {
            throw new IllegalArgumentException("체결 대기 상태의 주문이 아닙니다: " + incoming.getStatus());
        }

        List<Execution> executions = new ArrayList<>();
        boolean selfTradePrevented = false;
        boolean buying = incoming.getSide() == OrderSide.BUY;

        for (Order resting : book.orders(incoming.getSide().opposite())) {
            if (incoming.getRemainingQuantity() == 0 || !resting.isResting()) {
                continue;
            }
            boolean crossed = buying
                    ? resting.getPrice() <= boundPrice
                    : resting.getPrice() >= boundPrice;
            if (!crossed) {
                break;
            }
            if (resting.getPlayerId().equals(incoming.getPlayerId())) {
                selfTradePrevented = true;
                break;
            }
            long quantity = Math.min(
                    incoming.getRemainingQuantity(), resting.getRemainingQuantity());
            resting.fill(quantity);
            incoming.fill(quantity);
            executions.add(new Execution(resting, incoming, resting.getPrice(), quantity));
        }

        if (incoming.getStatus().isOpen()
                && (selfTradePrevented || incoming.getType() == OrderType.MARKET)) {
            // 접수·체결·취소가 같은 순간으로 기록되도록 접수 시각을 취소 시각으로 쓴다
            incoming.cancel(cancelReasonOf(incoming, selfTradePrevented), incoming.getOrderedAt());
        }
        return new MatchResult(List.copyOf(executions), selfTradePrevented);
    }

    private static CancelReason cancelReasonOf(Order incoming, boolean selfTradePrevented) {
        if (selfTradePrevented) {
            return CancelReason.SELF_TRADE_PREVENTED;
        }
        return incoming.filledQuantity() > 0
                ? CancelReason.MARKET_PARTIALLY_FILLED : CancelReason.MARKET_NOT_FILLED;
    }
}
