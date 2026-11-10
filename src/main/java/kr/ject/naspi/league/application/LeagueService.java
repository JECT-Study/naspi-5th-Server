package kr.ject.naspi.league.application;

import java.time.Clock;
import java.time.LocalDateTime;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 생성과 대기실 입장.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 생성 및 초대", "대기실"): 방장이 리그 이름을 정하고
 * 상장을 마치면 리그가 생성된다. 리그를 만들면 대기실과 공통 초대 코드 1개가 생기고,
 * 다른 사용자는 초대 코드로 대기실에 대기자로 입장한다.
 *
 * <p>TODO: 동시 참여 리그 수 제한(LEA-02)과 대기실 7일 만료(LEA-08)는 아직 검증하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final PlayerRepository playerRepository;
    private final InviteCodeIssuer inviteCodeIssuer;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 리그를 만들고 회원을 방장으로 입장시킨 뒤, 방장의 종목을 상장해 리그 생성을 완료한다.
     *
     * <p>league는 market을 호출할 수 없으므로 상장은 {@link LeagueCreated} 이벤트로 market에 맡긴다.
     * market은 같은 트랜잭션에서 상장을 마치고 {@link #completeListing(long)}으로 방장을 참여자로 바꾼다.
     * 종목이 상장 규칙에 맞지 않으면 예외가 전파되어 리그도 만들어지지 않는다.
     *
     * <p>초대 코드는 발급 전에 사용 중인 코드와 겹치는지 확인한다. 동시에 같은 코드를 뽑는 경우는 확률이 매우 낮아
     * 따로 막지 않고, DB UNIQUE 제약이 중복 저장만 막는다 (나중 요청은 실패).
     *
     * @param memberId 방장이 될 회원 ID
     * @param name 리그 이름
     * @param stockName 방장의 종목 이름
     * @param stockDescription 방장의 종목 소개
     * @return 생성된 리그
     * @throws NaspiBizException 리그 이름이 규칙(1~12자)에 맞지 않거나, 방장의 종목이 상장 규칙에 맞지 않는 경우
     */
    @Transactional
    public CreatedLeague create(long memberId, String name, String stockName,
            String stockDescription) {
        LeagueName leagueName = LeagueName.of(name);
        InviteCode inviteCode = inviteCodeIssuer.issue();

        LocalDateTime now = LocalDateTime.now(clock);
        League league = leagueRepository.save(League.create(leagueName, inviteCode, now));
        Player host = playerRepository.save(Player.enter(league.getId(), memberId, now));
        league.assignHost(host.getId());

        eventPublisher.publishEvent(
                new LeagueCreated(league.getId(), host.getId(), stockName, stockDescription));
        return new CreatedLeague(league.getId(), host.getId(), inviteCode.getValue());
    }

    /**
     * 초대 코드로 대기실에 대기자로 입장한다.
     *
     * @param memberId 입장할 회원 ID
     * @param inviteCode 입력한 초대 코드 (대소문자 구분 없음)
     * @return 입장한 리그
     * @throws NaspiBizException 초대 코드에 해당하는 대기실이 없거나 (이미 시작된 리그 포함),
     *         이미 입장한 리그인 경우
     */
    @Transactional
    public JoinedLeague join(long memberId, String inviteCode) {

        // 초대 코드로 리그를 찾고 유효한 초대 코드인지 검증한다.내가 잓
        League league = leagueRepository.findByInviteCode(InviteCode.of(inviteCode))
                .filter(found -> found.getStatus() == LeagueStatus.WAITING)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.INVITE_CODE_NOT_FOUND));

        // 이미 참여중인 리그인지 확인
        if (playerRepository.existsByLeagueIdAndMemberId(league.getId(), memberId)) {
            throw new NaspiBizException(ErrorCode.LEAGUE_ALREADY_JOINED);
        }

        // 리그에 플레이어 생성
        Player player = playerRepository.save(
                Player.enter(league.getId(), memberId, LocalDateTime.now(clock)));
        return new JoinedLeague(league.getId(), player.getId());
    }

    /**
     * 상장을 마친 대기자를 참여자로 바꾸고 상장일시를 기록한다.
     *
     * <p>league는 market의 상장 이벤트를 구독할 수 없으므로(league가 market을 참조하면 안 됨)
     * 상장을 처리한 market 쪽이 같은 트랜잭션에서 호출한다.
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
