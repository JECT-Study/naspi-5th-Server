package kr.ject.naspi.market.application;

import java.util.Comparator;
import java.util.List;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockRepository;
import kr.ject.naspi.market.domain.StockSnapshot;
import kr.ject.naspi.market.domain.StockSnapshotCache;
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
    private final StockSnapshotCache cache;

    /**
     * 리그의 종목을 모두 조회한다. 한 리그의 종목은 최대 10개라 한 번에 조회한다.
     *
     * <p>화면 표시용이다. 진행 중인 리그는 현재가 캐시에서 읽으므로 DB보다 최대 캐시 갱신 주기만큼 늦을 수 있다.
     * 캐시에 없는 리그(대기실, 종료, 갱신 전)는 DB에서 읽는다. 거래 로직은 이 메서드를 쓰지 않는다
     * (docs/design/asset-realtime.md 3장).
     *
     * @param leagueId 리그 ID
     * @return 종목 ID 순으로 정렬된 리그 종목 목록
     */
    @Transactional(readOnly = true)
    public List<LeagueStock> findAllByLeagueId(long leagueId) {
        return cache.findByLeagueId(leagueId)
                .map(snapshots -> snapshots.stream()
                        .map(StockQueryService::toLeagueStock)
                        .toList())
                .orElseGet(() -> stockRepository.findAllByLeagueId(leagueId).stream()
                        .sorted(Comparator.comparing(Stock::getId))
                        .map(StockSnapshot::from)
                        .map(StockQueryService::toLeagueStock)
                        .toList());
    }

    /**
     * 플레이어가 리그 종목의 상장자인지 확인한다. 자사주 판단처럼 거래 로직에서 쓰므로 캐시가 아니라 DB에서 읽는다.
     *
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param playerId 플레이어 ID
     * @return 상장자 여부
     * @throws NaspiBizException 리그에 해당 종목이 없는 경우 ({@link ErrorCode#STOCK_NOT_FOUND})
     */
    @Transactional(readOnly = true)
    public boolean isListedBy(long leagueId, long stockId, long playerId) {
        return stockRepository.findById(stockId)
                .filter(stock -> stock.getLeagueId() == leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.STOCK_NOT_FOUND))
                .isListedBy(playerId);
    }

    private static LeagueStock toLeagueStock(StockSnapshot stock) {
        return new LeagueStock(stock.stockId(), stock.name(), stock.description(),
                stock.listerPlayerId(), stock.currentPrice());
    }
}
