package kr.ject.naspi.order.domain;

import java.util.List;
import java.util.Optional;

/**
 * 주문 저장소. 구현은 infra의 {@code OrderJpaRepository}가 맡는다.
 */
public interface OrderRepository {

    /**
     * 주문을 저장한다.
     *
     * @param order 주문
     * @return 저장된 주문
     */
    <S extends Order> S save(S order);

    /**
     * 주문을 조회한다.
     *
     * @param id 주문 ID
     * @return 주문. 없으면 비어 있다
     */
    Optional<Order> findById(Long id);

    /**
     * 종목에서 체결을 기다리는 지정가 주문을 접수 순서로 조회한다. 호가창을 만드는 데 쓴다.
     *
     * @param stockId 종목 ID
     * @param type 주문 유형 (지정가)
     * @param statuses 체결 대기 상태 (대기, 부분 체결)
     * @return 주문 목록. 접수 순서(id 오름차순)
     */
    List<Order> findAllByStockIdAndTypeAndStatusInOrderByIdAsc(Long stockId, OrderType type,
            List<OrderStatus> statuses);
}
