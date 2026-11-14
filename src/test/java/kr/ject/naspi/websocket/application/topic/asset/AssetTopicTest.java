package kr.ject.naspi.websocket.application.topic.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.websocket.api.response.asset.AssetResponse;
import kr.ject.naspi.websocket.api.response.asset.AssetResponse.Holding;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssetTopicTest {

    private final TopicSubscriptions subscriptions = mock(TopicSubscriptions.class);
    private final AssetTopic topic = new AssetTopic(subscriptions);
    private final LeagueQueryService leagueQueryService = mock(LeagueQueryService.class);
    private final AssetTopicAuthorizer authorizer = new AssetTopicAuthorizer(leagueQueryService);

    @Test
    @DisplayName("구독 중인 내 자산 토픽에만 메시지를 만든다")
    void producesForSubscribedAssetTopics() {
        when(subscriptions.subscribedDestinations("/topic/leagues/")).thenReturn(Set.of(
                "/topic/leagues/1/players/7/assets",
                "/topic/leagues/1/players/7/alerts"));

        assertThat(topic.produce()).containsExactly(new TopicMessage(
                "/topic/leagues/1/players/7/assets", "ASSET", AssetTopic.DUMMY));
    }

    @Test
    @DisplayName("평가금은 예수금(예약금 포함 전체 현금)과 보유 주식 평가액(현재가 × 수량)의 합이다")
    void dummyTotalsAreConsistent() {
        AssetResponse asset = AssetTopic.DUMMY;
        long stockValuation = asset.holdings().stream().mapToLong(Holding::valuation).sum();

        assertThat(asset.holdings()).allSatisfy(holding -> assertThat(holding.valuation())
                .isEqualTo(holding.currentPrice() * holding.quantity()));
        assertThat(asset.stockValuation()).isEqualTo(stockValuation);
        assertThat(asset.totalAsset()).isEqualTo(asset.deposit() + stockValuation);
    }

    @Test
    @DisplayName("예약금은 예수금 중 미체결 주문에 묶인 금액이라 예수금을 넘지 않는다")
    void reservedDepositIsPartOfDeposit() {
        AssetResponse asset = AssetTopic.DUMMY;

        assertThat(asset.reservedDeposit()).isPositive().isLessThanOrEqualTo(asset.deposit());
    }

    @Test
    @DisplayName("내 자산은 로그인 회원 본인의 리그 플레이어 경로만 구독할 수 있다")
    void onlyOwnerCanSubscribe() {
        LoginMember member = new LoginMember(7L);
        when(leagueQueryService.findPlayerId(7L, 1L)).thenReturn(Optional.of(30L));

        assertThat(authorizer.supports("/topic/leagues/1/players/30/assets")).isTrue();
        assertThat(authorizer.canSubscribe(member, "/topic/leagues/1/players/30/assets")).isTrue();
        assertThat(authorizer.canSubscribe(member, "/topic/leagues/1/players/31/assets")).isFalse();
        // 플레이어 ID가 아니라 회원 ID를 넣으면 거부된다
        assertThat(authorizer.canSubscribe(member, "/topic/leagues/1/players/7/assets")).isFalse();
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 내 자산 토픽을 구독할 수 없다")
    void outsiderCannotSubscribe() {
        LoginMember member = new LoginMember(7L);
        when(leagueQueryService.findPlayerId(7L, 2L)).thenReturn(Optional.empty());

        assertThat(authorizer.canSubscribe(member, "/topic/leagues/2/players/30/assets")).isFalse();
    }
}
