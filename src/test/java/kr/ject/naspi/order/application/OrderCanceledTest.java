package kr.ject.naspi.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import kr.ject.naspi.order.domain.CancelReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderCanceledTest {

    @Test
    @DisplayName("리그 종료로 일괄 취소된 주문만 리그 종료 취소로 구분한다")
    void leagueEnded() {
        assertThat(new OrderCanceled(1L, 3L, 11L, 7L, CancelReason.LEAGUE_ENDED).leagueEnded())
                .isTrue();
        assertThat(new OrderCanceled(1L, 3L, 11L, 7L, CancelReason.USER_REQUESTED).leagueEnded())
                .isFalse();
        assertThat(new OrderCanceled(1L, 3L, 11L, 7L, CancelReason.TRADING_DAY_EXPIRED)
                .leagueEnded()).isFalse();
    }
}
