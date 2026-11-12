package kr.ject.naspi.league.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 플레이어 저장소. 구현은 infra의 {@code PlayerJpaRepository}가 맡는다.
 */
public interface PlayerRepository {

    /**
     * 플레이어를 저장한다.
     *
     * @param player 플레이어
     * @return 저장된 플레이어 (ID 발급됨)
     */
    Player save(Player player);

    /**
     * 플레이어 ID로 플레이어를 찾는다.
     *
     * @param id 플레이어 ID
     * @return 플레이어, 없으면 빈 값
     */
    Optional<Player> findById(Long id);

    /**
     * 회원이 이미 리그에 입장했는지 확인한다.
     *
     * @param leagueId 리그 ID
     * @param memberId 회원 ID
     * @return 입장 여부
     */
    boolean existsByLeagueIdAndMemberId(Long leagueId, Long memberId);

    /**
     * 리그에 입장한 플레이어(참여자 + 대기자) 수를 센다.
     *
     * @param leagueId 리그 ID
     * @return 플레이어 수
     */
    long countByLeagueId(Long leagueId);

    /**
     * 리그에서 상장을 마친 참여자 수를 센다.
     *
     * @param leagueId 리그 ID
     * @return 참여자 수
     */
    long countByLeagueIdAndListedTrue(Long leagueId);

    /**
     * 리그에 입장한 플레이어(참여자 + 대기자)를 모두 찾는다. 한 리그는 최대 10명이라 한 번에 조회한다.
     *
     * @param leagueId 리그 ID
     * @return 플레이어 목록
     */
    List<Player> findAllByLeagueId(Long leagueId);

    /**
     * 회원이 입장한 리그 중 주어진 상태인 리그 수를 센다. 대기실({@link LeagueStatus#WAITING})은
     * 기준 일시보다 뒤에 생성된 것만 세어, 7일이 지났지만 아직 만료 처리되지 않은 대기실을 제외한다.
     *
     * @param memberId 회원 ID
     * @param statuses 셀 리그 상태
     * @param waitingCreatedAfter 셀 대기실의 생성 일시 하한 (이 시각에 생성된 대기실은 제외)
     * @return 리그 수
     */
    long countLeaguesByMemberIdAndStatusIn(Long memberId, Collection<LeagueStatus> statuses,
            LocalDateTime waitingCreatedAfter);
}
