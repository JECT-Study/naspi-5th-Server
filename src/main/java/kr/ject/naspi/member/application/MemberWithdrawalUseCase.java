package kr.ject.naspi.member.application;

import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueLeaveUseCase;
import kr.ject.naspi.league.application.LeagueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴.
 *
 * <p>정책 (docs/policies/v1/domain/member.md "회원 탈퇴"): 즉시 탈퇴하고 회원 정보를 hard delete 한다.
 * 대기실에서는 퇴장 처리하고, 시작된 리그에서는 플레이어를 남긴 채 탈퇴 일시를 기록한다.
 *
 * <p>탈퇴 후속 처리는 하위 모듈이 맡으므로 UseCase로 만들어 각 모듈의 application을 직접 호출한다.
 * 한 트랜잭션에서 처리해 후속 처리가 실패하면 탈퇴도 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalUseCase {

    private final MemberService memberService;
    private final LeagueLeaveUseCase leagueLeaveUseCase;
    private final LeagueService leagueService;

    /**
     * 회원을 즉시 탈퇴시킨다.
     *
     * @param memberId 회원 ID
     * @throws NaspiBizException 회원이 없는 경우
     */
    @Transactional
    public void withdraw(long memberId) {
        // 이미 탈퇴한 회원이면 후속 처리 전에 거절한다 (남아 있는 플레이어에 탈퇴 일시를 다시 기록하지 않는다).
        memberService.getName(memberId);
        // TODO(ORD-11, ND-44): 참여 중인 모든 리그의 미체결 주문을 취소(예약 해제 포함)한 뒤 현금을 폐기한다.
        //  order에 탈퇴 회원 주문 일괄 취소를 만들고 (리그 거래 가능 확인 없이 cancelOrder의 예약 해제를 재사용),
        //  이어서 AssetWithdrawalService#discardCashOfMember를 호출한다. 예약이 남아 있으면 폐기가 거절되므로
        //  주문 취소가 먼저다.
        leagueLeaveUseCase.leaveAllWaitingRooms(memberId);
        leagueService.recordWithdrawal(memberId);
        memberService.withdraw(memberId);
    }
}
