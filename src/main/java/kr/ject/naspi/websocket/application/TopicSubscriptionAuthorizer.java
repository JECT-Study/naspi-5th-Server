package kr.ject.naspi.websocket.application;

import kr.ject.naspi.common.security.LoginMember;

/**
 * 토픽 구독 권한 판단. 토픽 구현마다 빈으로 등록한다 (예: 리그 참여자만 리그 토픽 구독).
 *
 * <p>구독 요청 토픽을 담당하는 Authorizer가 하나도 없으면 구독을 거부한다.
 */
public interface TopicSubscriptionAuthorizer {

    /**
     * 이 Authorizer가 판단할 토픽인지 확인한다.
     *
     * @param destination 구독 요청 토픽
     * @return 담당하면 true
     */
    boolean supports(String destination);

    /**
     * 회원이 토픽을 구독할 수 있는지 판단한다.
     *
     * @param member 로그인 회원
     * @param destination 구독 요청 토픽
     * @return 구독할 수 있으면 true
     */
    boolean canSubscribe(LoginMember member, String destination);
}
