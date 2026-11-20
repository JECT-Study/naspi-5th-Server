package kr.ject.naspi.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderExecutionTotalTest {

    @Test
    @DisplayName("체결 단가가 한 가지면 그 단가를 주당 체결 단가로 제공한다")
    void singlePrice() {
        OrderExecutionTotal total = new OrderExecutionTotal(1, 5, 55_000, 11_000, 11_000);

        assertThat(total.hasSinglePrice()).isTrue();
        assertThat(total.executionPrice()).isEqualTo(11_000L);
    }

    @Test
    @DisplayName("체결 단가가 둘 이상이면 총 체결 금액 ÷ 총 체결 수량으로 평균 체결 단가를 제공한다")
    void averagePrice() {
        OrderExecutionTotal total = new OrderExecutionTotal(1, 4, 42_000, 10_000, 11_000);

        assertThat(total.hasSinglePrice()).isFalse();
        assertThat(total.executionPrice()).isEqualTo(10_500L);
    }

    @Test
    @DisplayName("평균 체결 단가는 원 미만을 절사한다")
    void averagePriceTruncatesBelowWon() {
        // 10,000원 1주 + 10,100원 2주 = 30,200원 / 3주 = 10,066.67원
        OrderExecutionTotal total = new OrderExecutionTotal(1, 3, 30_200, 10_000, 10_100);

        assertThat(total.executionPrice()).isEqualTo(10_066L);
    }

    @Test
    @DisplayName("체결 수량이 0주면 체결 단가를 제공하지 않는다")
    void noExecution() {
        OrderExecutionTotal total = OrderExecutionTotal.none(1);

        assertThat(total.hasSinglePrice()).isFalse();
        assertThat(total.executionPrice()).isNull();
    }
}
