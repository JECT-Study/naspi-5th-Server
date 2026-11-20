package kr.ject.naspi.order.application;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.order.domain.OrderSide;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 로그인 회원의 지정가 주문 접수·취소 요청을 받아 {@link LimitOrderService}에 넘긴다 (ORD-02, ORD-07).
 *
 * <p>클라이언트가 보낸 플레이어 ID를 믿지 않고, 로그인 회원이 리그에서 가진 플레이어를 직접 찾아 쓴다.
 * 이 서비스는 트랜잭션을 열지 않는다. 리그 락과 주문 처리는 {@link LimitOrderService}의 트랜잭션 안에서
 * 일어난다.
 */
@Service
@RequiredArgsConstructor
public class OrderPlacementService {

    /**
     * TODO: 임시 값. 거래일별 기준가(MKT-06)가 생기면 제거하고 market에서 조회한다. 지금은 모든 주문을
     * 시초가(10,000원) 기준으로 처리하므로 2일차 이후에는 정책과 다르게 동작한다. 이 값이 남아 있는 채로
     * 운영에 배포하면 안 된다.
     */
    private static final long TEMPORARY_BASE_PRICE = 10_000L;

    private final LeagueQueryService leagueQueryService;
    private final LimitOrderService limitOrderService;

    /**
     * 로그인 회원이 리그의 종목에 지정가 주문을 넣는다.
     *
     * @param memberId 로그인한 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param side 매매 구분
     * @param price 주문 가격
     * @param quantity 주문 수량
     * @return 접수 직후 주문 상태
     * @throws NaspiBizException 리그에 입장하지 않은 회원이거나({@link ErrorCode#FORBIDDEN}),
     *     {@link LimitOrderService#placeLimitOrder}가 거절하는 경우
     */
    public PlacedOrder placeLimitOrder(long memberId, long leagueId, long stockId, OrderSide side,
            long price, long quantity) {
        return limitOrderService.placeLimitOrder(
                new PlaceLimitOrder(leagueId, playerIdOf(memberId, leagueId), stockId, side,
                        price, quantity),
                TEMPORARY_BASE_PRICE);
    }

    /**
     * 로그인 회원이 본인의 미체결 주문을 취소한다.
     *
     * @param memberId 로그인한 회원 ID
     * @param leagueId 리그 ID
     * @param orderId 주문 ID
     * @return 취소 결과
     * @throws NaspiBizException 리그에 입장하지 않은 회원이거나({@link ErrorCode#FORBIDDEN}),
     *     {@link LimitOrderService#cancelOrder}가 거절하는 경우
     */
    public CanceledOrder cancelOrder(long memberId, long leagueId, long orderId) {
        return limitOrderService.cancelOrder(leagueId, playerIdOf(memberId, leagueId), orderId);
    }

    private long playerIdOf(long memberId, long leagueId) {
        return leagueQueryService.findPlayerId(memberId, leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.FORBIDDEN));
    }
}
