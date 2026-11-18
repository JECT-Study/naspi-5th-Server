package kr.ject.naspi.market.application;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.LeagueService;
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
 */
@Service
@RequiredArgsConstructor
public class StockListingService {

    private final StockRepository stockRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LeagueService leagueService;
    private final LeagueQueryService leagueQueryService;

    /**
     * 대기실에서 회원이 종목을 상장하고 {@link StockListed}를 발행한다. 상장을 마친 대기자는 참여자가 된다.
     *
     * <p>정책 (docs/policies/v1/domain/league.md "대기실"): 만료된 대기실과 시작된 리그에는 상장할 수 없다.
     * 리그 행을 잠근 뒤 확인하므로 시작·추방·퇴장·만료 처리와 겹치지 않는다.
     *
     * @param leagueId 리그 ID
     * @param memberId 상장하는 회원 ID
     * @param name 종목 이름 (앞뒤 공백은 제거된다)
     * @param description 종목 소개
     * @return 상장된 종목 ID
     * @throws NaspiBizException 만료·삭제된 대기실이거나, 이미 시작된 리그이거나, 리그에 입장하지 않았거나,
     *         이름·소개가 규칙에 맞지 않거나, 이름이 같은 리그에서 중복되거나, 상장자가 이미 상장한 경우
     */
    @Transactional
    public long list(long leagueId, long memberId, String name, String description) {
        leagueService.lockWaitingRoom(leagueId);
        long playerId = leagueQueryService.findPlayerId(memberId, leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.FORBIDDEN));
        long stockId = listStock(leagueId, playerId, name, description);
        leagueService.completeListing(playerId);
        return stockId;
    }

    /**
     * 리그를 만드는 중 방장의 종목을 플레이어 ID로 상장한다. 리그 상태 확인과 대기자 -> 참여자 전환은
     * 호출하는 리그 생성 처리가 맡는다. 다른 흐름에서 쓰지 않는다.
     *
     * @param leagueId 방금 만든 리그 ID
     * @param hostPlayerId 방장 플레이어 ID
     * @param name 종목 이름 (앞뒤 공백은 제거된다)
     * @param description 종목 소개
     * @return 상장된 종목 ID
     * @throws NaspiBizException 이름·소개가 규칙에 맞지 않는 경우
     */
    @Transactional
    public long listHostStock(long leagueId, long hostPlayerId, String name, String description) {
        return listStock(leagueId, hostPlayerId, name, description);
    }

    private long listStock(long leagueId, long listerPlayerId, String name, String description) {
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

    /**
     * 대기실에서 퇴장하는 플레이어가 상장한 종목을 삭제한다. 상장 전 대기자라 종목이 없으면 아무것도 하지 않는다.
     *
     * <p>정책 (docs/policies/v1/domain/league.md "대기실"): 대기실에서 퇴장하면 그 리그에서 상장한 종목이 삭제된다.
     * 호출하는 퇴장 처리와 같은 트랜잭션에서 실행되므로 퇴장이 롤백되면 삭제도 롤백된다.
     * 다른 리그의 종목, 같은 리그의 다른 플레이어 종목은 지우지 않는다.
     *
     * @param leagueId 리그 ID
     * @param playerId 퇴장하는 플레이어 ID
     */
    @Transactional
    public void delist(long leagueId, long playerId) {
        stockRepository.deleteByLeagueIdAndListerPlayerId(leagueId, playerId);
    }
}
