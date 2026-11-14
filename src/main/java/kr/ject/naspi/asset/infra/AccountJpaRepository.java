package kr.ject.naspi.asset.infra;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.AccountRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/**
 * {@link AccountRepository}의 Spring Data 구현.
 */
interface AccountJpaRepository extends Repository<Account, Long>, AccountRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findWithLockByLeagueIdAndPlayerId(Long leagueId, Long playerId);
}
