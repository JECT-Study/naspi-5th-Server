package kr.ject.naspi.websocket.application.topic.league;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Set;
import kr.ject.naspi.market.application.StockListed;
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
 * 상장 이벤트에 의한 발행이 커밋 이후에만 일어나는지 확인한다. DB 없이 트랜잭션 동기화만 하는 트랜잭션 관리자를
 * 쓴다.
 */
@SpringJUnitConfig(LeagueDetailTopicTransactionTest.Config.class)
class LeagueDetailTopicTransactionTest {

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        LeagueDetailTopic leagueDetailTopic(TopicSubscriptions subscriptions,
                LeagueDetailMessageQuery query, TopicMessagePublisher publisher) {
            return new LeagueDetailTopic(subscriptions, query, publisher);
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
    private LeagueDetailMessageQuery query;

    @MockitoBean
    private TopicMessagePublisher publisher;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        given(subscriptions.subscribedDestinations("/topic/leagues/1/detail"))
                .willReturn(Set.of("/topic/leagues/1/detail"));
        given(query.getDetail(1L)).willReturn(LeagueDetailTopicTest.response());
    }

    @Test
    @DisplayName("상장이 커밋된 뒤에 그 리그 상세 구독자에게 발행해 대기자 → 참여자 전환을 반영한다")
    void publishAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new StockListed(100L, 1L, 31L));
            verify(query, never()).getDetail(anyLong());
        });

        verify(publisher).publish(LeagueDetailTopicTest.message(1L));
    }

    @Test
    @DisplayName("상장이 롤백되면 리그 상세를 조회·발행하지 않는다")
    void noPublishOnRollback() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new StockListed(100L, 1L, 31L));
            status.setRollbackOnly();
        });

        verify(query, never()).getDetail(anyLong());
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("리그 상세 발행에 실패해도 상장 트랜잭션은 커밋된다")
    void publishFailureDoesNotAffectTransaction() {
        given(query.getDetail(1L)).willThrow(new IllegalStateException("조회 실패"));

        assertThatCode(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> eventPublisher.publishEvent(new StockListed(100L, 1L, 31L))))
                .doesNotThrowAnyException();
    }
}
