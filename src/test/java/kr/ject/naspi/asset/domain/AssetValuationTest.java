package kr.ject.naspi.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import kr.ject.naspi.asset.domain.AssetValuation.HoldingValuation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class AssetValuationTest {

    private static final long LEAGUE_ID = 1L;
    private static final long PLAYER_ID = 10L;
    private static final long TREASURY_STOCK_ID = 101L;
    private static final long OTHER_STOCK_ID = 102L;

    @Test
    @DisplayName("총자산은 현금과 자사주를 포함한 각 종목 보유 수량 × 평가 가격의 합이다")
    void totalAsset() {
        Account account = Account.open(LEAGUE_ID, PLAYER_ID, 62_500_000);
        List<Holding> holdings = List.of(
                holding(TREASURY_STOCK_ID, 2_500, 0), holding(OTHER_STOCK_ID, 1_250, 0));

        AssetValuation valuation = AssetValuation.evaluate(account, holdings,
                Map.of(TREASURY_STOCK_ID, 12_000L, OTHER_STOCK_ID, 9_000L), TREASURY_STOCK_ID);

        // 2,500 × 12,000 + 1,250 × 9,000 = 30,000,000 + 11,250,000
        assertThat(valuation.stockValuation()).isEqualTo(41_250_000);
        assertThat(valuation.totalAsset()).isEqualTo(62_500_000 + 41_250_000);
        assertThat(valuation.holdings())
                .extracting(HoldingValuation::stockId, HoldingValuation::evaluationPrice,
                        HoldingValuation::valuation)
                .containsExactly(
                        tuple(TREASURY_STOCK_ID, 12_000L, 30_000_000L),
                        tuple(OTHER_STOCK_ID, 9_000L, 11_250_000L));
    }

    @Test
    @DisplayName("예약된 현금과 주식도 총자산에 포함하되 중복 합산하지 않는다")
    void reservedAssetsNotDoubleCounted() {
        Account account = Account.open(LEAGUE_ID, PLAYER_ID, 50_000_000);
        account.reserveForBuy(10_000, 100);
        List<Holding> holdings = List.of(holding(OTHER_STOCK_ID, 1_000, 300));

        AssetValuation valuation = AssetValuation.evaluate(account, holdings,
                Map.of(OTHER_STOCK_ID, 10_000L), TREASURY_STOCK_ID);

        assertThat(valuation.reservedDeposit()).isEqualTo(1_000_000);
        assertThat(valuation.holdings().getFirst().reservedQuantity()).isEqualTo(300);
        assertThat(valuation.totalAsset()).isEqualTo(50_000_000 + 1_000 * 10_000);
    }

    @Test
    @DisplayName("주문 가능 현금은 현금에서 예약 현금을 뺀 금액이다")
    void orderableDeposit() {
        Account account = Account.open(LEAGUE_ID, PLAYER_ID, 50_000_000);
        account.reserveForBuy(10_000, 125);

        AssetValuation valuation = AssetValuation.evaluate(account, List.of(), Map.of(),
                TREASURY_STOCK_ID);

        assertThat(valuation.deposit()).isEqualTo(50_000_000);
        assertThat(valuation.orderableDeposit()).isEqualTo(48_750_000);
        assertThat(valuation.totalAsset()).isEqualTo(50_000_000);
    }

    @Test
    @DisplayName("일반 종목의 매도 가능 수량은 보유 수량에서 예약 수량을 뺀 수량이다")
    void orderableQuantity() {
        AssetValuation valuation = evaluate(holding(OTHER_STOCK_ID, 1_250, 400));

        HoldingValuation holding = valuation.holdings().getFirst();
        assertThat(holding.treasuryStock()).isFalse();
        assertThat(holding.orderableQuantity()).isEqualTo(850);
    }

    @ParameterizedTest(name = "보유 {0}주, 매도 예약 {1}주 → 매도 가능 {2}주")
    @CsvSource({
        "2500, 0, 1500",
        "2500, 1000, 500",
        "2500, 1500, 0",
        "1500, 600, 0",
        "800, 0, 0",
    })
    @DisplayName("자사주 매도 가능 수량은 max(0, 보유 수량 - 매도 예약 수량 - 1,000주)이다")
    void treasuryStockOrderableQuantity(long quantity, long reserved, long expected) {
        AssetValuation valuation = evaluate(holding(TREASURY_STOCK_ID, quantity, reserved));

        HoldingValuation holding = valuation.holdings().getFirst();
        assertThat(holding.treasuryStock()).isTrue();
        assertThat(holding.orderableQuantity()).isEqualTo(expected);
    }

    @Test
    @DisplayName("보유 수량이 0인 종목은 보유 종목에서 제외하고, 보유 종목은 종목 ID 순으로 정렬한다")
    void excludesEmptyHoldings() {
        AssetValuation valuation = evaluate(
                holding(OTHER_STOCK_ID, 100, 0), holding(103L, 0, 0),
                holding(TREASURY_STOCK_ID, 2_500, 0));

        assertThat(valuation.holdings()).extracting(HoldingValuation::stockId)
                .containsExactly(TREASURY_STOCK_ID, OTHER_STOCK_ID);
    }

    @Test
    @DisplayName("평가 손익은 평가 금액에서 매입 금액을 뺀 금액이고, 수익률은 평가 손익 ÷ 매입 금액 × 100이다")
    void profitAndReturnRate() {
        // 초기 배분 2,500주의 매입 금액 = 2,500 × 10,000원
        Holding gain = holding(TREASURY_STOCK_ID, 2_500, 0);
        Holding loss = holding(OTHER_STOCK_ID, 1_250, 0);
        Account account = Account.open(LEAGUE_ID, PLAYER_ID, 62_500_000);

        AssetValuation valuation = AssetValuation.evaluate(account, List.of(gain, loss),
                Map.of(TREASURY_STOCK_ID, 12_000L, OTHER_STOCK_ID, 8_700L), TREASURY_STOCK_ID);

        assertThat(valuation.holdings())
                .extracting(HoldingValuation::stockId, HoldingValuation::purchaseAmount,
                        HoldingValuation::profit, HoldingValuation::returnRate)
                .containsExactly(
                        tuple(TREASURY_STOCK_ID, 25_000_000L, 5_000_000L, new BigDecimal("20.0")),
                        tuple(OTHER_STOCK_ID, 12_500_000L, -1_625_000L, new BigDecimal("-13.0")));
    }

    @Test
    @DisplayName("자사주도 일반 종목과 같은 방식으로 평가 손익과 수익률을 계산한다")
    void treasuryStockProfit() {
        Holding treasury = holding(TREASURY_STOCK_ID, 1_000, 0);
        Holding other = holding(OTHER_STOCK_ID, 1_000, 0);
        Account account = Account.open(LEAGUE_ID, PLAYER_ID, 80_000_000);

        AssetValuation valuation = AssetValuation.evaluate(account, List.of(treasury, other),
                Map.of(TREASURY_STOCK_ID, 11_000L, OTHER_STOCK_ID, 11_000L), TREASURY_STOCK_ID);

        HoldingValuation first = valuation.holdings().get(0);
        HoldingValuation second = valuation.holdings().get(1);
        assertThat(first.treasuryStock()).isTrue();
        assertThat(second.treasuryStock()).isFalse();
        assertThat(first.profit()).isEqualTo(second.profit()).isEqualTo(1_000_000);
        assertThat(first.returnRate()).isEqualTo(second.returnRate())
                .isEqualTo(new BigDecimal("10.0"));
    }

    @ParameterizedTest(name = "평가 손익 {0}원 ÷ 매입 금액 {1}원 → {2}%")
    @CsvSource({
        "1234, 10000, 12.3",
        "1235, 10000, 12.4",
        "-1234, 10000, -12.3",
        "-1235, 10000, -12.4",
        "1, 3, 33.3",
        "2, 3, 66.7",
        "0, 10000, 0.0",
    })
    @DisplayName("수익률은 소수점 첫째 자리까지 반올림하고, 음수는 절댓값 기준으로 반올림한다")
    void returnRateRounding(long profit, long baseAmount, String expected) {
        assertThat(AssetValuation.returnRate(profit, baseAmount))
                .isEqualTo(new BigDecimal(expected));
    }

    @Test
    @DisplayName("매입 금액(이동평균법)이 손익 기준이며, 매입 금액이 다르면 같은 평가 금액도 손익과 수익률이 다르다")
    void profitUsesPurchaseAmount() {
        // 이동평균 매입 단가 11,000원 (10,000원 100주 + 12,000원 100주)
        Holding holding = withPurchaseAmount(holding(OTHER_STOCK_ID, 200, 0), 2_200_000);

        AssetValuation valuation = evaluate(holding);

        // 200 × 10,000 - 2,200,000 = -200,000 → -200,000 ÷ 2,200,000 × 100 = -9.09...
        HoldingValuation result = valuation.holdings().getFirst();
        assertThat(result.averagePrice()).isEqualTo(new BigDecimal("11000.00"));
        assertThat(result.profit()).isEqualTo(-200_000);
        assertThat(result.returnRate()).isEqualTo(new BigDecimal("-9.1"));
    }

    @Test
    @DisplayName("초기 배분만 받은 종목의 평균 매입가는 시초가 10,000원이다")
    void initialAveragePrice() {
        AssetValuation valuation = evaluate(holding(OTHER_STOCK_ID, 1_250, 0));

        assertThat(valuation.holdings().getFirst().averagePrice())
                .isEqualTo(new BigDecimal("10000.00"));
    }

    @ParameterizedTest(name = "매입 금액 {0}원 ÷ 보유 수량 {1}주 → 평균 매입가 {2}원")
    @CsvSource({
        "2200000, 200, 11000.00",
        "1000000, 3, 333333.33",
        "2000000, 3, 666666.67",
        "100001, 8, 12500.13",
        "100004, 8, 12500.50",
        "0, 0, 0.00",
    })
    @DisplayName("평균 매입가는 매입 금액 ÷ 보유 수량이고, 소수점 셋째 자리에서 반올림해 둘째 자리까지 표시한다")
    void averagePriceRounding(long purchaseAmount, long quantity, String expected) {
        assertThat(AssetValuation.averagePrice(purchaseAmount, quantity))
                .isEqualTo(new BigDecimal(expected));
    }

    @ParameterizedTest(name = "현금 {0}원, 평가 금액 {1}원 → 총자산 평가 손익 {2}원")
    @CsvSource({
        "50000000, 50000000, 0",
        "60000000, 52500000, 12500000",
        "40000000, 45000000, -15000000",
    })
    @DisplayName("총자산 평가 손익은 총자산에서 초기 총자산 1억 원을 뺀 금액이다 (매도로 현금이 된 손익 포함)")
    void totalProfit(long deposit, long stockValuation, long expected) {
        Account account = Account.open(LEAGUE_ID, PLAYER_ID, deposit);
        Holding holding = holding(OTHER_STOCK_ID, stockValuation / 10_000, 0);

        AssetValuation valuation = AssetValuation.evaluate(account, List.of(holding),
                Map.of(OTHER_STOCK_ID, 10_000L), TREASURY_STOCK_ID);

        assertThat(valuation.totalProfit()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "현금 {0}원, 평가 금액 {1}원 → 총자산 수익률 {2}%")
    @CsvSource({
        "50000000, 50000000, 0.0",
        "60000000, 52500000, 12.5",
        "40000000, 45000000, -15.0",
        "50000000, 50120000, 0.1",
        "50000000, 49870000, -0.1",
        "50000000, 49950000, -0.1",
    })
    @DisplayName("총자산 수익률은 총자산 평가 손익 ÷ 1억 원 × 100이고, 소수점 첫째 자리까지 절댓값 기준으로 반올림한다")
    void totalReturnRate(long deposit, long stockValuation, String expected) {
        Account account = Account.open(LEAGUE_ID, PLAYER_ID, deposit);
        Holding holding = holding(OTHER_STOCK_ID, stockValuation / 10_000, 0);

        AssetValuation valuation = AssetValuation.evaluate(account, List.of(holding),
                Map.of(OTHER_STOCK_ID, 10_000L), TREASURY_STOCK_ID);

        assertThat(valuation.totalReturnRate()).isEqualTo(new BigDecimal(expected));
    }

    @Test
    @DisplayName("보유 종목의 평가 가격이 없으면 평가할 수 없다")
    void missingPrice() {
        Account account = Account.open(LEAGUE_ID, PLAYER_ID, 50_000_000);

        assertThatThrownBy(() -> AssetValuation.evaluate(account,
                List.of(holding(OTHER_STOCK_ID, 100, 0)), Map.of(), TREASURY_STOCK_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("다른 플레이어의 보유 주식은 함께 평가할 수 없다")
    void otherPlayersHolding() {
        Account account = Account.open(LEAGUE_ID, PLAYER_ID, 50_000_000);
        Holding others = Holding.allocate(LEAGUE_ID, 99L, OTHER_STOCK_ID, 100);

        assertThatThrownBy(() -> AssetValuation.evaluate(account, List.of(others),
                Map.of(OTHER_STOCK_ID, 10_000L), TREASURY_STOCK_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AssetValuation evaluate(Holding... holdings) {
        Account account = Account.open(LEAGUE_ID, PLAYER_ID, 50_000_000);
        Map<Long, Long> prices = Map.of(
                TREASURY_STOCK_ID, 10_000L, OTHER_STOCK_ID, 10_000L, 103L, 10_000L);
        return AssetValuation.evaluate(account, List.of(holdings), prices, TREASURY_STOCK_ID);
    }

    private static Holding holding(long stockId, long quantity, long reservedQuantity) {
        Holding holding = Holding.allocate(LEAGUE_ID, PLAYER_ID, stockId, quantity);
        // 예약은 주문 접수(AST-03, AST-04) 구현 전이라 필드를 직접 설정한다.
        ReflectionTestUtils.setField(holding, "reservedQuantity", reservedQuantity);
        return holding;
    }

    private static Holding withPurchaseAmount(Holding holding, long purchaseAmount) {
        // 체결 시 매입 금액 갱신(AST-05) 구현 전이라 필드를 직접 설정한다.
        ReflectionTestUtils.setField(holding, "purchaseAmount", purchaseAmount);
        return holding;
    }
}
