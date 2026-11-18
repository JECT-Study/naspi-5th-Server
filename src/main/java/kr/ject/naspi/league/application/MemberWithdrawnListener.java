package kr.ject.naspi.league.application;

import kr.ject.naspi.member.application.MemberWithdrawn;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 시 소속 대기실에서 퇴장시킨다.
 *
 * <p>탈퇴와 같은 트랜잭션에서 동기로 처리해, 퇴장이 실패하면 탈퇴도 롤백된다
 * (docs/policies/v1/domain/member.md "회원 탈퇴" - 대기실 처리).
 */
@Component
@RequiredArgsConstructor
class MemberWithdrawnListener {

    private final LeagueLeaveUseCase leagueLeaveUseCase;

    @EventListener
    void onMemberWithdrawn(MemberWithdrawn event) {
        leagueLeaveUseCase.leaveAllWaitingRooms(event.memberId());
    }
}
