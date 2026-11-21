package kr.ject.naspi.market.application;

import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPriceRepository;
import kr.ject.naspi.market.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체결 반영. 체결이 발생하면 종목의 현재가와 그 거래일 거래량을 갱신한다.
 *
 * <p>정책 (docs/policies/v1/domain/market.md "시장"): 현재가는 최신 체결가이고, 거래량은 실제 체결 수량의
 * 합계다.
 *
 * <p>order 체결 흐름이 정산·체결 기록과 같은 트랜잭션에서 체결 1건마다 호출한다
 * (docs/policies/README.md: 체결 시 market 현재가·거래량 갱신은 order가 market을 직접 호출). 현재가 캐시는
 * 건드리지 않으며, {@link CurrentPriceRefreshService}가 다음 주기에 DB 값을 읽어 반영한다.
 */
@Service
@RequiredArgsConstructor
public class StockExecutionService {

    private final StockRepository stockRepository;
    private final StockDailyPriceRepository dailyPriceRepository;

    /**
     * 체결 1건을 반영한다. 현재가를 체결가로 바꾸고 거래일 거래량에 체결 수량을 더한다. 시세 행은 리그 시작·거래일
     * 전환이 만들며, 없으면 불변식 위반이다.
     *
     * <p>리그 락을 잡은 체결 트랜잭션 안에서만 호출할 수 있다.
     *
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param tradingDay 체결 기록과 같은 거래일 번호
     * @param price 체결가 (원)
     * @param quantity 체결 수량 (주)
     * @throws IllegalStateException 종목이 없거나 다른 리그의 종목이거나 거래가 열리지 않았거나 그 거래일의 시세 행이
     *     없는 경우 (접수 단계에서 검증된 불변식 위반이므로 체결 트랜잭션 전체가 롤백된다)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyExecution(long leagueId, long stockId, int tradingDay, long price,
            long quantity) {
        Stock stock = stockRepository.findById(stockId)
                .filter(found -> found.getLeagueId() == leagueId)
                .orElseThrow(() -> new IllegalStateException(
                        "체결할 종목을 찾을 수 없습니다: leagueId=" + leagueId + ", stockId=" + stockId));
        stock.applyTrade(price);
        dailyPriceRepository.findByStockIdAndTradingDay(stockId, tradingDay)
                .orElseThrow(() -> new IllegalStateException(
                        "거래일 시세가 없습니다: stockId=" + stockId + ", tradingDay=" + tradingDay))
                .addVolume(quantity);
    }
}
