package kr.ject.naspi.asset.application;

import kr.ject.naspi.asset.domain.AccountRepository;
import kr.ject.naspi.asset.domain.HoldingRepository;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.market.application.StockQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 접수 시 자산 예약 (AST-03 매수 현금, AST-04 매도 주식). order가 주문을 접수할 때 호출한다.
 *
 * <p>잔액·수량 확인과 예약은 계좌(매수) 또는 보유 주식(매도) 쓰기 락을 잡은 상태에서 한 번에 처리한다.
 * 같은 참여자의 주문이 동시에 들어와도 차례로 처리되므로 주문 가능 자산을 넘어 예약되지 않는다. 주문 저장이 실패하면 예약도 롤백되도록 order의
 * 주문 접수와 같은 트랜잭션에서 호출하며, 락은 그 트랜잭션이 끝날 때 풀린다.
 *
 * <p>예약 기준 가격은 order가 정한다 (docs/policies/v1/domain/asset.md "매수 주문의 현금 예약").
 * <ul>
 *   <li>지정가: 지정 가격</li>
 *   <li>시장가: 보호 조건 내 최고 체결 가능 가격</li>
 * </ul>
 * 주문별 예약 기준 가격은 체결 시 차액 해제(AST-05)와 취소·만료 시 해제(AST-06)에 필요하므로 order가 주문에
 * 남겨 둔다. asset은 참여자별 예약 합계만 관리한다.
 */
@Service
@RequiredArgsConstructor
public class AssetReservationService {

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final StockQueryService stockQueryService;

    /**
     * 매수 주문에 필요한 현금({@code 예약 기준 가격 × 주문 수량})을 예약한다. 주문 가능 현금보다 많으면 거절한다.
     *
     * @param leagueId 리그 ID
     * @param playerId 주문한 플레이어 ID
     * @param price 예약 기준 가격. 양수
     * @param quantity 주문 수량. 양수
     * @return 예약한 금액
     * @throws IllegalArgumentException 가격이나 수량이 0 이하인 경우
     * @throws NaspiBizException 참여자의 자산이 없는 경우 ({@link ErrorCode#ASSET_NOT_FOUND}),
     *     주문 가능 현금이 부족한 경우 ({@link ErrorCode#INSUFFICIENT_DEPOSIT})
     */
    @Transactional
    public long reserveCashForBuy(long leagueId, long playerId, long price, long quantity) {
        return accountRepository.findWithLockByLeagueIdAndPlayerId(leagueId, playerId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.ASSET_NOT_FOUND))
                .reserveForBuy(price, quantity);
    }

    /**
     * 매도 주문 수량만큼 주식을 예약한다. 매도 가능 수량(보유 수량 - 예약 수량)보다 많으면 거절하고,
     * 자사주는 최소 유지분 1,000주를 남기지 못해도 거절한다.
     *
     * @param leagueId 리그 ID
     * @param playerId 주문한 플레이어 ID
     * @param stockId 매도할 종목 ID
     * @param quantity 주문 수량. 양수
     * @throws IllegalArgumentException 수량이 0 이하인 경우
     * @throws NaspiBizException 리그에 종목이 없는 경우 ({@link ErrorCode#STOCK_NOT_FOUND}),
     *     매도 가능 수량이 부족한 경우 ({@link ErrorCode#INSUFFICIENT_QUANTITY}),
     *     자사주 최소 유지분을 남기지 못하는 경우 ({@link ErrorCode#TREASURY_STOCK_MIN_SHARES})
     */
    @Transactional
    public void reserveStockForSell(long leagueId, long playerId, long stockId, long quantity) {
        // 상장자는 바뀌지 않으므로 락을 잡기 전에 조회해 락 구간을 줄인다.
        boolean treasuryStock = stockQueryService.isListedBy(leagueId, stockId, playerId);
        holdingRepository.findWithLockByLeagueIdAndPlayerIdAndStockId(leagueId, playerId, stockId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.INSUFFICIENT_QUANTITY))
                .reserveForSell(quantity, treasuryStock);
    }
}
