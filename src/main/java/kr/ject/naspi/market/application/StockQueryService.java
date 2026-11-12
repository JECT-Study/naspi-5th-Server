package kr.ject.naspi.market.application;

import java.util.Comparator;
import java.util.List;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종목 조회. 다른 모듈은 이 서비스로 리그 종목의 이름·상장자·현재가를 조회한다.
 */
@Service
@RequiredArgsConstructor
public class StockQueryService {

    private final StockRepository stockRepository;

    /**
     * 리그의 종목을 모두 조회한다. 한 리그의 종목은 최대 10개라 한 번에 조회한다.
     *
     * @param leagueId 리그 ID
     * @return 종목 ID 순으로 정렬된 리그 종목 목록
     */
    @Transactional(readOnly = true)
    public List<LeagueStock> findAllByLeagueId(long leagueId) {
        return stockRepository.findAllByLeagueId(leagueId).stream()
                .sorted(Comparator.comparing(Stock::getId))
                .map(StockQueryService::toLeagueStock)
                .toList();
    }

    private static LeagueStock toLeagueStock(Stock stock) {
        return new LeagueStock(stock.getId(), stock.getName().getValue(),
                stock.getDescription().getValue(),
                stock.getListerPlayerId(), stock.getCurrentPrice());
    }
}
