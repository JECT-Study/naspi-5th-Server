package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.CreatedLeague;
import kr.ject.naspi.league.application.LeagueService;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueCreatedListener.class, StockListingService.class, ClockConfig.class})
@ComponentScan(basePackageClasses = LeagueService.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = ".*\\.(LeagueService|InviteCodeIssuer)"))
class LeagueCreatedListenerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("방장이 리그 이름을 지정하고 상장을 마치면 리그가 생성되고, 방장은 참여자가 된다")
    void listHostStockOnLeagueCreated() {
        CreatedLeague created = leagueService.create(MEMBER_ID, "우리끼리 주식왕", "나스피", "소개");

        assertThat(stockRepository.findAllByLeagueId(created.leagueId()))
                .singleElement()
                .extracting(Stock::getListerPlayerId)
                .isEqualTo(created.hostPlayerId());

        Player host = entityManager.find(Player.class, created.hostPlayerId());
        assertThat(host.isListed()).isTrue();
        assertThat(host.getListedAt()).isNotNull();
    }

    @Test
    @DisplayName("방장의 종목이 상장 규칙에 맞지 않으면 리그를 생성할 수 없다")
    void failLeagueCreationWhenHostStockInvalid() {
        assertThatThrownBy(() -> leagueService.create(MEMBER_ID, "리그", "특수문자!", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_NAME);
    }
}
