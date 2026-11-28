package kr.ject.naspi.websocket.application.topic.alert;

import java.util.List;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.application.TradingDayChanged;
import kr.ject.naspi.order.application.TradeExecuted;
import kr.ject.naspi.websocket.api.response.alert.AlertResponse;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 토픽: {@code /topic/leagues/{leagueId}/players/{playerId}/alerts}.
 *
 * <p>사용자가 요청하지 않았는데 주문 상태가 바뀐 경우 해당 플레이어에게 알린다 (docs/policies/v1/domain/order.md
 * "주문 알림").
 * <ul>
 *   <li>체결({@link TradeExecuted}): 대기 주문 주인. 체결 1건이 체결된 대기 주문 1건이므로 체결마다 한 번</li>
 *   <li>거래일 전환({@link TradingDayChanged}): 그 리그의 구독자 전원. 주문이 만료된 플레이어와 아닌 플레이어의
 *       알림 종류가 다르다</li>
 * </ul>
 * 도메인 이벤트는 커밋 이후에 발행해 롤백된 변경은 보내지 않는다. 구독 중인 플레이어에게만 보내고 보관하지 않으며,
 * 구독자가 없으면 조회하지 않는다. 예외는 원인별(DB 조회 실패, 데이터 불일치, 그 밖의 오류)로 잡아 기록만 하므로
 * 원래 트랜잭션에 영향을 주지 않는다.
 *
 * <p>발행은 웹소켓 발행 스레드 풀에서 비동기로 한다. 이벤트를 낸 스레드(주문 요청, 거래일 전환)를 막지 않고, 커밋
 * 직후 원래 트랜잭션의 DB 커넥션을 쥔 채 조회용 커넥션을 하나 더 잡지 않기 위해서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertTopic {

    /** 구독 경로. {@code playerId}가 본인일 때만 구독. */
    static final Pattern DESTINATION =
            Pattern.compile("^/topic/leagues/(\\d+)/players/(\\d+)/alerts$");

    /** 발행을 실행할 웹소켓 발행 스레드 풀 ({@code WebSocketConfig#topicTaskScheduler}). */
    static final String PUBLISH_EXECUTOR = "topicTaskScheduler";

    private final TopicSubscriptions subscriptions;
    private final AlertMessageQuery alertMessageQuery;
    private final TopicMessagePublisher publisher;

    /**
     * 체결 커밋 후 대기 주문 주인에게 주문 체결 알림을 보낸다. 신규 주문자는 주문 응답으로 결과를 받으므로 보내지
     * 않는다.
     *
     * @param event 체결 이벤트
     */
    @Async(PUBLISH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTradeExecuted(TradeExecuted event) {
        long restingPlayerId = event.restingPlayerId();
        publish("체결", event.leagueId(), playerId -> playerId == restingPlayerId,
                playerId -> alertMessageQuery.tradeExecuted(event));
    }

    /**
     * 거래일 전환 커밋 후 그 리그의 구독자 전원에게 거래일 갱신 알림을 보낸다.
     *
     * @param event 거래일 전환 이벤트
     */
    @Async(PUBLISH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTradingDayChanged(TradingDayChanged event) {
        publish("거래일 전환", event.leagueId(), playerId -> true,
                playerId -> alertMessageQuery.tradingDayChanged(event, playerId));
    }

    /**
     * 구독 중인 대상 플레이어에게 알림을 만들어 발행한다. 실패는 원인별로 기록만 하고 삼킨다.
     *
     * <p>클라이언트 연결 종료는 여기서 예외가 되지 않는다. 그 사이 끊긴 구독은 브로커가 건너뛰고, 세션 전송
     * 실패는 브로커가 비동기로 처리한다. 메시지 변환·브로커 전달 실패는 {@link TopicMessagePublisher}가
     * 처리한다.
     */
    private void publish(String trigger, long leagueId, Predicate<Long> target,
            LongFunction<AlertResponse> message) {
        List<Long> playerIds = List.of();
        try {
            playerIds = subscribedPlayerIds(leagueId).stream()
                    .filter(target)
                    .sorted()
                    .toList();
            for (long playerId : playerIds) {
                publisher.publish(new TopicMessage(destination(leagueId, playerId),
                        AlertResponse.TYPE, message.apply(playerId)));
            }
        } catch (DataAccessException | TransactionException e) {
            log.warn("알림 발행 실패 (DB 조회 실패): trigger={}, leagueId={}, playerIds={}",
                    trigger, leagueId, playerIds, e);
        } catch (NaspiRuntimeException | IllegalStateException e) {
            // 체결 종목이 리그에 없음 등 데이터 불일치
            log.error("알림 발행 실패 (알림 생성 불가): trigger={}, leagueId={}, playerIds={}",
                    trigger, leagueId, playerIds, e);
        } catch (RuntimeException e) {
            log.error("알림 발행 실패 (예상하지 못한 오류): trigger={}, leagueId={}, playerIds={}",
                    trigger, leagueId, playerIds, e);
        }
    }

    private List<Long> subscribedPlayerIds(long leagueId) {
        return subscriptions.subscribedDestinations(leaguePrefix(leagueId)).stream()
                .map(DESTINATION::matcher)
                .filter(Matcher::matches)
                .filter(matcher -> Long.parseLong(matcher.group(1)) == leagueId)
                .map(matcher -> Long.parseLong(matcher.group(2)))
                .distinct()
                .toList();
    }

    private static String leaguePrefix(long leagueId) {
        return "/topic/leagues/" + leagueId + "/players/";
    }

    static String destination(long leagueId, long playerId) {
        return leaguePrefix(leagueId) + playerId + "/alerts";
    }
}
