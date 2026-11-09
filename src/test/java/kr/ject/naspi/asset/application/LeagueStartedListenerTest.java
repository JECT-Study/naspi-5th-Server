package kr.ject.naspi.asset.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.ject.naspi.asset.domain.AccountRepository;
import kr.ject.naspi.asset.domain.HoldingRepository;
import kr.ject.naspi.league.application.LeagueStarted;
import kr.ject.naspi.league.application.LeagueStarted.ListedStock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueStartedListener.class, InitialAssetAllocationService.class})
class LeagueStartedListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Test
    @DisplayName("리그가 시작되면 모든 참여자에게 초기 자산이 배분된다")
    void allocatesOnLeagueStarted() {
        eventPublisher.publishEvent(new LeagueStarted(1L, List.of(
                new ListedStock(101, 1), new ListedStock(102, 2), new ListedStock(103, 3))));

        assertThat(accountRepository.findAllByLeagueId(1L)).hasSize(3);
        assertThat(holdingRepository.findAllByLeagueId(1L)).hasSize(9);
    }
}
