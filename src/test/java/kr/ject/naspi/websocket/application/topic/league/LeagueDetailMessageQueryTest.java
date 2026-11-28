package kr.ject.naspi.websocket.application.topic.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import kr.ject.naspi.league.application.LeagueDetail;
import kr.ject.naspi.league.application.LeagueDetail.DetailPlayer;
import kr.ject.naspi.league.application.LeagueDetail.PlayerStock;
import kr.ject.naspi.league.application.LeagueDetailUseCase;
import kr.ject.naspi.league.application.LeagueTradingDay;
import kr.ject.naspi.websocket.api.response.league.LeagueDetailResponse;
import kr.ject.naspi.websocket.api.response.league.LeagueDetailResponse.Player;
import kr.ject.naspi.websocket.api.response.league.LeagueDetailResponse.Stock;
import kr.ject.naspi.websocket.api.response.league.LeagueDetailResponse.TradingDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeagueDetailMessageQueryTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 21, 30, 1);
    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 11, 27, 20, 0);

    private final LeagueDetailUseCase useCase = mock(LeagueDetailUseCase.class);
    private final Clock clock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
    private final LeagueDetailMessageQuery query = new LeagueDetailMessageQuery(useCase, clock);

    @Test
    @DisplayName("리그 상세 메시지는 조회자 없는 리그 상세를 그대로 옮기고, 대기자는 종목이 null이며 asOf는 조회 직전 시각이다")
    void mapsDetail() {
        given(useCase.getDetailInLeague(1L)).willReturn(new LeagueDetail(1L, "리그", "ABC1234",
                JOINED_AT.plusDays(7), false, null, List.of(
                        new DetailPlayer(30L, "수빈", false, true, true, JOINED_AT, JOINED_AT,
                                new PlayerStock(99L, "수빈전자", "소개")),
                        new DetailPlayer(31L, "민준", false, false, false,
                                JOINED_AT.plusMinutes(1), null, null))));

        LeagueDetailResponse response = query.getDetail(1L);

        assertThat(response).isEqualTo(new LeagueDetailResponse(1L, "리그", "ABC1234",
                JOINED_AT.plusDays(7), false, null, List.of(
                        new Player(30L, "수빈", true, true, JOINED_AT, JOINED_AT,
                                new Stock(99L, "수빈전자", "소개")),
                        new Player(31L, "민준", false, false, JOINED_AT.plusMinutes(1), null,
                                null)), NOW));
    }

    @Test
    @DisplayName("시작된 리그는 현재 거래일을 함께 담는다")
    void mapsTradingDay() {
        LeagueTradingDay tradingDay = new LeagueTradingDay(2, 7, NOW.plusHours(12),
                JOINED_AT, JOINED_AT.plusDays(7));
        given(useCase.getDetailInLeague(1L)).willReturn(
                new LeagueDetail(1L, "리그", null, null, false, tradingDay, List.of()));

        assertThat(query.getDetail(1L).tradingDay()).isEqualTo(new TradingDay(2, 7,
                NOW.plusHours(12), JOINED_AT, JOINED_AT.plusDays(7)));
    }
}
