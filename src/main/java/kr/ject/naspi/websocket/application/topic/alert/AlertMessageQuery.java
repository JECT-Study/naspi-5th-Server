package kr.ject.naspi.websocket.application.topic.alert;

import kr.ject.naspi.contents.application.NewsGenerationCompleted;
import kr.ject.naspi.contents.application.NewsGenerationType;
import kr.ject.naspi.league.application.TradingDayChanged;
import kr.ject.naspi.market.application.LeagueStock;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.order.application.TradeExecuted;
import kr.ject.naspi.websocket.api.response.alert.AlertResponse;
import kr.ject.naspi.websocket.api.response.alert.AlertType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 알림 메시지 조회. 문구와 이동 경로는 정책(docs/policies/v1/domain/order.md "주문 알림")을 따르고, 종목 이름은
 * market에서 조회한다.
 */
@Component
@RequiredArgsConstructor
class AlertMessageQuery {

    /** 거래일 갱신으로 대기 주문이 만료된 플레이어의 알림 문구. */
    static final String ORDER_EXPIRED_MESSAGE = "새 거래일이 시작돼 대기 중인 주문이 취소됐어요.";

    /** 거래일 갱신 시 만료된 주문이 없는 플레이어의 알림 문구. */
    static final String TRADING_DAY_CHANGED_MESSAGE = "새 거래일이 시작됐어요.";

    /** 제보 기반 뉴스가 게시됐을 때의 알림 문구. */
    static final String REPORT_NEWS_PUBLISHED_MESSAGE = "제보를 바탕으로 뉴스가 게시되었어요.";

    /** 제보 내용이 부족해 뉴스가 생성되지 않았을 때의 알림 문구 (두 줄). */
    static final String REPORT_NEWS_SKIPPED_MESSAGE =
            "제보 내용이 부족해 뉴스를 생성하지 못했어요.\n내용을 보완한 뒤 다시 시도해 주세요.";

    /** 공시 기반 뉴스가 게시됐을 때의 알림 문구. */
    static final String DISCLOSURE_NEWS_PUBLISHED_MESSAGE = "공시를 바탕으로 뉴스가 게시되었어요.";

    /** 공시 내용이 부족해 뉴스가 생성되지 않았을 때의 알림 문구. */
    static final String DISCLOSURE_NEWS_SKIPPED_MESSAGE = "공시 내용이 부족해 뉴스를 생성하지 못했어요.";

    /** 뉴스 생성이 실패했을 때의 알림 문구 (공시·제보 공통). */
    static final String NEWS_FAILED_MESSAGE = "뉴스를 생성하지 못했어요. 다시 시도해 주세요.";

    private final StockQueryService stockQueryService;

    /**
     * 대기 주문 체결 알림을 만든다.
     *
     * @param event 체결 이벤트
     * @return {@code {종목 이름} {매수|매도} 주문이 체결됐어요.}, 이동 경로는 종목 화면
     * @throws IllegalStateException 리그에 체결 종목이 없는 경우
     */
    public AlertResponse tradeExecuted(TradeExecuted event) {
        String stockName = stockQueryService.findAllByLeagueId(event.leagueId()).stream()
                .filter(stock -> stock.stockId() == event.stockId())
                .map(LeagueStock::name)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("리그에 체결 종목이 없습니다. leagueId="
                        + event.leagueId() + ", stockId=" + event.stockId()));
        String side = event.buyResting() ? "매수" : "매도";
        return new AlertResponse(AlertType.TRADE_EXECUTED,
                stockName + " " + side + " 주문이 체결됐어요.",
                "/leagues/" + event.leagueId() + "/stocks/" + event.stockId(), null, null);
    }

    /**
     * 뉴스 생성 결과 알림을 만든다. 이동 경로는 없다.
     *
     * @param event 뉴스 생성 결과 이벤트
     * @return 결과에 따라 {@link AlertType#NEWS_PUBLISHED}, {@link AlertType#NEWS_SKIPPED},
     *     {@link AlertType#NEWS_FAILED}. 뉴스 유형과 뉴스 ID를 담는다
     */
    public AlertResponse newsGenerationCompleted(NewsGenerationCompleted event) {
        boolean report = event.type() == NewsGenerationType.REPORT;
        AlertType alertType;
        String message;
        switch (event.outcome()) {
            case PUBLISHED -> {
                alertType = AlertType.NEWS_PUBLISHED;
                message = report ? REPORT_NEWS_PUBLISHED_MESSAGE
                        : DISCLOSURE_NEWS_PUBLISHED_MESSAGE;
            }
            case SKIPPED -> {
                alertType = AlertType.NEWS_SKIPPED;
                message = report ? REPORT_NEWS_SKIPPED_MESSAGE
                        : DISCLOSURE_NEWS_SKIPPED_MESSAGE;
            }
            default -> {
                alertType = AlertType.NEWS_FAILED;
                message = NEWS_FAILED_MESSAGE;
            }
        }
        return new AlertResponse(alertType, message, null, event.type().name(), event.newsId());
    }

    /**
     * 거래일 갱신 알림을 만든다. 이동 경로는 없다.
     *
     * @param event 거래일 전환 이벤트
     * @param playerId 받을 플레이어 ID
     * @return 주문이 만료된 플레이어면 {@link AlertType#ORDER_EXPIRED}, 아니면
     *     {@link AlertType#TRADING_DAY_CHANGED}
     */
    public AlertResponse tradingDayChanged(TradingDayChanged event, long playerId) {
        if (event.expiredPlayerIds().contains(playerId)) {
            return new AlertResponse(AlertType.ORDER_EXPIRED, ORDER_EXPIRED_MESSAGE, null, null,
                    null);
        }
        return new AlertResponse(AlertType.TRADING_DAY_CHANGED, TRADING_DAY_CHANGED_MESSAGE, null,
                null, null);
    }
}
