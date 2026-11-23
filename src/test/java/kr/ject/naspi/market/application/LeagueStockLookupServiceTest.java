package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeaguePlayers;
import kr.ject.naspi.league.application.LeaguePlayers.LeaguePlayer;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.domain.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@DataJpaTest
@Import(LeagueStockLookupService.class)
class LeagueStockLookupServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final long PLAYER_ID = 10L;

    @Autowired
    private LeagueStockLookupService service;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    @Test
    @DisplayName("리그 참여자는 같은 리그 종목의 이름과 소개를 종목 ID 순으로 조회하고, 본인 종목에는 mine이 표시된다")
    void findAll() {
        joined();
        long mine = list(1L, PLAYER_ID, "내종목", "내 소개");
        long other = list(1L, 11L, "남종목", "남 소개");

        assertThat(service.findAll(MEMBER_ID, 1L))
                .extracting(MemberStock::stockId, MemberStock::name, MemberStock::description,
                        MemberStock::listerName, MemberStock::mine)
                .containsExactly(
                        tuple(mine, "내종목", "내 소개", "나", true),
                        tuple(other, "남종목", "남 소개", "친구", false));
    }

    @Test
    @DisplayName("다른 리그의 종목은 조회되지 않는다")
    void excludesOtherLeagueStocks() {
        joined();
        list(2L, 20L, "다른리그", "소개");

        assertThat(service.findAll(MEMBER_ID, 1L)).isEmpty();
    }

    @Test
    @DisplayName("상장 전 대기자도 입장한 리그의 종목을 조회할 수 있고, 본인 종목은 없다")
    void waitingPlayerCanLookup() {
        joined();
        list(1L, 11L, "남종목", "남 소개");

        assertThat(service.findAll(MEMBER_ID, 1L))
                .extracting(MemberStock::mine)
                .containsExactly(false);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 종목을 조회할 수 없다")
    void outsiderCannotLookup() {
        notJoined();
        list(1L, 11L, "남종목", "남 소개");

        assertThatThrownBy(() -> service.findAll(MEMBER_ID, 1L))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("리그 참여자는 종목 하나의 이름과 소개를 조회하고, 본인 종목 여부가 표시된다")
    void findOne() {
        joined();
        long mine = list(1L, PLAYER_ID, "내종목", "내 소개");
        long other = list(1L, 11L, "남종목", "남 소개");

        assertThat(service.findOne(MEMBER_ID, 1L, mine))
                .isEqualTo(new MemberStock(mine, "내종목", "내 소개", "나", true));
        assertThat(service.findOne(MEMBER_ID, 1L, other).mine()).isFalse();
    }

    @Test
    @DisplayName("다른 리그의 종목 ID로는 개별 조회할 수 없다")
    void findOneOfOtherLeague() {
        joined();
        long otherLeagueStock = list(2L, 20L, "다른리그", "소개");

        assertThatThrownBy(() -> service.findOne(MEMBER_ID, 1L, otherLeagueStock))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STOCK_NOT_FOUND));
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 종목을 개별 조회할 수 없다")
    void outsiderCannotFindOne() {
        notJoined();
        long stockId = list(1L, 11L, "남종목", "남 소개");

        assertThatThrownBy(() -> service.findOne(MEMBER_ID, 1L, stockId))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private void joined() {
        given(leagueQueryService.getPlayers(MEMBER_ID, 1L)).willReturn(new LeaguePlayers(1L,
                "리그", "ABC1234", null, false, null,
                List.of(player(PLAYER_ID, "나"), player(11L, "친구"))));
        given(leagueQueryService.findPlayerId(MEMBER_ID, 1L)).willReturn(Optional.of(PLAYER_ID));
    }

    private void notJoined() {
        given(leagueQueryService.getPlayers(MEMBER_ID, 1L))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));
    }

    private static LeaguePlayer player(long playerId, String name) {
        return new LeaguePlayer(playerId, name, false, false, true,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private long list(long leagueId, long listerPlayerId, String name, String description) {
        Stock stock = stockRepository.save(Stock.list(leagueId, listerPlayerId,
                StockName.of(name), StockDescription.of(description)));
        entityManager.flush();
        return stock.getId();
    }
}
