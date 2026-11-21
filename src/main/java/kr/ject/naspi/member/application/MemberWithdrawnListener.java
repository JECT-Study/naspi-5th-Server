package kr.ject.naspi.member.application;

import kr.ject.naspi.member.domain.KakaoAccountUnlinker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원이 탈퇴하면 카카오 계정의 연결을 끊는다.
 *
 * <p>정책 (docs/policies/v1/domain/member.md "회원 탈퇴"): 연결 끊기에 실패해도 탈퇴는 처리한다.
 * 탈퇴가 커밋된 뒤 별도 스레드에서 실행하므로 탈퇴 응답을 늦추지 않고, 탈퇴가 롤백되면 연결을 끊지 않는다.
 * 실패는 로그로만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MemberWithdrawnListener {

    private final KakaoAccountUnlinker kakaoAccountUnlinker;

    /**
     * 탈퇴한 회원의 카카오 연결을 끊는다.
     *
     * @param event 회원 탈퇴 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberWithdrawn(MemberWithdrawn event) {
        try {
            kakaoAccountUnlinker.unlink(event.kakaoId());
        } catch (RuntimeException e) {
            log.warn("카카오 연결 끊기 실패 (탈퇴는 처리됨): memberId={}, kakaoId={}",
                    event.memberId(), event.kakaoId(), e);
        }
    }
}
