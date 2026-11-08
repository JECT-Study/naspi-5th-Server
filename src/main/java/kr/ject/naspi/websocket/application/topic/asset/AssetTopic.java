package kr.ject.naspi.websocket.application.topic.asset;

import java.util.List;
import java.util.regex.Pattern;
import kr.ject.naspi.websocket.api.response.asset.AssetResponse;
import kr.ject.naspi.websocket.api.response.asset.AssetResponse.Holding;
import kr.ject.naspi.websocket.application.PeriodicTopic;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 내 자산 토픽: {@code /topic/leagues/{leagueId}/players/{playerId}/assets}.
 *
 * <p>TODO: 실제 자산(현금·보유 주식·현재가) 조회로 교체. 지금은 구독 중인 토픽에 더미 응답을 주기적으로 보낸다.
 */
@Component
@RequiredArgsConstructor
public class AssetTopic implements PeriodicTopic {

    /** 구독 경로. {@code playerId}가 본인일 때만 구독. */
    static final Pattern DESTINATION =
            Pattern.compile("^/topic/leagues/(\\d+)/players/(\\d+)/assets$");

    /**
     * 더미 응답. 평가금 = 예수금 + 보유 주식 평가액 (시초가 기준 총자산 1억 원 근처).
     * 예약금은 12,500원 × 100주 매수 주문이 미체결인 상태.
     */
    static final AssetResponse DUMMY = dummy(52_000_000, 1_250_000, List.of(
            holding(1, "진영전자", 12_500, 3_000),
            holding(2, "민영건설", 12_500, 1_000),
            holding(3, "수진식품", 9_800, 500)));

    private static final String PREFIX = "/topic/leagues/";

    private final TopicSubscriptions subscriptions;

    @Override
    public String name() {
        return "assets";
    }

    @Override
    public String destinationPrefix() {
        return PREFIX;
    }

    @Override
    public List<TopicMessage> produce() {
        return subscriptions.subscribedDestinations(PREFIX).stream()
                .filter(destination -> DESTINATION.matcher(destination).matches())
                .sorted()
                .map(destination -> new TopicMessage(destination, AssetResponse.TYPE, DUMMY))
                .toList();
    }

    private static Holding holding(long stockId, String stockName, long price, long quantity) {
        return new Holding(stockId, stockName, price, quantity, price * quantity);
    }

    private static AssetResponse dummy(long deposit, long reservedDeposit,
            List<Holding> holdings) {
        long stockValuation = holdings.stream().mapToLong(Holding::valuation).sum();
        return new AssetResponse(deposit + stockValuation, deposit, reservedDeposit,
                stockValuation, holdings);
    }
}
