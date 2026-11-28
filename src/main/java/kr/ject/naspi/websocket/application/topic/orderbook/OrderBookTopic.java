package kr.ject.naspi.websocket.application.topic.orderbook;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.market.application.CurrentPricesChanged;
import kr.ject.naspi.order.application.OrderBookQueryService;
import kr.ject.naspi.order.application.OrderBookView;
import kr.ject.naspi.order.application.OrderCanceled;
import kr.ject.naspi.order.application.OrderPlaced;
import kr.ject.naspi.order.application.TradeExecuted;
import kr.ject.naspi.websocket.api.response.orderbook.OrderBookResponse;
import kr.ject.naspi.websocket.api.response.orderbook.OrderBookResponse.Level;
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
 * 호가 토픽: {@code /topic/leagues/{leagueId}/stocks/{stockId}/orderbook}.
 *
 * <p>호가가 바뀔 수 있을 때 해당 종목의 구독 경로에만 발행한다.
 * <ul>
 *   <li>체결({@link TradeExecuted}), 지정가 주문 접수({@link OrderPlaced}), 취소·만료
 *       ({@link OrderCanceled}): 그 종목</li>
 *   <li>현재가 변경({@link CurrentPricesChanged}): 그 리그에서 구독 중인 종목 전체</li>
 * </ul>
 * 도메인 이벤트는 커밋 이후에 발행해 롤백된 변경은 보내지 않는다. 리그 종료 일괄 취소는 거래할 수 없는 리그라
 * 조회하지 않고 건너뛴다. 구독자가 없으면 조회하지 않고, 종목 단위로 예외를 원인별로 잡아 기록만 하므로 한
 * 종목의 실패가 다른 종목 발행이나 원래 트랜잭션에 영향을 주지 않는다.
 *
 * <p>발행은 웹소켓 발행 스레드 풀에서 비동기로 한다. 이벤트를 낸 스레드를 막지 않고, 커밋 직후 원래
 * 트랜잭션의 DB 커넥션을 쥔 채 조회용 커넥션을 하나 더 잡지 않기 위해서다. 같은 커밋에서 같은 종목이 여러 번
 * 발행될 수 있고 메시지 순서가 바뀔 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBookTopic {

    /** 구독 경로. 진행 중인 리그의 참여자만 구독. */
    static final Pattern DESTINATION =
            Pattern.compile("^/topic/leagues/(\\d+)/stocks/(\\d+)/orderbook$");

    /** 발행을 실행할 웹소켓 발행 스레드 풀 ({@code WebSocketConfig#topicTaskScheduler}). */
    static final String PUBLISH_EXECUTOR = "topicTaskScheduler";

    private final TopicSubscriptions subscriptions;
    private final OrderBookQueryService orderBookQueryService;
    private final TopicMessagePublisher publisher;
    private final Clock clock;

    /**
     * 체결 커밋 후 해당 종목 호가를 발행한다.
     *
     * @param event 체결 이벤트
     */
    @Async(PUBLISH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTradeExecuted(TradeExecuted event) {
        publish("체결", event.leagueId(), stockId -> stockId == event.stockId());
    }

    /**
     * 지정가 주문 접수 커밋 후 해당 종목 호가를 발행한다.
     *
     * @param event 주문 접수 이벤트
     */
    @Async(PUBLISH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlaced event) {
        publish("주문 접수", event.leagueId(), stockId -> stockId == event.stockId());
    }

    /**
     * 주문 취소·만료 커밋 후 해당 종목 호가를 발행한다. 리그 종료 일괄 취소는 건너뛴다.
     *
     * @param event 주문 종료 이벤트
     */
    @Async(PUBLISH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCanceled(OrderCanceled event) {
        if (event.leagueEnded()) {
            return;
        }
        publish("주문 종료(" + event.reason() + ")", event.leagueId(),
                stockId -> stockId == event.stockId());
    }

    /**
     * 현재가가 바뀐 리그의 구독 중인 종목 호가를 모두 다시 발행한다.
     *
     * @param event 현재가 변경 이벤트
     */
    @Async(PUBLISH_EXECUTOR)
    @EventListener
    public void onCurrentPricesChanged(CurrentPricesChanged event) {
        event.leagueIds().stream()
                .sorted()
                .forEach(leagueId -> publish("현재가 변경", leagueId, stockId -> true));
    }

    /**
     * 구독 중인 대상 종목의 호가를 조회해 발행한다. 실패는 원인별로 기록만 하고 삼킨다.
     *
     * <p>메시지 변환·브로커 전달 실패는 {@link TopicMessagePublisher}가 처리한다.
     */
    private void publish(String trigger, long leagueId, Predicate<Long> target) {
        List<Long> stockIds = subscribedStockIds(leagueId).stream()
                .filter(target)
                .sorted()
                .toList();
        for (long stockId : stockIds) {
            publishOne(trigger, leagueId, stockId);
        }
    }

    private void publishOne(String trigger, long leagueId, long stockId) {
        try {
            LocalDateTime asOf = LocalDateTime.now(clock);
            OrderBookView view = orderBookQueryService.getOrderBook(leagueId, stockId);
            publisher.publish(new TopicMessage(destination(leagueId, stockId),
                    OrderBookResponse.TYPE, toResponse(view, asOf)));
        } catch (NaspiBizException e) {
            if (isLeagueNotTradable(e)) {
                // 리그 종료 직후 등 예상되는 상황
                log.debug("호가 발행 건너뜀 (거래할 수 없는 리그): trigger={}, leagueId={}, stockId={}, "
                        + "code={}", trigger, leagueId, stockId, e.getErrorCode());
            } else {
                log.error("호가 발행 실패 (호가 계산 불가): trigger={}, leagueId={}, stockId={}",
                        trigger, leagueId, stockId, e);
            }
        } catch (DataAccessException | TransactionException e) {
            // 일시적인 DB 오류. 다음 변경 때 최신 상태가 다시 발행된다
            log.warn("호가 발행 실패 (DB 조회 실패): trigger={}, leagueId={}, stockId={}",
                    trigger, leagueId, stockId, e);
        } catch (NaspiRuntimeException | IllegalStateException e) {
            // 리그에 없는 종목, 현재가 없음 등 데이터 불일치
            log.error("호가 발행 실패 (호가 계산 불가): trigger={}, leagueId={}, stockId={}",
                    trigger, leagueId, stockId, e);
        } catch (RuntimeException e) {
            log.error("호가 발행 실패 (예상하지 못한 오류): trigger={}, leagueId={}, stockId={}",
                    trigger, leagueId, stockId, e);
        }
    }

    private static boolean isLeagueNotTradable(NaspiBizException e) {
        ErrorCode code = e.getErrorCode();
        return code == ErrorCode.LEAGUE_ALREADY_ENDED || code == ErrorCode.LEAGUE_NOT_TRADABLE
                || code == ErrorCode.LEAGUE_NOT_FOUND;
    }

    private List<Long> subscribedStockIds(long leagueId) {
        return subscriptions.subscribedDestinations(leaguePrefix(leagueId)).stream()
                .map(DESTINATION::matcher)
                .filter(Matcher::matches)
                .filter(matcher -> Long.parseLong(matcher.group(1)) == leagueId)
                .map(matcher -> Long.parseLong(matcher.group(2)))
                .distinct()
                .toList();
    }

    /** 계산 시각은 조회 직전 시각이다. */
    private static OrderBookResponse toResponse(OrderBookView view, LocalDateTime asOf) {
        return new OrderBookResponse(view.currentPrice(), toLevels(view.asks()),
                toLevels(view.bids()), asOf);
    }

    private static List<Level> toLevels(List<OrderBookView.PriceLevel> levels) {
        return levels.stream()
                .map(level -> new Level(level.price(), level.quantity()))
                .toList();
    }

    private static String leaguePrefix(long leagueId) {
        return "/topic/leagues/" + leagueId + "/stocks/";
    }

    static String destination(long leagueId, long stockId) {
        return leaguePrefix(leagueId) + stockId + "/orderbook";
    }
}
