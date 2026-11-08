package kr.ject.naspi.websocket.infra;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

/**
 * 현재 서버에 접속한 클라이언트의 구독 현황. 구독자가 없는 토픽의 조회·발행을 건너뛰는 데 쓴다.
 *
 * <p>모든 연결은 로그인 회원이므로 {@link SimpUserRegistry}에 모든 구독이 기록된다.
 */
@Component
@RequiredArgsConstructor
public class TopicSubscriptions {

    private final SimpUserRegistry userRegistry;

    /**
     * 접두사로 시작하는 토픽에 구독자가 있는지 확인한다.
     *
     * @param destinationPrefix 토픽 접두사
     * @return 구독자가 한 명 이상이면 true
     */
    public boolean hasSubscribers(String destinationPrefix) {
        return !userRegistry.findSubscriptions(
                subscription -> subscription.getDestination().startsWith(destinationPrefix))
                .isEmpty();
    }

    /**
     * 접두사로 시작하는, 구독자가 있는 토픽 목록을 반환한다.
     *
     * @param destinationPrefix 토픽 접두사
     * @return 구독 중인 토픽
     */
    public Set<String> subscribedDestinations(String destinationPrefix) {
        return userRegistry.findSubscriptions(
                        subscription -> subscription.getDestination().startsWith(destinationPrefix))
                .stream()
                .map(SimpSubscription::getDestination)
                .collect(Collectors.toUnmodifiableSet());
    }
}
