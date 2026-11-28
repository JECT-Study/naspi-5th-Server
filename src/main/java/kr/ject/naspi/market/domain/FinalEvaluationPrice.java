package kr.ject.naspi.market.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 리그 종료 평가 가격 (league.md "리그 결과"). 종목의 체결 기록으로 1주당 평가 가격을 구한다.
 *
 * <ul>
 *   <li>마지막 거래일 누적 체결량이 {@value #MIN_QUANTITY}주 이상이면 마지막 거래일 전체 체결의 VWAP.</li>
 *   <li>미만이면 종료에 가까운 과거 체결부터 {@value #MIN_QUANTITY}주까지 확대한다. 마지막으로 추가하는 체결 건은
 *       {@value #MIN_QUANTITY}주를 채우는 데 필요한 수량만 반영한다.</li>
 *   <li>리그 전체 체결량이 {@value #MIN_QUANTITY}주 미만이면 전체 체결, 체결이 없으면 시초가.</li>
 * </ul>
 *
 * <p>VWAP은 원 미만 절사이며 100원 호가 단위는 적용하지 않는다 ({@link TradeVolume}).
 */
public final class FinalEvaluationPrice {

    /** 최소 평가 거래량 (주). 발행량 5,000주의 10%. */
    public static final long MIN_QUANTITY = 500;

    /** {@link #MIN_QUANTITY}를 채우는 데 필요한 최대 체결 건수. 체결 1건은 1주 이상이다. */
    public static final int MAX_RECENT_TRADES = (int) MIN_QUANTITY;

    private FinalEvaluationPrice() {
    }

    /**
     * 종목의 종료 평가 가격을 구한다.
     *
     * @param lastDayVolume 마지막 거래일 체결 합계
     * @param recentFirst 종료 시각에 가까운 체결부터 정렬된 최근 체결 (최대 {@value #MAX_RECENT_TRADES}건).
     *     마지막 거래일 체결량이 {@value #MIN_QUANTITY}주 미만일 때만 조회한다
     * @return 1주당 원
     */
    public static long evaluate(TradeVolume lastDayVolume,
            Supplier<List<EvaluatedTrade>> recentFirst) {
        if (lastDayVolume.quantity() >= MIN_QUANTITY) {
            return lastDayVolume.volumeWeightedAveragePrice();
        }
        List<EvaluatedTrade> taken = takeRecentQuantity(recentFirst.get(), MIN_QUANTITY);
        if (taken.isEmpty()) {
            return Stock.OPENING_PRICE;
        }
        return TradeVolume.of(taken).volumeWeightedAveragePrice();
    }

    /**
     * 종료 시각에 가까운 체결부터 수량이 {@code limit}주가 될 때까지만 모은다. 마지막 체결 건은 한도를 채우는 데
     * 필요한 수량만 반영한다. 전체 수량이 한도에 못 미치면 전체를 쓴다.
     *
     * @param recentFirst 최근 체결부터 정렬된 체결
     * @param limit 모을 수량 한도 (주)
     * @return 체결가와 반영 수량. 최근 체결부터의 순서이며 반영 수량 합계는 최대 {@code limit}이다
     */
    static List<EvaluatedTrade> takeRecentQuantity(List<EvaluatedTrade> recentFirst,
            long limit) {
        List<EvaluatedTrade> taken = new ArrayList<>();
        long remaining = limit;
        for (EvaluatedTrade trade : recentFirst) {
            if (remaining <= 0) {
                break;
            }
            long quantity = Math.min(trade.quantity(), remaining);
            taken.add(new EvaluatedTrade(trade.price(), quantity));
            remaining -= quantity;
        }
        return taken;
    }
}
