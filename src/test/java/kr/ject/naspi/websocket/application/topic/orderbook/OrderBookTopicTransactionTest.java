package kr.ject.naspi.websocket.application.topic.orderbook;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import kr.ject.naspi.order.application.OrderBookQueryService;
import kr.ject.naspi.order.application.OrderBookView;
import kr.ject.naspi.order.application.OrderCanceled;
import kr.ject.naspi.order.application.OrderPlaced;
import kr.ject.naspi.order.application.TradeExecuted;
import kr.ject.naspi.order.domain.CancelReason;
import kr.ject.naspi.websocket.api.response.orderbook.OrderBookResponse;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 도메인 이벤트에 의한 호가 발행이 커밋 이후에만 일어나는지 확인한다. DB 없이 트랜잭션 동기화만 하는
 * 트랜잭션 관리자를 쓴다.
 */
@SpringJUnitConfig(OrderBookTopicTransactionTest.Config.class)
class OrderBookTopicTransactionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 10, 0, 0);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final Clock CLOCK = Clock.fixed(NOW.atZone(KST).toInstant(), KST);

    private static final TopicMessage MESSAGE = new TopicMessage(
            "/topic/leagues/1/stocks/3/orderbook", OrderBookResponse.TYPE,
            new OrderBookResponse(10_000, List.of(), List.of(), NOW));

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        OrderBookTopic orderBookTopic(TopicSubscriptions subscriptions,
                OrderBookQueryService query, TopicMessagePublisher publisher) {
            return new OrderBookTopic(subscriptions, query, publisher, CLOCK);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new SynchronizationOnlyTransactionManager();
        }
    }

    /** 실제 자원 없이 트랜잭션 동기화(커밋·롤백 콜백)만 하는 트랜잭션 관리자. */
    static class SynchronizationOnlyTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    @MockitoBean
    private TopicSubscriptions subscriptions;

    @MockitoBean
    private OrderBookQueryService query;

    @MockitoBean
    private TopicMessagePublisher publisher;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        given(subscriptions.subscribedDestinations("/topic/leagues/1/stocks/"))
                .willReturn(Set.of("/topic/leagues/1/stocks/3/orderbook"));
        given(query.getOrderBook(1L, 3L))
                .willReturn(new OrderBookView(10_000, List.of(), List.of()));
    }

    @Test
    @DisplayName("지정가 주문 접수·취소·체결이 커밋되면 해당 종목 호가를 발행한다")
    void publishAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new OrderPlaced(1L, 3L, 11L, 7L));
            eventPublisher.publishEvent(
                    new OrderCanceled(1L, 3L, 11L, 7L, CancelReason.USER_REQUESTED));
            eventPublisher.publishEvent(trade());
            verify(publisher, never()).publish(any());
        });

        verify(publisher, org.mockito.Mockito.times(3)).publish(MESSAGE);
    }

    @Test
    @DisplayName("주문 접수·체결이 롤백되면 호가를 발행하지 않는다")
    void noPublishOnRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(trade());
            eventPublisher.publishEvent(new OrderPlaced(1L, 3L, 11L, 7L));
            status.setRollbackOnly();
        });

        verify(query, never()).getOrderBook(anyLong(), anyLong());
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("호가 발행에 실패해도 원래 트랜잭션은 커밋된다")
    void publishFailureDoesNotAffectTransaction() {
        given(query.getOrderBook(1L, 3L)).willThrow(new IllegalStateException("조회 실패"));

        assertThatCode(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> eventPublisher.publishEvent(new OrderPlaced(1L, 3L, 11L, 7L))))
                .doesNotThrowAnyException();
    }

    private static TradeExecuted trade() {
        return new TradeExecuted(1L, 3L, 11L, 12L, 7L, 8L, 10_500, 3, NOW);
    }
}
