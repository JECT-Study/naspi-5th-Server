package kr.ject.naspi.asset.infra;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.asset.domain.HoldingRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/**
 * {@link HoldingRepository}의 Spring Data 구현.
 */
interface HoldingJpaRepository extends Repository<Holding, Long>, HoldingRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Holding> findWithLockByLeagueIdAndPlayerIdAndStockId(Long leagueId, Long playerId,
            Long stockId);
}
