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
     * 일어나도록 주문 행도 잠근다. 락 순서는 리그 락 → 주문 락 → asset 락이다. 주문 상태를 바꾸는 모든
     * 경로가 리그 락 안에 있어 이 행 락은 중복 방어다. 제거 여부는 ND-43 확정 후 판단한다.
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

    /**
     * 거래일 만료 대상 주문을 접수 순서로 조회한다. 리그에서 마감하는 거래일 이하에 접수한 주어진 유형·상태의
     * 주문이다.
     *
     * @param leagueId 리그 ID
     * @param type 주문 유형 (지정가)
     * @param statuses 열린 상태 (대기, 부분 체결)
     * @param tradingDay 마감하는 거래일 일차 (이 일차 이하)
     * @return 주문 목록. 접수 순서(id 오름차순)
     */
    List<Order> findAllByLeagueIdAndTypeAndStatusInAndTradingDayLessThanEqualOrderByIdAsc(
            Long leagueId, OrderType type, List<OrderStatus> statuses, int tradingDay);

    /**
     * 플레이어가 접수한 주문 중 주어진 상태의 주문을 접수 역순으로 조회한다.
     *
     * @param playerId 플레이어 ID
     * @param statuses 조회할 주문 상태
     * @return 주문 목록. 접수 역순(id 내림차순)
     */
    List<Order> findAllByPlayerIdAndStatusInOrderByIdDesc(Long playerId,
            List<OrderStatus> statuses);

    /**
     * 플레이어가 접수한 주문 중 특정 종목의 주어진 상태 주문을 접수 역순으로 조회한다.
     *
     * @param playerId 플레이어 ID
     * @param stockId 종목 ID
     * @param statuses 조회할 주문 상태
     * @return 주문 목록. 접수 역순(id 내림차순)
     */
    List<Order> findAllByPlayerIdAndStockIdAndStatusInOrderByIdDesc(Long playerId, Long stockId,
            List<OrderStatus> statuses);
}
