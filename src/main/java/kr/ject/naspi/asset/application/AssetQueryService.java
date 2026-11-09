package kr.ject.naspi.asset.application;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ject.naspi.asset.application.PlayerAsset.HoldingAsset;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.AccountRepository;
import kr.ject.naspi.asset.domain.AssetValuation;
import kr.ject.naspi.asset.domain.AssetValuation.HoldingValuation;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.asset.domain.HoldingRepository;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.market.application.LeagueStock;
import kr.ject.naspi.market.application.StockQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여자 본인의 자산 조회 (AST-02).
 *
 * <p>현금·보유 수량·예약은 asset이 저장한 값을 쓰고, 현재가·종목 이름·상장자는 조회할 때마다
 * market에서 리그 단위로 한 번에 가져온다. 평가액과 총자산은 저장하지 않고 매번 계산한다.
 *
 * <p>대기 중인 주문은 order가 별도 API로 제공한다 (ORD-08). 이 서비스는 order를 참조하지 않는다.
 *
 * <p>TODO: 로그인한 회원 본인의 자산만 조회할 수 있어야 한다. league의 Player 구현 후 연동한다.
 * <ul>
 *   <li>API는 경로로 {@code playerId}를 받지 않는다 ({@code GET /api/leagues/{leagueId}/assets/me}).
 *       {@code @AuthenticationPrincipal LoginMember}의 회원 ID로 league application에서 해당 리그의
 *       플레이어 ID를 조회해 이 서비스에 넘긴다.</li>
 *   <li>해당 리그의 플레이어가 아닌 회원은 거절한다 (오류 코드는 league 대역에 추가).</li>
 *   <li>웹소켓 내 자산 토픽 구독 권한({@code AssetTopicAuthorizer})도 같은 조회로 교체한다.</li>
 * </ul>
 * 지금은 호출하는 쪽이 검증을 마친 {@code playerId}를 넘긴다고 가정한다.
 */
@Service
@RequiredArgsConstructor
public class AssetQueryService {

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final StockQueryService stockQueryService;

    /**
     * 참여자의 현금, 보유 주식, 예약 자산, 주문 가능 자산과 현재가 기준 평가액·평가 손익·수익률,
     * 총자산과 총자산 평가 손익을 조회한다.
     *
     * @param leagueId 리그 ID
     * @param playerId 플레이어 ID
     * @return 자산 조회 결과
     * @throws NaspiBizException 참여자의 자산이 없는 경우 ({@link ErrorCode#ASSET_NOT_FOUND})
     * @throws NaspiRuntimeException 보유 종목이 리그에 없거나 현재가가 정해지지 않은 경우
     */
    @Transactional(readOnly = true)
    public PlayerAsset getPlayerAsset(long leagueId, long playerId) {
        Account account = accountRepository.findByLeagueIdAndPlayerId(leagueId, playerId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.ASSET_NOT_FOUND));
        List<Holding> holdings = holdingRepository.findAllByLeagueIdAndPlayerId(leagueId, playerId);
        Map<Long, LeagueStock> stocks = stockQueryService.findAllByLeagueId(leagueId).stream()
                .collect(Collectors.toMap(LeagueStock::stockId, Function.identity()));

        AssetValuation valuation = AssetValuation.evaluate(account, holdings,
                currentPrices(leagueId, holdings, stocks), treasuryStockId(playerId, stocks));
        return toPlayerAsset(valuation, stocks);
    }

    private static Map<Long, Long> currentPrices(long leagueId, List<Holding> holdings,
            Map<Long, LeagueStock> stocks) {
        return holdings.stream()
                .map(Holding::getStockId)
                .distinct()
                .collect(Collectors.toMap(Function.identity(),
                        stockId -> currentPriceOf(leagueId, stocks.get(stockId), stockId)));
    }

    private static long currentPriceOf(long leagueId, LeagueStock stock, long stockId) {
        if (stock == null) {
            throw new NaspiRuntimeException(
                    "보유 종목이 리그에 없습니다: leagueId=" + leagueId + ", stockId=" + stockId);
        }
        if (stock.currentPrice() == null) {
            // 리그 시작 시 시초가가 적용되므로 (MKT-04) 자산이 있는데 현재가가 없으면 데이터 오류다.
            throw new NaspiRuntimeException(
                    "현재가가 정해지지 않은 종목입니다: leagueId=" + leagueId + ", stockId=" + stockId);
        }
        return stock.currentPrice();
    }

    private static Long treasuryStockId(long playerId, Map<Long, LeagueStock> stocks) {
        return stocks.values().stream()
                .filter(stock -> stock.listerPlayerId() == playerId)
                .map(LeagueStock::stockId)
                .findFirst()
                .orElse(null);
    }

    private static PlayerAsset toPlayerAsset(AssetValuation valuation,
            Map<Long, LeagueStock> stocks) {
        List<HoldingAsset> holdings = valuation.holdings().stream()
                .map(holding -> toHoldingAsset(holding, stocks.get(holding.stockId())))
                .toList();
        return new PlayerAsset(valuation.playerId(), valuation.deposit(),
                valuation.reservedDeposit(), valuation.orderableDeposit(),
                valuation.stockValuation(), valuation.totalAsset(), valuation.totalProfit(),
                holdings);
    }

    private static HoldingAsset toHoldingAsset(HoldingValuation holding, LeagueStock stock) {
        return new HoldingAsset(holding.stockId(), stock.name(), holding.treasuryStock(),
                holding.quantity(), holding.reservedQuantity(), holding.orderableQuantity(),
                holding.evaluationPrice(), holding.valuation(), holding.profit(),
                holding.returnRate());
    }
}
