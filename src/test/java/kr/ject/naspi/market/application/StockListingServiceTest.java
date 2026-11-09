package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.domain.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import(StockListingService.class)
class StockListingServiceTest {

    private static final long LEAGUE_ID = 1L;

    @Autowired
    private StockListingService service;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("참여자는 종목 이름과 소개를 작성해 종목을 상장할 수 있고, 상장 이벤트가 발행된다")
    void list() {
        long stockId = service.list(LEAGUE_ID, 10L, " 나스피 ", "친구들과 함께하는 모의 투자");

        assertThat(stockRepository.existsByLeagueIdAndName(LEAGUE_ID, StockName.of("나스피")))
                .isTrue();
        assertThat(events.stream(StockListed.class))
                .containsExactly(new StockListed(stockId, LEAGUE_ID, 10L));
    }

    @Test
    @DisplayName("상장 시점의 종목에는 현재가가 없다 (시초가는 리그 시작 때 적용된다)")
    void noCurrentPriceAtListing() {
        long stockId = service.list(LEAGUE_ID, 10L, "나스피", "소개");

        assertThat(entityManager.find(Stock.class, stockId).getCurrentPrice()).isNull();
    }

    @Test
    @DisplayName("한 사용자는 하나의 종목만 상장할 수 있다")
    void onlyOneStockPerLister() {
        service.list(LEAGUE_ID, 10L, "나스피", "소개");

        assertThatThrownBy(() -> service.list(LEAGUE_ID, 10L, "다른종목", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_ALREADY_LISTED);
    }

    @Test
    @DisplayName("같은 리그 안에서는 종목 이름이 중복될 수 없다")
    void duplicatedNameInSameLeague() {
        service.list(LEAGUE_ID, 10L, "나스피", "소개");

        assertThatThrownBy(() -> service.list(LEAGUE_ID, 11L, " 나스피 ", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("다른 리그에서는 같은 종목 이름을 사용할 수 있다")
    void sameNameInOtherLeague() {
        service.list(LEAGUE_ID, 10L, "나스피", "소개");

        long otherStockId = service.list(2L, 20L, "나스피", "소개");

        assertThat(otherStockId).isPositive();
    }

    @Test
    @DisplayName("이름 또는 소개가 규칙에 맞지 않으면 상장되지 않고 이벤트도 발행되지 않는다")
    void invalidInput() {
        assertThatThrownBy(() -> service.list(LEAGUE_ID, 10L, "나스피!", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_NAME);
        assertThatThrownBy(() -> service.list(LEAGUE_ID, 10L, "나스피", " "))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_DESCRIPTION);

        assertThat(events.stream(StockListed.class)).isEmpty();
        assertThat(stockRepository.existsByListerPlayerId(10L)).isFalse();
    }
}
