package kr.ject.naspi.league.infra;

import kr.ject.naspi.league.domain.DailyAssetSnapshot;
import kr.ject.naspi.league.domain.DailyAssetSnapshotRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link DailyAssetSnapshotRepository}의 Spring Data 구현.
 */
interface DailyAssetSnapshotJpaRepository
        extends Repository<DailyAssetSnapshot, Long>, DailyAssetSnapshotRepository {
}
