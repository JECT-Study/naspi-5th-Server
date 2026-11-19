package kr.ject.naspi.contents.domain;

import java.util.List;
import java.util.Optional;

/**
 * 공시 저장소. 구현은 infra의 {@code DisclosureJpaRepository}가 맡는다.
 */
public interface DisclosureRepository {

    /**
     * 공시를 저장한다.
     *
     * @param disclosure 공시
     * @return 저장된 공시 (ID 발급됨)
     */
    Disclosure save(Disclosure disclosure);

    /**
     * 공시를 ID로 찾는다. 삭제된 공시도 돌려주므로 조회 용도라면 호출하는 쪽이 삭제 여부를 확인한다.
     *
     * @param id 공시 ID
     * @return 공시
     */
    Optional<Disclosure> findById(Long id);

    /**
     * 종목의 게시 중인(삭제되지 않은) 공시를 최신순으로 커서 이후부터 찾는다.
     *
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param cursor 이 ID보다 작은(이전에 작성된) 공시부터 찾는다. 첫 페이지면 null
     * @param limit 최대 개수
     * @return 공시 목록 (ID 내림차순)
     */
    List<Disclosure> findPublishedByStock(long leagueId, long stockId, Long cursor, int limit);
}
