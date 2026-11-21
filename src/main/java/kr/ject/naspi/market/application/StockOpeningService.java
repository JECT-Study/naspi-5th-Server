package kr.ject.naspi.market.application;

import java.util.List;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;
import kr.ject.naspi.market.domain.StockDailyPriceRepository;
import kr.ject.naspi.market.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 시작 시 종목 거래 개시.
 *
 * <p>정책 (docs/policies/v1/domain/market.md "시장"): 리그가 시작되면 모든 종목이 시초가 1주당 10,000원으로
 * 거래를 시작한다. 첫 체결 전 현재가와 최초 거래일의 기준가는 시초가다.
 *
 * <p>리그 시작 UseCase가 초기 자산 배분과 같은 트랜잭션에서 호출한다. 실패하면 리그 시작도 롤백되므로
 * 현재가가 없는 종목으로 거래가 열리지 않는다. 1일차 시세 행(기준가 = 시초가, 상·하한가 7,000~13,000원,
 * 거래량 0)도 함께 만든다.
 */
@Service
@RequiredArgsConstructor
public class StockOpeningService {

    private final StockRepository stockRepository;
    private final StockDailyPriceRepository dailyPriceRepository;

    /**
     * 리그의 모든 종목 현재가를 시초가로 정하고 1일차 시세 행을 만든다.
     *
     * @param leagueId 시작하는 리그 ID
     * @throws IllegalStateException 이미 거래가 열린 종목이 있는 경우
     */
    @Transactional
    public void open(long leagueId) {
        List<Stock> stocks = stockRepository.findAllByLeagueId(leagueId);
        stocks.forEach(Stock::open);
        dailyPriceRepository.saveAll(stocks.stream()
                .map(stock -> StockDailyPrice.open(leagueId, stock.getId(),
                        StockDailyPrice.FIRST_TRADING_DAY, Stock.OPENING_PRICE))
                .toList());
    }
}
