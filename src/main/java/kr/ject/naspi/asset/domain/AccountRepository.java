package kr.ject.naspi.asset.domain;

import java.util.List;

/**
 * 현금 계좌 저장소. 구현은 infra의 {@code AccountJpaRepository}가 맡는다.
 */
public interface AccountRepository {

    /**
     * 계좌를 모두 저장한다.
     *
     * @param accounts 계좌 목록
     * @return 저장된 계좌 목록
     */
    <S extends Account> List<S> saveAll(Iterable<S> accounts);

    /**
     * 리그의 계좌를 모두 조회한다.
     *
     * @param leagueId 리그 ID
     * @return 계좌 목록
     */
    List<Account> findAllByLeagueId(Long leagueId);

    /**
     * 리그에 개설된 계좌가 있는지 확인한다. 초기 자산 배분 여부 판단에 쓴다.
     *
     * @param leagueId 리그 ID
     * @return 계좌 존재 여부
     */
    boolean existsByLeagueId(Long leagueId);
}
