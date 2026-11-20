package kr.ject.naspi.contents.infra;

import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link NewsRepository}의 Spring Data 구현.
 */
interface NewsJpaRepository extends Repository<News, Long>, NewsRepository {
}
