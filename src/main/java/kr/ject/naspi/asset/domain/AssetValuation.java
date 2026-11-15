package kr.ject.naspi.asset.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 참여자 한 명의 자산 평가.
 *
 * <p>정책 (docs/policies/v1/domain/asset.md "총자산 집계"):
 * {@code 총자산 = 현금 + Σ(각 종목 보유 수량 × 평가 가격)}. 평가 가격은 호출하는 쪽이 지정하고
 * (진행 중: 현재가, 리그 종료 시: 종료 평가 가격), 자사주를 포함한다. 금액은 모두 정수(원)라
 * 원 미만이 생기지 않는다.
 *
 * <p>예수금과 보유 수량은 예약분을 포함한 전체 값이므로 예약 자산을 따로 더하지 않는다 (중복 합산 없음).
 *
 * <p>손익 (docs/policies/v1/domain/asset.md "자산 조회"):
 * <ul>
 *   <li>{@code 평균 매입가 = 매입 금액 ÷ 보유 수량}, 원 미만 절사 (표시용)</li>
 *   <li>{@code 평가 손익 = 평가 금액 - 매입 금액}</li>
 *   <li>{@code 수익률(%) = 평가 손익 ÷ 매입 금액 × 100}, 소수점 첫째 자리까지 반올림.
 *       음수는 절댓값 기준으로 반올림한다 (-12.35% → -12.4%)</li>
 *   <li>{@code 총자산 평가 손익 = 총자산 - 1억 원}. 이미 매도해 현금으로 바뀐 손익을 포함한다</li>
 *   <li>{@code 총자산 수익률(%) = 총자산 평가 손익 ÷ 1억 원 × 100}. 반올림은 수익률과 같다</li>
 * </ul>
 *
 * <p>매입 금액은 이동평균법으로 관리한다. 체결 시 갱신은 정산(AST-05)이 맡고, 여기서는 저장된 값을 쓴다.
 *
 * @param playerId 플레이어 ID
 * @param deposit 예수금. 예약금을 포함한 전체 현금
 * @param reservedDeposit 예약금. 미체결 매수 주문에 묶인 현금
 * @param orderableDeposit 주문 가능 현금
 * @param stockValuation 보유 주식 평가액 합계
 * @param totalAsset 총자산
 * @param totalProfit 총자산 평가 손익 ({@code 총자산 - 1억 원})
 * @param totalReturnRate 총자산 수익률(%). 소수점 첫째 자리까지
 * @param holdings 보유 수량이 있는 종목. 종목 ID 순
 */
