package kr.ject.naspi.websocket.application.topic.asset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import kr.ject.naspi.order.application.OrderPlaced;
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
 * 도메인 이벤트에 의한 즉시 발행이 커밋 이후에만 일어나는지 확인한다. DB 없이 트랜잭션 동기화만 하는 트랜잭션
 * 관리자를 쓴다.
 */
@SpringJUnitConfig(AssetTopicTransactionTest.Config.class)
class AssetTopicTransactionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 25, 21, 30, 1);

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        AssetTopic assetTopic(TopicSubscriptions subscriptions, AssetMessageQuery query,
                TopicMessagePublisher publisher) {
            return new AssetTopic(subscriptions, query, publisher);
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
    private AssetMessageQuery query;

    @MockitoBean
    private TopicMessagePublisher publisher;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        given(subscriptions.subscribedDestinations("/topic/leagues/1/players/"))
                .willReturn(Set.of("/topic/leagues/1/players/7/assets",
                        "/topic/leagues/1/players/8/assets"));
        given(query.getAssets(1L, List.of(7L, 8L))).willReturn(
                List.of(AssetTopicTest.asset(7L), AssetTopicTest.asset(8L)));
        given(query.getAssets(1L, List.of(7L))).willReturn(List.of(AssetTopicTest.asset(7L)));
    }

    @Test
    @DisplayName("체결이 커밋되면 매수자·매도자에게 즉시 발행한다")
    void publishAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(trade());
            verify(publisher, never()).publish(any());
        });

        verify(publisher).publish(AssetTopicTest.message(1L, 7L));
        verify(publisher).publish(AssetTopicTest.message(1L, 8L));
    }

    @Test
    @DisplayName("체결·주문 접수가 롤백되면 발행하지 않는다")
    void noPublishOnRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(trade());
            eventPublisher.publishEvent(new OrderPlaced(1L, 100L, 11L, 7L));
            status.setRollbackOnly();
        });

        verify(query, never()).getAssets(anyLong(), anyCollection());
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("발행에 실패해도 원래 트랜잭션은 커밋된다")
    void publishFailureDoesNotAffectTransaction() {
        given(query.getAssets(1L, List.of(7L))).willThrow(new IllegalStateException("조회 실패"));

        assertThatCode(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> eventPublisher.publishEvent(new OrderPlaced(1L, 100L, 11L, 7L))))
                .doesNotThrowAnyException();
    }

    private static TradeExecuted trade() {
        return new TradeExecuted(1L, 100L, 11L, 12L, 7L, 8L, 10_500, 3, NOW, false);
    }
}
