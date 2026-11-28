package kr.ject.naspi.websocket.application.topic.league;

import java.util.regex.Pattern;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.market.application.StockListed;
import kr.ject.naspi.websocket.api.response.league.LeagueDetailResponse;
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
 * 리그 상세 토픽: {@code /topic/leagues/{leagueId}/detail}.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "대기실 실시간 반영"): 대기실의 사용자는 다른 사용자의 상장 완료를
 * 새로고침 없이 확인한다. 종목 상장({@link StockListed}) 커밋 후 그 리그의 상세를 다시 조회해 발행한다. 상장
 * 이벤트는 대기자 → 참여자 전환보다 먼저 발행되므로 커밋 이후에 조회해야 전환이 반영된다. 입장·퇴장·추방·리그
 * 이름 수정·종목 수정으로는 발행하지 않는다.
 *
 * <p>구독자가 없는 리그는 조회하지 않는다 (리그 생성 중 방장 종목 상장 포함). 예외는 잡아 기록만 하므로 원래
 * 트랜잭션에 영향을 주지 않는다. 발행은 웹소켓 발행 스레드 풀에서 비동기로 한다. 메시지 순서가 바뀔 수 있으므로
 * 클라이언트는 {@code asOf}로 늦게 온 이전 상태를 버린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeagueDetailTopic {

    /** 구독 경로. 리그에 입장한 회원만 구독. */
    static final Pattern DESTINATION = Pattern.compile("^/topic/leagues/(\\d+)/detail$");

    /**
     * 발행을 실행할 웹소켓 발행 스레드 풀 ({@code WebSocketConfig#topicTaskScheduler}).
     * {@code AssetTopic.PUBLISH_EXECUTOR}와 같은 값.
     */
    static final String PUBLISH_EXECUTOR = "topicTaskScheduler";

    private final TopicSubscriptions subscriptions;
    private final LeagueDetailMessageQuery leagueDetailMessageQuery;
    private final TopicMessagePublisher publisher;

    /**
     * 종목 상장 커밋 후 그 리그의 구독자에게 발행한다.
     *
     * @param event 종목 상장 이벤트
     */
    @Async(PUBLISH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockListed(StockListed event) {
        publish("상장", event.leagueId());
    }

    /**
     * 리그 상세 경로를 구독 중이면 조회해 발행한다. 실패는 원인별로 기록만 하고 삼킨다.
     */
    private void publish(String trigger, long leagueId) {
        String destination = destination(leagueId);
        try {
            if (!subscriptions.subscribedDestinations(destination).contains(destination)) {
                return;
            }
            LeagueDetailResponse response = leagueDetailMessageQuery.getDetail(leagueId);
            publisher.publish(new TopicMessage(destination, LeagueDetailResponse.TYPE, response));
        } catch (DataAccessException | TransactionException e) {
            // 일시적인 DB 오류. 클라이언트는 다시 불러오면 최신 상태를 본다
            log.warn("리그 상세 발행 실패 (DB 조회 실패): trigger={}, leagueId={}",
                    trigger, leagueId, e);
        } catch (NaspiRuntimeException | IllegalStateException e) {
            // 커밋 직후 리그가 삭제되는 등 데이터 불일치
            log.error("리그 상세 발행 실패 (조회 불가): trigger={}, leagueId={}",
                    trigger, leagueId, e);
        } catch (RuntimeException e) {
            log.error("리그 상세 발행 실패 (예상하지 못한 오류): trigger={}, leagueId={}",
                    trigger, leagueId, e);
        }
    }

    static String destination(long leagueId) {
        return "/topic/leagues/" + leagueId + "/detail";
    }
}
