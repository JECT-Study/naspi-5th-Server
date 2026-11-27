package kr.ject.naspi.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import kr.ject.naspi.order.domain.Execution;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@Import(TradeQueryService.class)
class TradeQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 12, 0);

    @Autowired
    private TradeQueryService service;

    @Autowired
    private TestEntityManager entityManager;

    private void save(long stockId, int tradingDay, long price, long quantity,
            LocalDateTime executedAt) {
        Order sell = order(10, stockId, OrderSide.SELL, price, quantity, tradingDay);
        Order buy = order(20, stockId, OrderSide.BUY, price, quantity, tradingDay);
        entityManager.persist(Trade.record(new Execution(sell, buy, price, quantity), tradingDay,
                executedAt));
    }

    private Order order(long playerId, long stockId, OrderSide side, long price, long quantity,
            int tradingDay) {
        Order order = Order.limit(1L, playerId, stockId, side, price, quantity, tradingDay, NOW);
        ReflectionTestUtils.setField(order, "id", 1L);
        return order;
    }

    @Test
    @DisplayName("종목의 해당 거래일 체결 수량·금액 합계를 구하고 체결이 없으면 0이다")
    void dailyTotalSumsOnlyThatDay() {
        save(1L, 7, 10_000, 10, NOW);
        save(1L, 7, 11_000, 5, NOW.plusMinutes(1));
        save(1L, 6, 20_000, 7, NOW.minusDays(1));
        save(2L, 7, 30_000, 9, NOW);
        entityManager.flush();

        assertThat(service.getDailyTotal(1L, 7)).isEqualTo(new TradeDailyTotal(15, 155_000));
        assertThat(service.getDailyTotal(1L, 5)).isEqualTo(new TradeDailyTotal(0, 0));
    }

    @Test
    @DisplayName("최근 체결은 체결 일시 역순, 같은 시각이면 나중에 저장된 체결이 먼저이며 다른 종목은 제외한다")
    void findRecentOrdersByExecutedAtThenIdDesc() {
        save(1L, 1, 10_000, 1, NOW);
        save(1L, 1, 10_100, 2, NOW.plusMinutes(1));
        save(1L, 1, 10_200, 3, NOW.plusMinutes(1));
        save(2L, 1, 99_000, 4, NOW.plusMinutes(2));
        entityManager.flush();

        assertThat(service.findRecent(1L, 500)).containsExactly(new TradeRecord(10_200, 3),
                new TradeRecord(10_100, 2), new TradeRecord(10_000, 1));
    }

    @Test
    @DisplayName("최근 체결은 최대 limit건만 조회하고 가장 오래된 체결부터 제외한다")
    void findRecentRespectsLimit() {
        save(1L, 1, 10_000, 1, NOW);
        save(1L, 1, 10_100, 2, NOW.plusMinutes(1));
        save(1L, 1, 10_200, 3, NOW.plusMinutes(2));
        entityManager.flush();

        assertThat(service.findRecent(1L, 2)).containsExactly(new TradeRecord(10_200, 3),
                new TradeRecord(10_100, 2));
    }
}
