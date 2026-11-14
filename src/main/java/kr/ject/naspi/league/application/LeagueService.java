package kr.ject.naspi.league.application;

import java.time.Clock;
import java.time.LocalDateTime;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대기실 입장과 상장 완료 처리.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "대기실"): 다른 사용자는 초대 코드로 대기실에 대기자로 입장한다.
 * 생성 후 7일이 지난 대기실의 초대 코드로는 입장할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final PlayerRepository playerRepository;
    private final LeagueJoinLimit leagueJoinLimit;
    private final Clock clock;

    /**
     * 초대 코드로 대기실에 대기자로 입장한다.
     *
     * <p>정책 (docs/policies/v1/domain/league.md "대기실"): 참여자 + 대기자는 최대 10명이며,
     * 10명이면 입장할 수 없다. 동시에 입장해 10명을 넘지 않도록 리그 행을 잠근 뒤 인원을 센다.
     * 회원은 대기실과 진행 중인 리그를 합쳐 최대 5개까지 참여할 수 있다.
     *
     * @param memberId 입장할 회원 ID
     * @param inviteCode 입력한 초대 코드 (대소문자 구분 없음)
     * @return 입장한 리그
     * @throws NaspiBizException 초대 코드에 해당하는 대기실이 없거나 (이미 시작된 리그, 만료된 대기실 포함),
     *         이미 입장한 리그이거나, 이미 리그 5개에 참여 중이거나, 리그 인원이 10명인 경우
     */
    @Transactional
    public JoinedLeague join(long memberId, String inviteCode) {

        // 초대 코드로 리그를 찾고 유효한 초대 코드인지 검증한다.
        LocalDateTime now = LocalDateTime.now(clock);
        League league = leagueRepository.findWithLockByInviteCode(InviteCode.of(inviteCode))
                .filter(found -> found.isOpenForInvitation(now))
                .orElseThrow(() -> new NaspiBizException(ErrorCode.INVITE_CODE_NOT_FOUND));

        // 이미 참여중인 리그인지 확인
        if (playerRepository.existsByLeagueIdAndMemberId(league.getId(), memberId)) {
            throw new NaspiBizException(ErrorCode.LEAGUE_ALREADY_JOINED);
        }

        // 동시 참여 리그 수 제한 확인
        leagueJoinLimit.check(memberId);

        // 참여자 + 대기자 인원 제한 확인
        if (playerRepository.countByLeagueId(league.getId()) >= League.MAX_PLAYERS) {
            throw new NaspiBizException(ErrorCode.LEAGUE_FULL);
        }

        // 리그에 플레이어 생성
        Player player = playerRepository.save(
                Player.enter(league.getId(), memberId, LocalDateTime.now(clock)));
        return new JoinedLeague(league.getId(), player.getId());
    }

    /**
     * 상장을 마친 대기자를 참여자로 바꾸고 상장일시를 기록한다.
     *
     * <p>상장과 같은 트랜잭션에서 호출한다 (일반 상장 연동은 LEA-05).
     *
     * @param playerId 상장한 플레이어 ID
     * @throws NaspiRuntimeException 플레이어가 없는 경우
     */
    @Transactional
    public void completeListing(long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NaspiRuntimeException(
                        "상장한 플레이어를 찾을 수 없습니다. playerId=" + playerId));
        player.completeListing(LocalDateTime.now(clock));
    }
}
