package kr.ject.naspi.order.infra;

import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link OrderRepository}의 Spring Data 구현.
 */
interface OrderJpaRepository extends Repository<Order, Long>, OrderRepository {
}
