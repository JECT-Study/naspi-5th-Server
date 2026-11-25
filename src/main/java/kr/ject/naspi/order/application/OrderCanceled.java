package kr.ject.naspi.order.application;

import kr.ject.naspi.order.domain.CancelReason;

/**
 * 미체결 주문 종료 이벤트. 본인 취소, 거래일 만료, 리그 종료·회원 탈퇴 일괄 취소로 미체결 잔여 수량의 예약을
 * 푼 뒤 주문마다 발행한다.
 *
 * <p>websocket이 커밋 이후 주문한 플레이어의 자산(예약 자산, 대기 주문)을 발행한다.
 *
 * @param leagueId 리그 ID
 * @param stockId 종목 ID
 * @param orderId 주문 ID
 * @param playerId 주문한 플레이어 ID
 * @param reason 종료 사유. 거래일 만료는 {@link CancelReason#TRADING_DAY_EXPIRED}
 */
public record OrderCanceled(long leagueId, long stockId, long orderId, long playerId,
        CancelReason reason) {
}
