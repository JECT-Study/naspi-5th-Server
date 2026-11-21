package kr.ject.naspi.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    @DisplayName("완료된 주문은 수량의 일부 또는 전체가 체결·취소된 주문이며 전량 대기 중인 주문은 제외한다")
    void completedStatuses() {
        assertThat(OrderStatus.completedStatuses()).containsExactlyInAnyOrder(
                OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.CANCELED,
                OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("대기 주문은 미체결 잔여 수량이 있는 대기·부분 체결 주문이다")
    void openStatuses() {
        assertThat(OrderStatus.openStatuses())
                .containsExactlyInAnyOrder(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED);
    }
}
