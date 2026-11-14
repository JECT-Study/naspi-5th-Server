package kr.ject.naspi.asset.application;

import java.util.Map;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.AccountRepository;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.asset.domain.HoldingRepository;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체결 정산 (AST-05). order가 매칭으로 체결 1건이 정해지면 같은 트랜잭션에서 호출한다.
 *
 * <p>매수자·매도자의 현금과 보유 주식을 한 트랜잭션에서 실제 체결가·체결 수량으로 정산한다
 * (docs/policies/v1/domain/asset.md "주문 가능 자산 및 정산").
 * <ul>
 *   <li>매수자: 예약 현금 {@code 예약 기준 가격 × 수량} 해제, 현금 {@code 체결가 × 수량} 감소, 보유 수량 증가,
 *       매입 금액 가산</li>
 *   <li>매도자: 보유·예약 수량 감소, 매입 금액 비율 감액, 현금 {@code 체결가 × 수량} 증가</li>
 * </ul>
 *
 * <p>계좌와 보유 주식은 쓰기 락으로 조회한다. 서로 반대 방향의 체결이 동시에 정산될 때 서로의 락을 기다리며 멈추지
 * 않도록, 계좌를 먼저 플레이어 ID 오름차순으로, 그다음 보유 주식을 플레이어 ID 오름차순으로 잠근다.
 * 이 순서는 이 메서드 한 번 안에서만 보장된다. 호출하는 트랜잭션이 이미 다른 락(주문 접수 시 예약, 앞선 체결의
 * 정산)을 잡고 있으면 순서가 섞이므로, 매칭을 동시에 실행하지 않도록 order가 직렬화해야 한다 (ORD-03).
 *
 * <p>정산 중 데이터 오류(예약보다 많은 체결, 예약 기준 가격보다 높은 매수 체결가, 계좌·보유 주식 없음)가 나면
 * {@link NaspiRuntimeException}을 던지고 트랜잭션 전체를 롤백한다. 잘못된 값이 저장되지 않게 하기 위함이다.
 */
@Service
@RequiredArgsConstructor
public class AssetSettlementService {

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;

    /**
     * 체결 1건을 매수자·매도자 모두에 대해 정산한다.
     *
     * @param trade 체결 정보
     * @throws IllegalArgumentException 매수자와 매도자가 같거나, 가격·수량이 0 이하인 경우
     * @throws NaspiRuntimeException 정산할 자산이 없거나 예약 기록과 맞지 않는 경우 (데이터 오류)
     */
    @Transactional
    public void settle(TradeSettlement trade) {
        if (trade.buyerPlayerId() == trade.sellerPlayerId()) {
            throw new IllegalArgumentException("매수자와 매도자가 같은 체결은 정산할 수 없습니다: " + trade);
        }
        long leagueId = trade.leagueId();
        long buyer = trade.buyerPlayerId();
        long seller = trade.sellerPlayerId();
        long low = Math.min(buyer, seller);
        long high = Math.max(buyer, seller);

        Map<Long, Account> accounts = Map.of(
                low, lockAccount(leagueId, low), high, lockAccount(leagueId, high));
        Map<Long, Holding> holdings = Map.of(
                low, lockHolding(leagueId, low, trade.stockId()),
                high, lockHolding(leagueId, high, trade.stockId()));
        final Account buyerAccount = accounts.get(buyer);
        final Account sellerAccount = accounts.get(seller);
        final Holding buyerHolding = holdings.get(buyer);
        final Holding sellerHolding = holdings.get(seller);

        buyerAccount.settleBuy(trade.buyReservedPrice(), trade.price(), trade.quantity());
        buyerHolding.settleBuy(trade.price(), trade.quantity());
        sellerHolding.settleSell(trade.quantity());
        sellerAccount.settleSell(trade.price(), trade.quantity());
    }

    private Account lockAccount(long leagueId, long playerId) {
        return accountRepository.findWithLockByLeagueIdAndPlayerId(leagueId, playerId)
                .orElseThrow(() -> new NaspiRuntimeException(
                        "정산할 계좌가 없습니다: leagueId=" + leagueId + ", playerId=" + playerId));
    }

    private Holding lockHolding(long leagueId, long playerId, long stockId) {
        // 초기 배분에서 모든 참여자가 리그의 모든 종목을 받으므로 보유 주식 행은 항상 있다 (AST-01).
        return holdingRepository
                .findWithLockByLeagueIdAndPlayerIdAndStockId(leagueId, playerId, stockId)
                .orElseThrow(() -> new NaspiRuntimeException("정산할 보유 주식이 없습니다: leagueId="
                        + leagueId + ", playerId=" + playerId + ", stockId=" + stockId));
    }
}
