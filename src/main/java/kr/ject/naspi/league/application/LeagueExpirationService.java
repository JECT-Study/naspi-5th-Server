package kr.ject.naspi.league.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.LeagueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시작되지 않은 대기실 만료.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 생성 및 초대"): 대기실 생성 이후 7일 내에 리그가 시작되지 않으면
 * 대기실과 초대 코드가 만료된다. 만료된 대기실은 접근할 수 없고, 동시 참여 리그 수에서 빠지며,
 * 폐기된 초대 코드는 다른 리그에 다시 발급될 수 있다.
 *
 * <p>주기적으로 실행되어 만료 상태를 저장한다. 실행 사이의 짧은 틈은 조회·입장 시점의 만료 판정
 * ({@link League#isExpired})이 막는다.
 */
@Service
@RequiredArgsConstructor
public class LeagueExpirationService {

    private final LeagueRepository leagueRepository;
    private final Clock clock;

    /**
     * 생성 후 7일이 지난 대기실을 모두 만료시킨다.
     *
     * @return 만료시킨 대기실 수
     */
    @Transactional
    public int expireOverdueWaitingRooms() {
        LocalDateTime now = LocalDateTime.now(clock);
        // 생성된지 7일이 지난 대기중인 리그 조회
        List<League> overdue = leagueRepository.findAllWithLockByStatusAndCreatedAtLessThanEqual(
                LeagueStatus.WAITING, now.minus(League.WAITING_ROOM_LIFETIME));
        overdue.forEach(league -> league.expire(now));  // 대기실 만료
        return overdue.size();
    }
}
