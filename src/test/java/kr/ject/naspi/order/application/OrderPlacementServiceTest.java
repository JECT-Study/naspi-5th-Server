package kr.ject.naspi.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Optional;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderPlacementServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final long LEAGUE_ID = 3L;
    private static final long PLAYER_ID = 21L;

    @Mock
    private LeagueQueryService leagueQueryService;

    @Mock
    private LimitOrderService limitOrderService;

    @InjectMocks
    private OrderPlacementService service;

    @Test
    @DisplayName("주문하는 플레이어는 로그인 회원이 리그에서 가진 플레이어로 정한다")
    void usesPlayerOfLoginMember() {
        PlacedOrder placed = new PlacedOrder(11L, OrderStatus.PENDING, 10L, 10L);
        given(leagueQueryService.findPlayerId(MEMBER_ID, LEAGUE_ID))
                .willReturn(Optional.of(PLAYER_ID));
        given(limitOrderService.placeLimitOrder(
                new PlaceLimitOrder(LEAGUE_ID, PLAYER_ID, 5L, OrderSide.BUY, 10_000L, 10L),
                1, 10_000L)).willReturn(placed);

        PlacedOrder result = service.placeLimitOrder(
                MEMBER_ID, LEAGUE_ID, 5L, OrderSide.BUY, 10_000L, 10L);

        assertThat(result).isEqualTo(placed);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 주문하지 못하고 주문 처리도 시작하지 않는다")
    void rejectsMemberNotInLeague() {
        given(leagueQueryService.findPlayerId(MEMBER_ID, LEAGUE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.placeLimitOrder(
                MEMBER_ID, LEAGUE_ID, 5L, OrderSide.BUY, 10_000L, 10L))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        then(limitOrderService).should(never())
                .placeLimitOrder(any(), anyInt(), anyLong());
    }
}
