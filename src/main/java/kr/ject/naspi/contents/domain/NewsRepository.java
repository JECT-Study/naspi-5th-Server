package kr.ject.naspi.contents.domain;

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
}
