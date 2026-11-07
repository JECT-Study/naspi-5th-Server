package kr.ject.naspi.websocket.application;

import java.util.List;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;

/**
 * 주기적으로 발행하는 토픽. 빈으로 등록하면 {@link TopicScheduler}가 자동으로 예약한다.
 *
 * <p>주기는 {@code naspi.websocket.topics.<name>.interval}로 토픽마다 따로 설정한다.
 * 설정이 없으면 예약하지 않는다. 이전 실행이 끝난 뒤 주기만큼 기다렸다 다음 실행을 하므로
 * 같은 토픽이 겹쳐 실행되지 않는다.
 */
public interface PeriodicTopic {

    /**
     * 설정 키로 쓰는 토픽 이름. 토픽마다 달라야 한다 (예: {@code ranking}).
     *
     * @return 토픽 이름
     */
    String name();

    /**
     * 구독자 확인에 쓰는 토픽 접두사 (예: {@code /topic/leagues/}). 이 접두사로 시작하는 토픽의
     * 구독자가 없으면 {@link #produce()}를 호출하지 않는다.
     *
     * @return 토픽 접두사
     */
    String destinationPrefix();

    /**
     * 이번 주기에 발행할 메시지를 만든다. 구독자가 있는 토픽만 골라 만들려면
     * {@link TopicSubscriptions}를 주입받아 사용한다.
     *
     * @return 발행할 메시지. 없으면 빈 목록
     */
    List<TopicMessage> produce();
}
