package kr.ject.naspi.contents.domain;

import java.util.List;
import java.util.Optional;

/**
 * 뉴스 저장소. 구현은 infra의 {@code NewsJpaRepository}가 맡는다.
 */
public interface NewsRepository {

    /**
     * 뉴스를 저장한다.
     *
     * @param news 뉴스
     * @return 저장된 뉴스 (ID 발급됨)
     */
    News save(News news);

    /**
     * 공시로 만든 뉴스를 찾는다.
     *
     * @param disclosureId 공시 ID
     * @return 뉴스 (상태와 관계없이)
     */
    Optional<News> findByDisclosureId(long disclosureId);

    /**
     * 공시로 만든 뉴스를 쓰기 잠금을 걸고 찾는다. 같은 뉴스의 동시 재생성 요청을 직렬화한다.
     *
     * @param disclosureId 공시 ID
     * @return 뉴스 (상태와 관계없이)
     */
    Optional<News> findWithLockByDisclosureId(long disclosureId);

    /**
     * 뉴스를 ID로 찾는다.
     *
     * @param id 뉴스 ID
     * @return 뉴스 (상태와 관계없이)
     */
    Optional<News> findById(Long id);

    /**
     * 종목의 발행된(PUBLISHED) 뉴스를 최신순(ID 내림차순)으로 찾는다.
     *
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param cursor 이 ID보다 작은 뉴스만 찾는다. 첫 페이지면 null
     * @param limit 최대 개수
     * @return 뉴스 목록
     */
    List<News> findPublishedByStock(long leagueId, long stockId, Long cursor, int limit);
}
