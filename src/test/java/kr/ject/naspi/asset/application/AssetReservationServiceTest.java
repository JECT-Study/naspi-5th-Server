package kr.ject.naspi.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 예약 규칙 자체는 {@code AccountTest}가 검증한다. 여기서는 계좌 조회와 저장만 확인한다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(AssetReservationService.class)
class AssetReservationServiceTest {

    private static final long LEAGUE_ID = 1L;
    private static final long PLAYER_ID = 10L;

    @Autowired
    private AssetReservationService service;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("매수 주문은 예약 기준 가격 × 주문 수량만큼 참여자의 현금을 예약한다")
    void reserveCashForBuy() {
        final long accountId = entityManager.persistAndGetId(
                Account.open(LEAGUE_ID, PLAYER_ID, 1_000_000), Long.class);

        long reserved = service.reserveCashForBuy(LEAGUE_ID, PLAYER_ID, 10_000, 80);

        entityManager.flush();
        entityManager.clear();
        assertThat(reserved).isEqualTo(800_000);
        assertThat(entityManager.find(Account.class, accountId).getReservedDeposit())
                .isEqualTo(800_000);
    }

    @Test
    @DisplayName("초기 자산이 배분되지 않은 참여자는 매수 주문을 넣을 수 없다")
    void rejectWithoutAccount() {
        assertThatThrownBy(() -> service.reserveCashForBuy(LEAGUE_ID, PLAYER_ID, 10_000, 1))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ASSET_NOT_FOUND);
    }
}
