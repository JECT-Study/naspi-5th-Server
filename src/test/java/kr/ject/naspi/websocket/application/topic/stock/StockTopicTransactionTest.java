package kr.ject.naspi.websocket.application.topic.stock;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Set;
import kr.ject.naspi.market.application.CurrentPricesChanged;
import kr.ject.naspi.order.application.TradeExecuted;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
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
 * 체결 이벤트에 의한 발행이 커밋 이후에만 일어나고, 현재가 변경은 트랜잭션 밖에서도 받는지 확인한다. DB 없이
 * 트랜잭션 동기화만 하는 트랜잭션 관리자를 쓴다.
 */
@SpringJUnitConfig(StockTopicTransactionTest.Config.class)
class StockTopicTransactionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 21, 30, 1);

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        StockTopic stockTopic(TopicSubscriptions subscriptions, StockMessageQuery query,
                TopicMessagePublisher publisher) {
            return new StockTopic(subscriptions, query, publisher);
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
    private StockMessageQuery query;

    @MockitoBean
    private TopicMessagePublisher publisher;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        given(subscriptions.subscribedDestinations("/topic/leagues/1/stocks"))
                .willReturn(Set.of("/topic/leagues/1/stocks"));
        given(query.getStocks(1L)).willReturn(StockTopicTest.response());
    }

    @Test
    @DisplayName("체결이 커밋된 뒤에 해당 리그 종목 정보 구독자에게 발행한다")
    void publishAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(trade());
            verify(publisher, never()).publish(any());
        });

        verify(publisher).publish(StockTopicTest.message(1L));
    }

    @Test
    @DisplayName("체결이 롤백되면 종목 정보를 조회·발행하지 않는다")
    void noPublishOnRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(trade());
            status.setRollbackOnly();
        });

        verify(query, never()).getStocks(anyLong());
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("트랜잭션 밖에서 발행된 현재가 변경도 받아 발행한다")
    void currentPricesChangedOutsideTransaction() {
        eventPublisher.publishEvent(new CurrentPricesChanged(Set.of(1L)));

        verify(publisher).publish(StockTopicTest.message(1L));
    }

    @Test
    @DisplayName("종목 정보 발행에 실패해도 체결 트랜잭션은 커밋된다")
    void publishFailureDoesNotAffectTransaction() {
        given(query.getStocks(1L)).willThrow(new IllegalStateException("조회 실패"));

        assertThatCode(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> eventPublisher.publishEvent(trade()))).doesNotThrowAnyException();
    }

    private static TradeExecuted trade() {
        return new TradeExecuted(1L, 100L, 11L, 12L, 7L, 8L, 10_500, 3, NOW, false);
    }
}
