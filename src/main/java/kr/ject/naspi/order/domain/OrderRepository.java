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
     * 주문을 비관적 쓰기 락으로 조회한다. 취소처럼 주문 상태를 바꾸기 전에 쓴다.
     *
     * <p>같은 리그의 주문 처리는 리그 거래 락으로 이미 직렬화되지만, 주문 상태 전환과 예약 해제가 한 번만
     * 일어나도록 주문 행도 잠근다. 락 순서는 리그 락 → 주문 락 → asset 락이다.
     *
     * @param id 주문 ID
     * @return 주문. 없으면 비어 있다
     */
    Optional<Order> findWithLockById(Long id);

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
