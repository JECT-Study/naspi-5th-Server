package kr.ject.naspi.order.infra;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/**
 * {@link OrderRepository}의 Spring Data 구현.
 */
interface OrderJpaRepository extends Repository<Order, Long>, OrderRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findWithLockById(Long id);
}
