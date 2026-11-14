package kr.ject.naspi.league.domain;

import java.time.LocalDateTime;
import java.util.List;
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
     * 리그 ID로 리그를 찾는다.
     *
     * @param id 리그 ID
     * @return 리그, 없으면 빈 값
     */
    Optional<League> findById(Long id);

    /**
     * 리그 ID로 리그를 찾아 비관적 쓰기 락을 건다. 리그 시작 중에 같은 리그로 입장하거나
     * 대기실이 만료 처리되는 요청이 끼어들지 않게 한다.
     *
     * @param id 리그 ID
     * @return 리그, 없으면 빈 값
     */
    Optional<League> findWithLockById(Long id);

    /**
     * 초대 코드로 리그를 찾는다 (락 없음). 초대가 마감된 리그는 코드가 비어 있어 찾을 수 없다.
     *
     * @param inviteCode 초대 코드
     * @return 리그, 없으면 빈 값
     */
    Optional<League> findByInviteCode(InviteCode inviteCode);

    /**
     * 초대 코드로 리그를 찾아 비관적 쓰기 락을 건다. 초대가 마감된 리그는 코드가 비어 있어 찾을 수 없다.
     * 락은 현재 트랜잭션이 끝날 때 풀리므로, 같은 리그에 동시에 입장하는 요청은 차례로 처리된다.
     *
     * @param inviteCode 초대 코드
     * @return 리그, 없으면 빈 값
     */
    Optional<League> findWithLockByInviteCode(InviteCode inviteCode);

    /**
     * 초대 코드를 쓰고 있는 리그가 있는지 확인한다.
     *
     * @param inviteCode 초대 코드
     * @return 존재 여부
     */
    boolean existsByInviteCode(InviteCode inviteCode);

    /**
     * 상태가 같은 리그의 ID를 모두 조회한다.
     *
     * @param status 리그 상태
     * @return 리그 ID 목록
     */
    List<Long> findIdsByStatus(LeagueStatus status);

    /**
     * 주어진 상태이면서 기준 일시 이전에(같은 시각 포함) 생성된 리그를 모두 찾아 비관적 쓰기 락을 건다.
     * 만료 처리 중에 같은 대기실로 입장하는 요청이 끼어들지 않게 한다.
     *
     * @param status 리그 상태
     * @param createdAt 기준 생성 일시
     * @return 리그 목록
     */
    List<League> findAllWithLockByStatusAndCreatedAtLessThanEqual(LeagueStatus status,
            LocalDateTime createdAt);
}
