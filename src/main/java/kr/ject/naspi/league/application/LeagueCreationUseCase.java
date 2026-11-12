package kr.ject.naspi.league.application;

import java.time.Clock;
import java.time.LocalDateTime;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.PlayerRepository;
import kr.ject.naspi.market.application.StockListingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 생성.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 생성 및 초대"): 방장이 리그 이름을 지정하고
 * 상장을 완료하면 리그가 생성된다. 리그를 만들면 대기실과 공통 초대 코드 1개가 생긴다.
 * 리그와 방장은 league가 직접 만들고, 하위 모듈인 market에는 방장의 종목 상장만 요청하므로 UseCase로 만든다.
 */
@Service
@RequiredArgsConstructor
public class LeagueCreationUseCase {

    private final LeagueRepository leagueRepository;
    private final PlayerRepository playerRepository;
    private final LeagueJoinLimit leagueJoinLimit;
    private final InviteCodeIssuer inviteCodeIssuer;
    private final StockListingService stockListingService;
    private final Clock clock;

    /**
     * 리그를 만들고 방장의 종목을 상장해, 방장이 참여자인 대기실을 연다. 한 트랜잭션에서 처리하므로
     * 리그 이름이나 종목이 규칙에 맞지 않으면 리그도 만들어지지 않는다.
     *
     * <p>초대 코드는 발급 전에 사용 중인 코드와 겹치는지 확인한다. 동시에 같은 코드를 뽑는 경우는 확률이 매우 낮아
     * 따로 막지 않고, DB UNIQUE 제약이 중복 저장만 막는다 (나중 요청은 실패).
     *
     * @param memberId 방장이 될 회원 ID
     * @param name 리그 이름
     * @param stockName 방장의 종목 이름
     * @param stockDescription 방장의 종목 소개
     * @return 생성된 리그
     * @throws NaspiBizException 리그 이름이 규칙(1~12자)에 맞지 않거나, 이미 리그 5개에 참여 중이거나,
     *         방장의 종목이 상장 규칙에 맞지 않는 경우
     */
    @Transactional
    public CreatedLeague create(long memberId, String name, String stockName,
            String stockDescription) {
        // 동시 참여 리그 수 제한 확인 (생성하면 방장으로 참여하므로)
        leagueJoinLimit.check(memberId);

        LeagueName leagueName = LeagueName.of(name);
        InviteCode inviteCode = inviteCodeIssuer.issue();

        // 리그와 방장 생성
        LocalDateTime now = LocalDateTime.now(clock);
        League league = leagueRepository.save(League.create(leagueName, inviteCode, now));
        Player host = playerRepository.save(Player.enter(league.getId(), memberId, now));
        league.assignHost(host.getId());

        // market에 방장의 종목 상장 요청
        // TODO: market에 플레이어 ID로 종목만 상장하는 서비스가 생기면 그것으로 바꾼다.
        //       StockListingService.list는 회원 ID를 받지만 지금은 그 값을 플레이어 ID로 그대로 쓰므로
        //       방장 플레이어 ID를 넘긴다.
        stockListingService.list(league.getId(), host.getId(), stockName, stockDescription);

        // 상장을 마친 방장은 참여자
        host.completeListing(now);
        return new CreatedLeague(league.getId(), host.getId(), inviteCode.getValue());
    }
}
