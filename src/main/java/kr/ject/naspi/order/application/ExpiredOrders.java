package kr.ject.naspi.order.application;

import java.util.Set;

/**
 * 거래일 만료 결과.
 *
 * @param count 만료 처리한 주문 수
 * @param playerIds 주문이 만료된 플레이어 ID
 */
public record ExpiredOrders(int count, Set<Long> playerIds) {
}
