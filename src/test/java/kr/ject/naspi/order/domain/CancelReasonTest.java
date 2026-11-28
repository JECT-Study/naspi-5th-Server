package kr.ject.naspi.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CancelReasonTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "USER_REQUESTED, 직접 취소",
        "MARKET_PARTIALLY_FILLED, 체결 가능 수량 부족",
        "MARKET_NOT_FILLED, 체결 가능 수량 없음",
        "SELF_TRADE_PREVENTED, 내 주문 간 체결 방지",
        "TRADING_DAY_EXPIRED, 거래일 변경으로 만료",
        "LEAGUE_ENDED, 거래일 변경으로 만료",
        "MEMBER_WITHDRAWN,"
    })
    @DisplayName("취소 사유 문구는 정책서를 따르고, 리그 종료는 거래일 변경으로 만료, 회원 탈퇴는 문구 없음")
    void message(CancelReason reason, String expected) {
        assertThat(reason.message()).isEqualTo(expected);
    }
}
