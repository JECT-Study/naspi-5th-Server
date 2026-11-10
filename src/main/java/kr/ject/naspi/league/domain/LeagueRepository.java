package kr.ject.naspi.league.domain;

import java.util.Optional;

/**
 * 리그 저장소. 구현은 infra의 {@code LeagueJpaRepository}가 맡는다.
 */
public interface LeagueRepository {

    /**
     * 리그를 저장한다.
     *
     * @param league 리그
     * @return 저장된 리그 (ID 발급됨)
     */
    League save(League league);

    /**
     * 초대 코드로 리그를 찾는다. 초대가 마감된 리그는 코드가 비어 있어 찾을 수 없다.
     *
     * @param inviteCode 초대 코드
     * @return 리그, 없으면 빈 값
     */
    Optional<League> findByInviteCode(InviteCode inviteCode);

    /**
     * 초대 코드를 쓰고 있는 리그가 있는지 확인한다.
     *
     * @param inviteCode 초대 코드
     * @return 존재 여부
     */
    boolean existsByInviteCode(InviteCode inviteCode);
}
