package kr.ject.naspi.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MyAssetQueryServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final long LEAGUE_ID = 1L;
    private static final long PLAYER_ID = 30L;

    private final LeagueQueryService leagueQueryService = mock(LeagueQueryService.class);
    private final AssetQueryService assetQueryService = mock(AssetQueryService.class);
    private final MyAssetQueryService service =
            new MyAssetQueryService(leagueQueryService, assetQueryService);

    @Test
    @DisplayName("로그인한 회원은 참여 중인 리그에서 본인 플레이어의 자산을 조회한다")
    void getMyAsset() {
        PlayerAsset asset = new PlayerAsset(PLAYER_ID, 50_000_000, 0, 50_000_000, 50_000_000,
                100_000_000, 0, new BigDecimal("0.0"), List.of());
        given(leagueQueryService.findPlayerId(MEMBER_ID, LEAGUE_ID))
                .willReturn(Optional.of(PLAYER_ID));
        given(assetQueryService.getPlayerAsset(LEAGUE_ID, PLAYER_ID)).willReturn(asset);

        assertThat(service.getMyAsset(MEMBER_ID, LEAGUE_ID)).isEqualTo(asset);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 자산을 조회할 수 없다 (본인의 자산만 조회 가능)")
    void outsiderCannotGetAsset() {
        given(leagueQueryService.findPlayerId(MEMBER_ID, LEAGUE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyAsset(MEMBER_ID, LEAGUE_ID))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        then(assetQueryService).should(never()).getPlayerAsset(anyLong(), anyLong());
    }
}
