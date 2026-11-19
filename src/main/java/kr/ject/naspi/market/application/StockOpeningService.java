package kr.ject.naspi.market.application;

import kr.ject.naspi.market.domain.Stock;
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
 * 현재가가 없는 종목으로 거래가 열리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class StockOpeningService {

    private final StockRepository stockRepository;

    /**
     * 리그의 모든 종목 현재가를 시초가로 정한다.
     *
     * @param leagueId 시작하는 리그 ID
     * @throws IllegalStateException 이미 거래가 열린 종목이 있는 경우
     */
    @Transactional
    public void open(long leagueId) {
        stockRepository.findAllByLeagueId(leagueId).forEach(Stock::open);
    }
}
