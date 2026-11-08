package kr.ject.naspi.asset.application;

import java.util.List;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.AccountRepository;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.asset.domain.HoldingRepository;
import kr.ject.naspi.asset.domain.InitialAllocation;
import kr.ject.naspi.asset.domain.InitialAllocation.PlayerAllocation;
import kr.ject.naspi.asset.domain.StockListing;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 시작 시 초기 자산 배분.
 *
 * <p>정책 (docs/policies/v1/domain/asset.md "초기 자산 배분"): 리그 시작 시 일괄 배분하고,
 * 배분이 완료되지 않으면 거래할 수 없다. 모든 참여자의 계좌와 보유 주식을 한 트랜잭션에서 저장해
 * 일부만 배분된 상태가 남지 않게 한다.
 *
 * <p>리그 시작 이벤트는 {@link LeagueStartedListener}가 받아 호출한다.
 */
@Service
@RequiredArgsConstructor
public class InitialAssetAllocationService {

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;

    /**
     * 리그 참여자 전원에게 초기 주식과 현금을 배분한다.
     *
     * @param leagueId 리그 ID
     * @param listings 리그의 모든 상장 종목과 상장자 (참여자당 1종목)
     * @throws IllegalArgumentException 상장 종목 목록이 배분 조건에 맞지 않는 경우
     * @throws NaspiRuntimeException 이미 배분된 리그인 경우
     */
    @Transactional
    public void allocate(long leagueId, List<StockListing> listings) {
        if (accountRepository.existsByLeagueId(leagueId)) {
            throw new NaspiRuntimeException("이미 초기 자산이 배분된 리그입니다: " + leagueId);
        }

        // 모든 종목에 대해 자산 배분 처리
        InitialAllocation allocation = InitialAllocation.calculate(listings);

        List<Account> accounts = allocation.allocations().stream()
                .map(player -> Account.open(leagueId, player.playerId(), player.cash()))
                .toList();
        List<Holding> holdings = allocation.allocations().stream()
                .flatMap(player -> holdingsOf(leagueId, player).stream())
                .toList();

        accountRepository.saveAll(accounts);
        holdingRepository.saveAll(holdings);
    }

    private static List<Holding> holdingsOf(long leagueId, PlayerAllocation player) {
        return player.shares().entrySet().stream()
                .map(share -> Holding.allocate(
                        leagueId, player.playerId(), share.getKey(), share.getValue()))
                .toList();
    }
}
