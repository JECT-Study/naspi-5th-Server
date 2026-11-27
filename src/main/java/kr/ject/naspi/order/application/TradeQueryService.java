package kr.ject.naspi.order.application;

import java.util.List;
import kr.ject.naspi.order.domain.Trade;
import kr.ject.naspi.order.domain.TradeRepository;
import kr.ject.naspi.order.domain.TradeTotal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체결 기록 조회. 다른 모듈(예: market의 리그 종료 평가 가격)이 체결 기록을 원본으로 쓸 때 사용한다. 평가 규칙은
 * 두지 않고 조회만 제공한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeQueryService {

    private final TradeRepository tradeRepository;

    /**
     * 종목의 거래일 체결 합계를 구한다.
     *
     * @param stockId 종목 ID
     * @param tradingDay 거래일 번호
     * @return 체결 수량·금액 합계. 체결이 없으면 수량·금액 0
     */
    public TradeDailyTotal getDailyTotal(long stockId, int tradingDay) {
        TradeTotal total = tradeRepository.sumByStockIdAndTradingDay(stockId, tradingDay);
        return new TradeDailyTotal(total.quantity(), total.amount());
    }

    /**
     * 종목의 최근 체결부터 최대 {@code limit}건을 조회한다. 체결 일시 역순이며 같은 체결 일시면 나중에 저장된
     * 체결이 먼저다.
     *
     * @param stockId 종목 ID
     * @param limit 최대 건수 (1 이상)
     * @return 최근 체결부터의 체결가·수량
     */
    public List<TradeRecord> findRecent(long stockId, int limit) {
        return tradeRepository.findRecentByStockId(stockId, limit).stream()
                .map(TradeQueryService::toRecord)
                .toList();
    }

    private static TradeRecord toRecord(Trade trade) {
        return new TradeRecord(trade.getPrice(), trade.getQuantity());
    }
}
