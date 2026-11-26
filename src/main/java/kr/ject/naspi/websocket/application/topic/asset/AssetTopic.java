package kr.ject.naspi.websocket.application.topic.asset;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.market.application.CurrentPricesChanged;
import kr.ject.naspi.order.application.OrderCanceled;
import kr.ject.naspi.order.application.OrderPlaced;
import kr.ject.naspi.order.application.TradeExecuted;
import kr.ject.naspi.websocket.api.response.asset.AssetResponse;
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
 * 내 자산 토픽: {@code /topic/leagues/{leagueId}/players/{playerId}/assets}.
 *
 * <p>값이 바뀔 수 있을 때 바뀐 대상에게만 발행한다 (docs/design/asset-realtime.md 2장).
 * <ul>
 *   <li>현재가 변경({@link CurrentPricesChanged}): 그 리그의 구독자 전원</li>
 *   <li>체결({@link TradeExecuted}): 매수자·매도자</li>
 *   <li>지정가 주문 접수({@link OrderPlaced}), 취소·만료·일괄 취소({@link OrderCanceled}): 주문한
 *       플레이어</li>
 * </ul>
 * 도메인 이벤트는 커밋 이후에 발행해 롤백된 변경은 보내지 않는다. 구독자가 없으면 조회하지 않고, 자산이 없는
 * 플레이어(리그 시작 전)는 보내지 않는다. 리그 단위로 예외를 원인별(DB 조회 실패, 자산 계산 불가, 그 밖의
 * 오류)로 잡아 기록만 하므로 한 리그의 실패가 다른 리그
 * 발행이나 원래 트랜잭션에 영향을 주지 않는다.
 *
 * <p>발행은 웹소켓 발행 스레드 풀에서 비동기로 한다. 이벤트를 낸 스레드(현재가 갱신, 주문 요청)를 막지 않고,
 * 커밋 직후 원래 트랜잭션의 DB 커넥션을 쥔 채 조회용 커넥션을 하나 더 잡지 않기 위해서다. 메시지 순서가
 * 바뀔 수 있으므로 클라이언트는 {@code asOf}로 늦게 온 이전 상태를 버린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetTopic {

    /** 구독 경로. {@code playerId}가 본인일 때만 구독. */
    static final Pattern DESTINATION =
            Pattern.compile("^/topic/leagues/(\\d+)/players/(\\d+)/assets$");

    /** 발행을 실행할 웹소켓 발행 스레드 풀 ({@code WebSocketConfig#topicTaskScheduler}). */
    static final String PUBLISH_EXECUTOR = "topicTaskScheduler";

    private final TopicSubscriptions subscriptions;
    private final AssetMessageQuery assetMessageQuery;
    private final TopicMessagePublisher publisher;

    /**
     * 현재가가 바뀐 리그의 구독자 전원에게 발행한다.
     *
     * @param event 현재가 변경 이벤트
     */
    @Async(PUBLISH_EXECUTOR)
    @EventListener
    public void onCurrentPricesChanged(CurrentPricesChanged event) {
        event.leagueIds().stream()
                .sorted()
                .forEach(leagueId -> publish("현재가 변경", leagueId, playerId -> true));
    }

    /**
     * 체결 커밋 후 매수자·매도자에게 발행한다.
     *
     * @param event 체결 이벤트
     */
    @Async(PUBLISH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTradeExecuted(TradeExecuted event) {
        publish("체결", event.leagueId(),
                List.of(event.buyerPlayerId(), event.sellerPlayerId()));
    }

    /**
     * 지정가 주문 접수 커밋 후 주문한 플레이어에게 발행한다.
     *
     * @param event 주문 접수 이벤트
     */
    @Async(PUBLISH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlaced event) {
        publish("주문 접수", event.leagueId(), List.of(event.playerId()));
    }

    /**
     * 주문 취소·만료 커밋 후 주문한 플레이어에게 발행한다.
     *
     * @param event 주문 종료 이벤트
     */
    @Async(PUBLISH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCanceled(OrderCanceled event) {
        publish("주문 종료(" + event.reason() + ")", event.leagueId(), List.of(event.playerId()));
    }

    private void publish(String trigger, long leagueId, Collection<Long> playerIds) {
        publish(trigger, leagueId, playerIds::contains);
    }

    /**
     * 구독 중인 대상 플레이어의 자산을 조회해 발행한다. 실패는 원인별로 기록만 하고 삼킨다.
     *
     * <p>클라이언트 연결 종료는 여기서 예외가 되지 않는다. 그 사이 끊긴 구독은 브로커가 건너뛰고, 세션 전송
     * 실패는 브로커가 비동기로 처리한다. 메시지 변환·브로커 전달 실패는 {@link TopicMessagePublisher}가
     * 처리한다.
     */
    private void publish(String trigger, long leagueId, Predicate<Long> target) {
        List<Long> playerIds = List.of();
        try {
            playerIds = subscribedPlayerIds(leagueId).stream()
                    .filter(target)
                    .sorted()
                    .toList();
            if (playerIds.isEmpty()) {
                return;
            }
            assetMessageQuery.getAssets(leagueId, playerIds).forEach(asset ->
                    publisher.publish(new TopicMessage(destination(leagueId, asset.playerId()),
                            AssetResponse.TYPE, asset)));
        } catch (DataAccessException | TransactionException e) {
            // 일시적인 DB 오류. 다음 변경 때 최신 상태가 다시 발행된다
            log.warn("내 자산 발행 실패 (DB 조회 실패): trigger={}, leagueId={}, playerIds={}",
                    trigger, leagueId, playerIds, e);
        } catch (NaspiRuntimeException | IllegalStateException e) {
            // 현재가 없음, 보유·주문 종목이 리그에 없음 등 데이터 불일치
            log.error("내 자산 발행 실패 (자산 계산 불가): trigger={}, leagueId={}, playerIds={}",
                    trigger, leagueId, playerIds, e);
        } catch (RuntimeException e) {
            log.error("내 자산 발행 실패 (예상하지 못한 오류): trigger={}, leagueId={}, playerIds={}",
                    trigger, leagueId, playerIds, e);
        }
    }

    private Set<Long> subscribedPlayerIds(long leagueId) {
        return subscriptions.subscribedDestinations(leaguePrefix(leagueId)).stream()
                .map(DESTINATION::matcher)
                .filter(Matcher::matches)
                .filter(matcher -> Long.parseLong(matcher.group(1)) == leagueId)
                .map(matcher -> Long.parseLong(matcher.group(2)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String leaguePrefix(long leagueId) {
        return "/topic/leagues/" + leagueId + "/players/";
    }

    static String destination(long leagueId, long playerId) {
        return leaguePrefix(leagueId) + playerId + "/assets";
    }
}
