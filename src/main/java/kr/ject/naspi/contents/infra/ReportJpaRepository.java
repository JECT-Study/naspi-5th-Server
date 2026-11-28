package kr.ject.naspi.contents.infra;

import kr.ject.naspi.contents.domain.Report;
import kr.ject.naspi.contents.domain.ReportRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link ReportRepository}의 Spring Data 구현.
 */
interface ReportJpaRepository extends Repository<Report, Long>, ReportRepository {
}
