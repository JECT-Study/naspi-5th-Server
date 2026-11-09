package kr.ject.naspi.asset.infra;

import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.AccountRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link AccountRepository}의 Spring Data 구현.
 */
interface AccountJpaRepository extends Repository<Account, Long>, AccountRepository {
}
