package kr.ject.naspi.asset.application;

import kr.ject.naspi.asset.domain.AccountRepository;
import kr.ject.naspi.asset.domain.HoldingRepository;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.MemberPlayer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 시 자산 처리 (AST-07, docs/policies/v1/domain/member.md "회원 탈퇴").
 *
 * <p>회원 탈퇴 UseCase가 order의 미체결 주문 취소(예약 해제 포함) 뒤에 호출한다 (ORD-11). 예약이 남아 있으면
 * 폐기를 거절하므로 주문 취소보다 먼저 호출하면 안 된다.
 *
 * <p>현금만 폐기하고 보유 주식은 조회·변경하지 않는다. 보유 수량이 그대로 남으므로 탈퇴자의 보유분은 발행주식 총수
 * ({@code InitialAllocation.ISSUED_SHARES} = 리그 내 종목별 {@code Holding.quantity} 합)에 계속 포함되고,
 * 이후 거래는 {@link AssetReservationService}가 거절한다. 종료된 리그는 확정된 결과를 유지하므로 진행 중인 리그만
 * 대상이다.
 */
@Service
@RequiredArgsConstructor
public class AssetWithdrawalService {

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final LeagueQueryService leagueQueryService;

    /**
     * 참여자의 현금을 폐기하고 탈퇴 처리한다. 자산이 배분되지 않았으면(계좌 없음) 아무것도 하지 않는다.
     * 미체결 주문 취소와 예약 해제가 끝난 뒤 호출해야 한다.
     *
     * @param leagueId 리그 ID
     * @param playerId 탈퇴한 회원의 플레이어 ID
     * @throws NaspiRuntimeException 예약금이나 매도 예약 수량이 남아 있는 경우 (데이터 오류)
     */
    @Transactional
    public void discardCash(long leagueId, long playerId) {
        // 락 순서는 정산·예약과 같은 "계좌 → 보유 주식"이다. 계좌 락이 매도 예약과 폐기를 직렬화한다.
        accountRepository.findWithLockByLeagueIdAndPlayerId(leagueId, playerId)
                .ifPresent(account -> {
                    ensureNoReservedStock(leagueId, playerId);
                    account.discardForWithdrawal();
                });
    }

    /** 매도 예약 수량이 남은 채 탈퇴 처리되면 이후 정산이 막혀 예약이 영구히 묶이므로 거절한다. */
    private void ensureNoReservedStock(long leagueId, long playerId) {
        boolean reserved = holdingRepository.findAllByLeagueIdAndPlayerId(leagueId, playerId)
                .stream().anyMatch(holding -> holding.getReservedQuantity() > 0);
        if (reserved) {
            throw new NaspiRuntimeException("미체결 매도 주문에 묶인 주식이 남아 있어 폐기할 수 없습니다: leagueId="
                    + leagueId + ", playerId=" + playerId);
        }
    }

    /**
     * 회원이 참여 중인 진행 중 리그 전체에서 현금을 폐기한다. 회원이 삭제되기 전 탈퇴 트랜잭션 안에서 호출한다.
     *
     * @param memberId 탈퇴한 회원 ID
     * @throws NaspiRuntimeException 예약금이나 매도 예약 수량이 남아 있는 계좌가 있는 경우
     */
    @Transactional
    public void discardCashOfMember(long memberId) {
        for (MemberPlayer player : leagueQueryService.findStartedPlayersOfMember(memberId)) {
            discardCash(player.leagueId(), player.playerId());
        }
    }
}
