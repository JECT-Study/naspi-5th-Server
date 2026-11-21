package kr.ject.naspi.order.application;

import kr.ject.naspi.asset.application.AssetReservationService;
import kr.ject.naspi.asset.application.AssetSettlementService;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.application.StockExecutionService;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.market.infra.LocalStockSnapshotCache;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 다른 모듈의 테스트가 미체결 주문 종료({@link OpenOrderClosingService})를 쓸 때 필요한 빈 묶음.
 * package-private인 {@code OrderExecutionSupport}를 다른 패키지의 테스트에서 직접 {@code @Import}할 수
 * 없어서 둔다. 이 설정을 쓰는 테스트는 {@code MemberService}를 목으로 대체해야 한다.
 */
@TestConfiguration
@Import({OpenOrderClosingService.class, OrderExecutionSupport.class,
        AssetReservationService.class, AssetSettlementService.class, StockExecutionService.class,
        StockQueryService.class, LocalStockSnapshotCache.class, LeagueQueryService.class})
public class OrderClosingTestConfig {
}
