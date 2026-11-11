package kr.ject.naspi.market.api.leaguedetail;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.market.application.LeagueDetail;

/**
 * 리그 상세 정보.
 *
 * @param leagueId 리그 ID
 * @param startable 리그 시작 가능 여부 (참여자 3명 이상, 대기자 0명)
 * @param players 참여일시 순 플레이어 목록
 */
public record LeagueDetailResponse(long leagueId, boolean startable, List<PlayerResponse> players) {

    static LeagueDetailResponse from(LeagueDetail detail) {
        return new LeagueDetailResponse(detail.leagueId(), detail.startable(),
                detail.players().stream().map(PlayerResponse::from).toList());
    }

    /**
     * 리그 플레이어.
     *
     * @param playerId 플레이어 ID
     * @param name 회원 이름. 탈퇴한 회원이면 {@code null}
     * @param host 방장 여부
     * @param listed 상장 완료 여부 (true 참여자, false 대기자)
     * @param joinedAt 참여일시 (KST)
     * @param listedAt 상장일시 (KST). 대기자는 {@code null}
     * @param stock 상장한 종목. 대기자는 {@code null}
     */
    public record PlayerResponse(long playerId, String name, boolean host, boolean listed,
            LocalDateTime joinedAt, LocalDateTime listedAt, StockResponse stock) {

        static PlayerResponse from(LeagueDetail.DetailPlayer player) {
            LeagueDetail.PlayerStock stock = player.stock();
            StockResponse stockResponse = stock == null ? null
                    : new StockResponse(stock.stockId(), stock.name(), stock.description());
            return new PlayerResponse(player.playerId(), player.name(), player.host(),
                    player.listed(), player.joinedAt(), player.listedAt(), stockResponse);
        }
    }

    /**
     * 플레이어가 상장한 종목.
     *
     * @param stockId 종목 ID
     * @param name 종목 이름
     * @param description 종목 소개
     */
    public record StockResponse(long stockId, String name, String description) {
    }
}
