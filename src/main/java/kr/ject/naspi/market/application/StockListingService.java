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
 * <p>TODO: league 구현 후 league 서비스를 직접 호출해 연동한다. 리그 상태(대기실인지, 참여자가
 * 10명인지) 검증, 회원의 플레이어 ID 해석, 상장 후 대기자 -> 참여자 전환(LEA-05)이 대상이다.
 * 지금은 회원 ID를 플레이어 ID 자리에 임시로 쓴다.
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
     * @param memberId 상장하는 회원 ID
     * @param name 종목 이름 (앞뒤 공백은 제거된다)
     * @param description 종목 소개
     * @return 상장된 종목 ID
     * @throws NaspiBizException 이름·소개가 규칙에 맞지 않거나, 이름이 같은 리그에서 중복되거나,
     *         상장자가 이미 상장한 경우
     */
    @Transactional
    public long list(long leagueId, long memberId, String name, String description) {
        // TODO: league 연동 전까지 회원 ID를 플레이어 ID로 임시 사용한다.
        long listerPlayerId = memberId;
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
        // TODO: 상장 시 대기자 -> 참여자 전환 (league 서비스 호출, LEA-05).
        //       연동 전에는 상장해도 대기자로 남는다.
        eventPublisher.publishEvent(new StockListed(stock.getId(), leagueId, listerPlayerId));
        return stock.getId();
    }
}
