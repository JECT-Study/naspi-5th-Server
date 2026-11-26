package kr.ject.naspi.websocket.application.topic.stock;

import java.util.regex.Pattern;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.market.application.CurrentPricesChanged;
import kr.ject.naspi.order.application.TradeExecuted;
import kr.ject.naspi.websocket.api.response.stock.StockResponse;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 종목 정보 토픽: {@code /topic/leagues/{leagueId}/stocks}.
 *
 * <p>리그 전체 종목을 한 메시지로 발행한다. 발행 시점은 두 가지다.
 * <ul>
 *   <li>현재가 변경({@link CurrentPricesChanged}): 트랜잭션 밖에서 발행되므로 바로 받는다</li>
 *   <li>체결({@link TradeExecuted}): 커밋 이후. 현재가가 같은 체결의 거래량을 반영한다</li>
 * </ul>
 * 구독자가 없는 리그는 조회하지 않는다. 리그 단위로 예외를 잡아 기록만 하므로 한 리그의 실패가 다른 리그
 * 발행이나 원래 트랜잭션에 영향을 주지 않는다. 거래일 전환과 종목 소개 수정으로는 발행하지 않는다.
 *
 * <p>발행은 웹소켓 발행 스레드 풀에서 비동기로 한다. 메시지 순서가 바뀔 수 있으므로 클라이언트는
 * {@code asOf}로 늦게 온 이전 상태를 버린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockTopic {

    /** 구독 경로. 리그에 입장한 회원만 구독. */
    static final Pattern DESTINATION = Pattern.compile("^/topic/leagues/(\\d+)/stocks$");

    /**
     * 발행을 실행할 웹소켓 발행 스레드 풀 ({@code WebSocketConfig#topicTaskScheduler}).
     * {@code AssetTopic.PUBLISH_EXECUTOR}와 같은 값.
     */
    static final String PUBLISH_EXECUTOR = "topicTaskScheduler";

    private final TopicSubscriptions subscriptions;
    private final StockMessageQuery stockMessageQuery;
    private final TopicMessagePublisher publisher;

    /**
     * 현재가가 바뀐 리그의 구독자에게 발행한다.
     *
     * @param event 현재가 변경 이벤트
     */
    @Async(PUBLISH_EXECUTOR)
    @EventListener
    public void onCurrentPricesChanged(CurrentPricesChanged event) {
        event.leagueIds().stream()
                .sorted()
                .forEach(leagueId -> publish("현재가 변경", leagueId));
    }

    /**
     * 체결 커밋 후 그 리그의 구독자에게 발행한다.
     *
     * @param event 체결 이벤트
     */
    @Async(PUBLISH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTradeExecuted(TradeExecuted event) {
        publish("체결", event.leagueId());
    }

    /**
     * 종목 목록 경로를 구독 중이면 조회해 발행한다. 실패는 원인별로 기록만 하고 삼킨다.
     */
    private void publish(String trigger, long leagueId) {
        String destination = destination(leagueId);
        try {
            if (!subscriptions.subscribedDestinations(destination).contains(destination)) {
                return;
            }
            StockResponse response = stockMessageQuery.getStocks(leagueId);
            publisher.publish(new TopicMessage(destination, StockResponse.TYPE, response));
        } catch (DataAccessException | TransactionException e) {
            // 일시적인 DB 오류. 다음 변경 때 최신 상태가 다시 발행된다
            log.warn("종목 정보 발행 실패 (DB 조회 실패): trigger={}, leagueId={}",
                    trigger, leagueId, e);
        } catch (NaspiRuntimeException | IllegalStateException e) {
            // 데이터 불일치
            log.error("종목 정보 발행 실패 (조회 불가): trigger={}, leagueId={}",
                    trigger, leagueId, e);
        } catch (RuntimeException e) {
            log.error("종목 정보 발행 실패 (예상하지 못한 오류): trigger={}, leagueId={}",
                    trigger, leagueId, e);
        }
    }

    static String destination(long leagueId) {
        return "/topic/leagues/" + leagueId + "/stocks";
    }
}
