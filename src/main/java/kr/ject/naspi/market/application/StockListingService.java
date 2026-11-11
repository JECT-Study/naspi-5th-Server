package kr.ject.naspi.market.application;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종목 상장.
 *
 * <p>정책 (docs/policies/v1/domain/market.md "종목 상장"): 각 사용자는 하나의 리그에서 하나의 종목만
 * 상장할 수 있고, 이름과 소개는 필수이며, 이름은 같은 리그 안에서 중복될 수 없다.
 *
 * <p>TODO: 리그 상태(대기실인지)와 회원의 플레이어 ID 해석은 league 구현 후 연동한다.
 * 지금은 호출하는 쪽이 검증을 마치고 {@code listerPlayerId}를 넘긴다고 가정한다.
 */
@Service
@RequiredArgsConstructor
public class StockListingService {

    private final StockRepository stockRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 종목을 상장하고 {@link StockListed}를 발행한다.
     *
     * @param leagueId 리그 ID
     * @param listerPlayerId 상장자 플레이어 ID
     * @param name 종목 이름 (앞뒤 공백은 제거된다)
     * @param description 종목 소개
     * @return 상장된 종목 ID
     * @throws NaspiBizException 이름·소개가 규칙에 맞지 않거나, 이름이 같은 리그에서 중복되거나,
     *         상장자가 이미 상장한 경우
     */
    @Transactional
    public long list(long leagueId, long listerPlayerId, String name, String description) {
        StockName stockName = StockName.of(name);
        StockDescription stockDescription = StockDescription.of(description);

        if (stockRepository.existsByListerPlayerId(listerPlayerId)) {
            throw new NaspiBizException(ErrorCode.STOCK_ALREADY_LISTED);
        }
        if (stockRepository.existsByLeagueIdAndName(leagueId, stockName)) {
            throw new NaspiBizException(ErrorCode.STOCK_NAME_DUPLICATED);
        }

        Stock stock = stockRepository.save(
                Stock.list(leagueId, listerPlayerId, stockName, stockDescription));
        eventPublisher.publishEvent(new StockListed(stock.getId(), leagueId, listerPlayerId));
        return stock.getId();
    }
}
