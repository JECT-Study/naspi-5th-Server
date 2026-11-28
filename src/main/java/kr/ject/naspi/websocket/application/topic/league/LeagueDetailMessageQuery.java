package kr.ject.naspi.websocket.application.topic.league;

import java.time.Clock;
import java.time.LocalDateTime;
import kr.ject.naspi.league.application.LeagueDetail;
import kr.ject.naspi.league.application.LeagueDetail.DetailPlayer;
import kr.ject.naspi.league.application.LeagueDetailUseCase;
import kr.ject.naspi.league.application.LeagueTradingDay;
import kr.ject.naspi.websocket.api.response.league.LeagueDetailResponse;
import kr.ject.naspi.websocket.api.response.league.LeagueDetailResponse.Player;
import kr.ject.naspi.websocket.api.response.league.LeagueDetailResponse.Stock;
import kr.ject.naspi.websocket.api.response.league.LeagueDetailResponse.TradingDay;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 리그 상세 메시지 조회. REST와 같은 리그 상세 조회({@link LeagueDetailUseCase})에 계산 시각({@code asOf})을
 * 붙이고, 구독자마다 다른 {@code me}는 뺀다.
 */
@Component
@RequiredArgsConstructor
class LeagueDetailMessageQuery {

    private final LeagueDetailUseCase leagueDetailUseCase;
    private final Clock clock;

    /**
     * 리그 상세 메시지를 만든다. 계산 시각은 조회 직전 시각이다.
     *
     * @param leagueId 리그 ID
     * @return 리그 상세와 계산 시각
     */
    public LeagueDetailResponse getDetail(long leagueId) {
        LocalDateTime asOf = LocalDateTime.now(clock);
        LeagueDetail detail = leagueDetailUseCase.getDetailInLeague(leagueId);
        return new LeagueDetailResponse(detail.leagueId(), detail.name(), detail.inviteCode(),
                detail.waitingRoomExpiresAt(), detail.startable(),
                toTradingDay(detail.tradingDay()),
                detail.players().stream().map(LeagueDetailMessageQuery::toPlayer).toList(),
                asOf);
    }

    private static TradingDay toTradingDay(LeagueTradingDay tradingDay) {
        if (tradingDay == null) {
            return null;
        }
        return new TradingDay(tradingDay.dayNumber(), tradingDay.totalDays(),
                tradingDay.dayEndsAt(), tradingDay.startedAt(), tradingDay.endsAt());
    }

    private static Player toPlayer(DetailPlayer player) {
        LeagueDetail.PlayerStock stock = player.stock();
        return new Player(player.playerId(), player.name(), player.host(), player.listed(),
                player.joinedAt(), player.listedAt(),
                stock == null ? null : new Stock(stock.stockId(), stock.name(),
                        stock.description()));
    }
}