public record AssetValuation(
        long playerId,
        long deposit,
        long reservedDeposit,
        long orderableDeposit,
        long stockValuation,
        long totalAsset,
        long totalProfit,
        BigDecimal totalReturnRate,
        List<HoldingValuation> holdings
) {

    /** 수익률 소수점 자릿수. */
    static final int RETURN_RATE_SCALE = 1;

    /**
     * 목록을 복사해 변경할 수 없게 만든다.
     */
    public AssetValuation {
        holdings = List.copyOf(holdings);
    }

    /**
     * 계좌와 보유 주식을 평가 가격으로 평가한다.
     *
     * @param account 플레이어의 계좌
     * @param holdings 플레이어의 보유 주식
     * @param evaluationPrices 종목 ID별 평가 가격 (1주당 원)
     * @param treasuryStockId 자사주(본인이 상장한 종목) ID. 없으면 {@code null}
     * @return 자산 평가 결과
     * @throws IllegalArgumentException 다른 플레이어의 보유 주식이 섞여 있거나,
     *     보유 종목의 평가 가격이 없거나 음수인 경우
     */
    public static AssetValuation evaluate(Account account, List<Holding> holdings,
            Map<Long, Long> evaluationPrices, Long treasuryStockId) {

        holdings.forEach(holding -> validateOwner(account, holding));

        // 보유 중인 주식에 대해 가치 평가
        List<HoldingValuation> valuations = holdings.stream()
                .filter(holding -> holding.getQuantity() > 0)
                .sorted(Comparator.comparing(Holding::getStockId))
                .map(holding -> HoldingValuation.of(holding,
                        priceOf(evaluationPrices, holding.getStockId()),    // quantity * price 로 계산
                        Objects.equals(holding.getStockId(), treasuryStockId)))
                .toList();

        // 평가액(quantity * price)을 0부터 모두 더한다.
        // Math.addExact는 long 오버플로우 시 ArithmeticException을 던진다.
        long stockValuation = valuations.stream()
                .map(HoldingValuation::valuation)
                .reduce(0L, Math::addExact);
        long totalAsset = Math.addExact(account.getDeposit(), stockValuation);
        long totalProfit = Math.subtractExact(totalAsset, InitialAllocation.INITIAL_TOTAL_ASSET);
        return new AssetValuation(account.getPlayerId(), account.getDeposit(),
                account.getReservedDeposit(), account.orderableDeposit(), stockValuation,
                totalAsset, totalProfit,
                returnRate(totalProfit, InitialAllocation.INITIAL_TOTAL_ASSET), valuations);
    }

    /**
     * 수익률(%)을 계산한다. {@link RoundingMode#HALF_UP}은 절댓값 기준 반올림이라
     * 음수도 -12.35 → -12.4가 된다.
     *
     * @param profit 평가 손익
     * @param baseAmount 수익률의 기준 금액 (종목: 매입 금액, 총자산: 1억 원)
     * @return 소수점 첫째 자리까지의 수익률. 기준 금액이 0이면 0.0
     */
    static BigDecimal returnRate(long profit, long baseAmount) {
        if (baseAmount == 0) {
            return BigDecimal.ZERO.setScale(RETURN_RATE_SCALE);
        }
        return BigDecimal.valueOf(profit)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(baseAmount), RETURN_RATE_SCALE,
                        RoundingMode.HALF_UP);
    }

    /**
     * 평균 매입가를 계산한다. 원 미만은 절사한다.
     *
     * <p>매입 금액과 보유 수량은 음수가 아니므로 정수 나눗셈이 곧 절사다. 매도 후 매입 금액을 원 미만 절사해도
     * {@code ⌊⌊a⌋ ÷ n⌋ = ⌊a ÷ n⌋}이라 원 단위 평균 매입가는 매도 전과 같다.
     *
     * @param purchaseAmount 매입 금액
     * @param quantity 보유 수량
     * @return 1주당 평균 매입가 (원). 보유 수량이 0이면 0
     */
    static long averagePrice(long purchaseAmount, long quantity) {
        if (quantity == 0) {
            return 0;
        }
        return purchaseAmount / quantity;
    }

    private static void validateOwner(Account account, Holding holding) {
        if (!holding.getPlayerId().equals(account.getPlayerId())) {
            throw new IllegalArgumentException("다른 플레이어의 보유 주식입니다: holdingId="
                    + holding.getId() + ", playerId=" + holding.getPlayerId());
        }
    }

    private static long priceOf(Map<Long, Long> evaluationPrices, long stockId) {
        Long price = evaluationPrices.get(stockId);
        if (price == null || price < 0) {
            throw new IllegalArgumentException(
                    "평가 가격이 없거나 음수입니다: stockId=" + stockId + ", price=" + price);
        }
        return price;
    }

    /**
     * 보유 종목 하나의 평가.
     *
     * @param stockId 종목 ID
     * @param treasuryStock 자사주(본인이 상장한 종목) 여부
     * @param quantity 보유 수량. 예약 수량 포함
     * @param reservedQuantity 예약 수량. 미체결 매도 주문에 묶인 수량
     * @param orderableQuantity 매도 주문 가능 수량. 자사주는 최소 유지분 1,000주 제외
     * @param evaluationPrice 평가 가격 (1주당 원)
     * @param valuation 평가액 ({@code 보유 수량 × 평가 가격})
     * @param purchaseAmount 매입 금액 (이동평균법)
     * @param averagePrice 평균 매입가 ({@code 매입 금액 ÷ 보유 수량}, 원 미만 절사)
     * @param profit 평가 손익 ({@code 평가액 - 매입 금액})
     * @param returnRate 수익률(%). 소수점 첫째 자리까지
     */
    public record HoldingValuation(
            long stockId,
            boolean treasuryStock,
            long quantity,
            long reservedQuantity,
            long orderableQuantity,
            long evaluationPrice,
            long valuation,
            long purchaseAmount,
            long averagePrice,
            long profit,
            BigDecimal returnRate) {

        static HoldingValuation of(Holding holding, long evaluationPrice, boolean treasuryStock) {
            long valuation = Math.multiplyExact(holding.getQuantity(), evaluationPrice);
            long purchaseAmount = holding.getPurchaseAmount();
            long profit = Math.subtractExact(valuation, purchaseAmount);
            return new HoldingValuation(
                    holding.getStockId(),
                    treasuryStock,
                    holding.getQuantity(),
                    holding.getReservedQuantity(),
                    holding.orderableQuantity(treasuryStock),
                    evaluationPrice,
                    valuation,
                    purchaseAmount,
                    AssetValuation.averagePrice(purchaseAmount, holding.getQuantity()),
                    profit,
                    AssetValuation.returnRate(profit, purchaseAmount)
            );
        }
    }
}
