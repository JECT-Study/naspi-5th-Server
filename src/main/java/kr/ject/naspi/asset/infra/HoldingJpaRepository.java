package kr.ject.naspi.asset.infra;

import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.asset.domain.HoldingRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link HoldingRepository}의 Spring Data 구현.
 */
interface HoldingJpaRepository extends Repository<Holding, Long>, HoldingRepository {
}
