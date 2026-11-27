package kr.ject.naspi.market.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FinalEvaluationPriceTest {

    private static final Supplier<List<EvaluatedTrade>> NOT_CALLED = () -> {
        throw new AssertionError("최근 체결을 조회하면 안 된다");
    };

    private static EvaluatedTrade trade(long price, long quantity) {
        return new EvaluatedTrade(price, quantity);
    }

    @Test
    @DisplayName("종료에 가까운 체결부터 500주까지 모으고 마지막 체결 건은 500주를 채우는 데 필요한 수량만 반영한다")
    void takesRecentQuantityUntilLimit() {
        List<EvaluatedTrade> recentFirst = List.of(trade(10_000, 300), trade(11_000, 150),
                trade(12_000, 100), trade(13_000, 50));

        assertThat(FinalEvaluationPrice.takeRecentQuantity(recentFirst, 500))
                .containsExactly(trade(10_000, 300), trade(11_000, 150), trade(12_000, 50));
        assertThat(FinalEvaluationPrice.takeRecentQuantity(List.of(), 500)).isEmpty();
    }

    @Test
    @DisplayName("마지막 거래일 누적 체결량이 500주 이상이면 마지막 거래일 VWAP을 쓴다")
    void lastDayAtLeast500UsesLastDayOnly() {
        TradeVolume lastDay = new TradeVolume(500, 5_100_000);

        assertThat(FinalEvaluationPrice.evaluate(lastDay, NOT_CALLED)).isEqualTo(10_200);
    }

    @Test
    @DisplayName("마지막 거래일 체결량이 500주를 넘어도 마지막 거래일 전체 체결로 계산한다")
    void lastDayOver500UsesWholeLastDay() {
        // 300주 @10,000 (먼저) + 500주 @12,000 (최근). 최근 500주만이면 12,000
        TradeVolume lastDay = TradeVolume.of(List.of(trade(12_000, 500), trade(10_000, 300)));

        assertThat(FinalEvaluationPrice.evaluate(lastDay, NOT_CALLED)).isEqualTo(11_250);
    }

    @Test
    @DisplayName("마지막 거래일 체결량이 500주 미만이면 종료에 가까운 과거 체결부터 500주까지 확대한다")
    void lastDayBelow500ExpandsToRecentTrades() {
        TradeVolume lastDay = new TradeVolume(499, 4_990_000);
        List<EvaluatedTrade> recentFirst = List.of(trade(10_000, 499), trade(12_000, 10));

        // 499주 @10,000 + 1주 @12,000 = 5,002,000 / 500
        assertThat(FinalEvaluationPrice.evaluate(lastDay, () -> recentFirst))
                .isEqualTo(10_004);
    }

    @Test
    @DisplayName("마지막 거래일 체결이 없으면 이전 거래일의 최근 체결 500주로 계산한다")
    void noLastDayTradesUsesRecent500() {
        List<EvaluatedTrade> recentFirst = List.of(trade(11_000, 300), trade(12_000, 300));

        // 300주 @11,000 + 200주 @12,000 = 5,700,000 / 500
        assertThat(FinalEvaluationPrice.evaluate(new TradeVolume(0, 0), () -> recentFirst))
                .isEqualTo(11_400);
    }

    @Test
    @DisplayName("리그 전체 거래량이 500주 미만이면 전체 체결을 쓴다")
    void leagueBelow500UsesAllTrades() {
        TradeVolume lastDay = new TradeVolume(299, 2_990_000);
        List<EvaluatedTrade> recentFirst = List.of(trade(10_000, 299), trade(10_300, 200));

        // 5,050,000 / 499 = 10,120.2... → 원 미만 절사
        assertThat(FinalEvaluationPrice.evaluate(lastDay, () -> recentFirst))
                .isEqualTo(10_120);
    }

    @Test
    @DisplayName("리그 전체에서 체결이 없는 종목은 시초가로 평가한다")
    void noTradesUsesOpeningPrice() {
        assertThat(FinalEvaluationPrice.evaluate(new TradeVolume(0, 0), List::of))
                .isEqualTo(Stock.OPENING_PRICE);
    }
}
