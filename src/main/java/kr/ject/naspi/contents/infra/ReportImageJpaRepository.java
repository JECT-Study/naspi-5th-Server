package kr.ject.naspi.contents.infra;

import kr.ject.naspi.contents.domain.ReportImage;
import kr.ject.naspi.contents.domain.ReportImageRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link ReportImageRepository}의 Spring Data 구현.
 */
interface ReportImageJpaRepository extends Repository<ReportImage, Long>, ReportImageRepository {
}
