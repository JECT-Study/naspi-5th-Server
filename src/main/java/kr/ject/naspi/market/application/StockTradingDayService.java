package kr.ject.naspi.market.application;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;
import kr.ject.naspi.market.domain.StockDailyPriceRepository;
import kr.ject.naspi.market.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래일 전환 시 종가 확정과 새 거래일 시세 적용 (MKT-06).
 *
 * <p>정책 (docs/policies/v1/domain/market.md "시장", "가격제한폭"): 오전 9시에 직전 거래일의 종가가 확정되고
 * 새 거래일의 기준가와 가격 범위가 적용된다. 종가는 그 거래일의 마지막 체결가이며 거래량이 0이면 기준가다.
 * 새 거래일의 기준가는 직전 거래일 종가다.
 *
 * <p>종가는 전환 시점의 종목 현재가(마지막 체결가)다. 전환은 리그 락을 잡은 트랜잭션에서 일어나고, 전환 처리
 * 전(09:00 이후)에는 주문 접수를 거절하므로 전환 시점의 현재가는 그 거래일의 마지막 체결가와 같다.
 * 거래량은 새 거래일 행이 0부터 시작하므로 초기화된다. 현재가는 바꾸지 않는다.
 *
 * <p>league 거래일 전환 UseCase가 호출한다. 거래일 번호는 league가 넘긴다.
 */
@Service
@RequiredArgsConstructor
public class StockTradingDayService {

    private final StockRepository stockRepository;
    private final StockDailyPriceRepository dailyPriceRepository;

    /**
     * 마감하는 거래일의 종가를 확정하고 다음 거래일 시세를 만든다. 리그 락을 잡은 전환 트랜잭션 안에서만 호출할 수
     * 있다.
     *
     * @param leagueId 리그 ID
     * @param closingDay 마감하는 거래일 번호 (1~6)
     * @throws IllegalStateException 종목의 마감 거래일 시세 행이 없거나 현재가가 없는 경우
     * @throws IllegalArgumentException 다음 거래일 번호가 7을 넘는 경우
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void changeTradingDay(long leagueId, int closingDay) {
        List<Stock> stocks = stockRepository.findAllByLeagueId(leagueId);
        Map<Long, StockDailyPrice> closingPrices = dailyPriceRepository
                .findAllByLeagueIdAndTradingDay(leagueId, closingDay).stream()
                .collect(Collectors.toMap(StockDailyPrice::getStockId, Function.identity()));
        List<StockDailyPrice> nextPrices = stocks.stream().map(stock -> {
            StockDailyPrice closing = closingPrices.get(stock.getId());
            if (closing == null || stock.getCurrentPrice() == null) {
                throw new IllegalStateException("거래일 전환에 필요한 시세가 없습니다: leagueId="
                        + leagueId + ", stockId=" + stock.getId() + ", closingDay=" + closingDay);
            }
            long close = closing.close(stock.getCurrentPrice());
            return StockDailyPrice.open(leagueId, stock.getId(), closingDay + 1, close);
        }).toList();
        dailyPriceRepository.saveAll(nextPrices);
    }
}
