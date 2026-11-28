package kr.ject.naspi.websocket.application.topic.alert;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Set;
import kr.ject.naspi.league.application.TradingDayChanged;
import kr.ject.naspi.websocket.api.response.alert.AlertResponse;
import kr.ject.naspi.websocket.api.response.alert.AlertType;
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
 * 도메인 이벤트에 의한 알림 발행이 커밋 이후에만 일어나는지 확인한다. DB 없이 트랜잭션 동기화만 하는 트랜잭션
 * 관리자를 쓴다.
 */
@SpringJUnitConfig(AlertTopicTransactionTest.Config.class)
class AlertTopicTransactionTest {

    private static final AlertResponse TRADE_ALERT = new AlertResponse(
            AlertType.TRADE_EXECUTED, "진영전자 매도 주문이 체결됐어요.", "/leagues/1/stocks/100");

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        AlertTopic alertTopic(TopicSubscriptions subscriptions, AlertMessageQuery query,
                TopicMessagePublisher publisher) {
            return new AlertTopic(subscriptions, query, publisher);
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
    private AlertMessageQuery query;

    @MockitoBean
    private TopicMessagePublisher publisher;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        given(subscriptions.subscribedDestinations("/topic/leagues/1/players/"))
                .willReturn(Set.of("/topic/leagues/1/players/8/alerts"));
        given(query.tradeExecuted(any())).willReturn(TRADE_ALERT);
    }

    @Test
    @DisplayName("체결이 커밋되면 대기 주문 주인에게 즉시 알림을 보낸다")
    void publishAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(AlertTopicTest.trade(false));
            verify(publisher, never()).publish(any());
        });

        verify(publisher).publish(AlertTopicTest.message(8L, TRADE_ALERT));
    }

    @Test
    @DisplayName("체결·거래일 전환이 롤백되면 알림을 보내지 않는다")
    void noPublishOnRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(AlertTopicTest.trade(false));
            eventPublisher.publishEvent(new TradingDayChanged(1L, 2, Set.of()));
            status.setRollbackOnly();
        });

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("알림 발행에 실패해도 원래 트랜잭션은 커밋된다")
    void publishFailureDoesNotAffectTransaction() {
        given(query.tradeExecuted(any())).willThrow(new IllegalStateException("조회 실패"));

        assertThatCode(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> eventPublisher.publishEvent(AlertTopicTest.trade(false))))
                .doesNotThrowAnyException();
    }
}
