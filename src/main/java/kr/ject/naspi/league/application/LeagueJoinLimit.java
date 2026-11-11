package kr.ject.naspi.league.application;

import java.util.EnumSet;
import java.util.Set;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 회원이 동시에 참여할 수 있는 리그 수 제한.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 생성 및 초대"): 동시 참여 가능한 리그는 최대 5개이며,
 * 대기실과 진행 중인 리그를 포함하고 종료된 리그는 제외한다. 리그 생성과 대기실 입장 시 모두 확인한다.
 *
 * <p>같은 회원이 동시에 여러 리그를 생성·입장하는 경우는 막지 않는다.
 */
@Component
@RequiredArgsConstructor
class LeagueJoinLimit {

    /** 동시에 참여할 수 있는 최대 리그 수. */
    static final int MAX_JOINED_LEAGUES = 5;

    /** 참여 개수에 포함하는 리그 상태 (대기실, 진행 중). */
    private static final Set<LeagueStatus> COUNTED_STATUSES =
            EnumSet.of(LeagueStatus.WAITING, LeagueStatus.STARTED);

    private final PlayerRepository playerRepository;

    /**
     * 회원이 리그에 하나 더 참여할 수 있는지 확인한다.
     *
     * @param memberId 리그를 생성하거나 입장하려는 회원 ID
     * @throws NaspiBizException 이미 대기실과 진행 중인 리그를 합쳐 5개에 참여 중인 경우
     */
    void check(long memberId) {
        long joined = playerRepository.countLeaguesByMemberIdAndStatusIn(memberId,
                COUNTED_STATUSES);
        if (joined >= MAX_JOINED_LEAGUES) {
            throw new NaspiBizException(ErrorCode.LEAGUE_JOIN_LIMIT_EXCEEDED);
        }
    }
}
