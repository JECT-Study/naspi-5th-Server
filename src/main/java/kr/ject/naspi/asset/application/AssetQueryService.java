package kr.ject.naspi.asset.application;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p>본인 확인은 하지 않는다. 호출하는 쪽이 본인 확인을 마친 {@code playerId}를 넘긴다.
 * <ul>
 *   <li>REST: {@link MyAssetQueryService}가 로그인 회원 ID로 플레이어를 찾아 넘긴다.</li>
 *   <li>웹소켓: 구독할 때 {@code AssetTopicAuthorizer}가 본인 플레이어인지 확인하고, 허용된 구독의
 *       {@code playerId}만 {@link #getPlayerAssets}로 넘긴다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AssetQueryService {

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final StockQueryService stockQueryService;

    /**
     * 참여자의 현금, 보유 주식, 예약 자산, 주문 가능 자산과 현재가 기준 평가액·평가 손익·수익률,
     * 총자산과 총자산 평가 손익·수익률을 조회한다.
     *
     * @param leagueId 리그 ID
     * @param playerId 플레이어 ID
     * @return 자산 조회 결과
     * @throws NaspiBizException 참여자의 자산이 없는 경우 ({@link ErrorCode#ASSET_NOT_FOUND})
     * @throws NaspiRuntimeException 보유 종목이 리그에 없거나 현재가가 정해지지 않은 경우
     */
    @Transactional(readOnly = true)
    PlayerAsset getPlayerAsset(long leagueId, long playerId) {
        Account account = accountRepository.findByLeagueIdAndPlayerId(leagueId, playerId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.ASSET_NOT_FOUND));
        List<Holding> holdings = holdingRepository.findAllByLeagueIdAndPlayerId(leagueId, playerId);
        return evaluate(leagueId, account, holdings, leagueStocks(leagueId));
    }

    /**
     * 같은 리그 참여자 여러 명의 자산을 한 번에 조회한다. 웹소켓 주기 발행처럼 한 리그의 여러 구독자에게
     * 보낼 때 쓴다. 넘기는 플레이어는 구독할 때 본인 확인을 마친 플레이어여야 한다.
     *
     * <p>리그의 종목·현재가는 한 번만 조회하고, 계좌와 보유 주식도 플레이어별이 아니라 한 번에 조회한다.
     * 초기 자산이 배분되지 않은 플레이어(리그 시작 전 등)는 오류 없이 결과에서 빠진다.
     *
     * @param leagueId 리그 ID
     * @param playerIds 플레이어 ID 목록. 중복은 무시한다
     * @return 자산이 있는 플레이어의 자산 조회 결과. 플레이어 ID 순
     * @throws NaspiRuntimeException 보유 종목이 리그에 없거나 현재가가 정해지지 않은 경우.
     *     리그 종목 데이터 문제라 같은 리그의 모든 플레이어에 해당한다
     */
    @Transactional(readOnly = true)
    public List<PlayerAsset> getPlayerAssets(long leagueId, Collection<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = Set.copyOf(playerIds);
        List<Account> accounts = accountRepository.findAllByLeagueIdAndPlayerIdIn(leagueId, ids);
        if (accounts.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Holding>> holdingsByPlayer = holdingRepository
                .findAllByLeagueIdAndPlayerIdIn(leagueId, ids).stream()
                .collect(Collectors.groupingBy(Holding::getPlayerId));
        Map<Long, LeagueStock> stocks = leagueStocks(leagueId);

        return accounts.stream()
                .sorted(Comparator.comparing(Account::getPlayerId))
                .map(account -> evaluate(leagueId, account,
                        holdingsByPlayer.getOrDefault(account.getPlayerId(), List.of()), stocks))
                .toList();
    }

    /**
     * 리그 전체 참여자의 총자산을 호출자가 지정한 평가 가격으로 집계한다 (AST-08). 진행 중 순위·리그 결과·
     * 거래일별 집계처럼 리그가 호출한다. 현재가·종가·종료 평가 가격 중 무엇을 쓸지는 호출하는 쪽이 정한다.
     *
     * <p>{@code 총자산 = 현금 + Σ(각 종목 보유 수량 × 평가 가격)}이며 자사주를 포함한다. 평가 가격이 원 단위
     * 정수라 원 미만이 생기지 않는다. 탈퇴한 참여자도 집계하고 {@link PlayerTotalAsset#withdrawn()}으로 표시한다.
     * 보유 수량이 0인 종목의 가격은 없어도 되고, 맵에 남는 가격은 무시한다.
     *
     * @param leagueId 리그 ID
     * @param evaluationPrices 종목 ID별 평가 가격 (1주당 원)
     * @return 자산이 배분된 참여자의 총자산. 플레이어 ID 순. 자산이 배분되지 않은 리그는 빈 목록
     * @throws NaspiRuntimeException 보유 종목의 평가 가격이 없거나 음수인 경우 (호출자 계약 위반)
     */
    @Transactional(readOnly = true)
    public List<PlayerTotalAsset> aggregateTotalAssets(long leagueId,
            Map<Long, Long> evaluationPrices) {
        List<Account> accounts = accountRepository.findAllByLeagueId(leagueId);
        if (accounts.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Holding>> holdingsByPlayer = holdingRepository.findAllByLeagueId(leagueId)
                .stream()
                .collect(Collectors.groupingBy(Holding::getPlayerId));
        Map<Long, LeagueStock> stocks = leagueStocks(leagueId);

        return accounts.stream()
                .sorted(Comparator.comparing(Account::getPlayerId))
                .map(account -> toPlayerTotalAsset(account,
                        holdingsByPlayer.getOrDefault(account.getPlayerId(), List.of()),
                        evaluationPrices, treasuryStockId(account.getPlayerId(), stocks)))
                .toList();
    }

    private static PlayerTotalAsset toPlayerTotalAsset(Account account, List<Holding> holdings,
            Map<Long, Long> evaluationPrices, Long treasuryStockId) {
        AssetValuation valuation;
        try {
            valuation = AssetValuation.evaluate(account, holdings, evaluationPrices,
                    treasuryStockId);
        } catch (IllegalArgumentException e) {
            throw new NaspiRuntimeException("총자산을 집계할 수 없습니다: playerId="
                    + account.getPlayerId() + ", " + e.getMessage(), e);
        }
        List<PlayerTotalAsset.HoldingTotal> holdingTotals = valuation.holdings().stream()
                .map(holding -> new PlayerTotalAsset.HoldingTotal(holding.stockId(),
                        holding.treasuryStock(), holding.quantity(), holding.evaluationPrice(),
                        holding.valuation()))
                .toList();
        return new PlayerTotalAsset(valuation.playerId(), valuation.deposit(),
                valuation.stockValuation(), valuation.totalAsset(), valuation.totalProfit(),
                valuation.totalReturnRate(), account.isWithdrawn(),
                holdingTotals);
    }

    private Map<Long, LeagueStock> leagueStocks(long leagueId) {
        return stockQueryService.findAllByLeagueId(leagueId).stream()
                .collect(Collectors.toMap(LeagueStock::stockId, Function.identity()));
    }

    private static PlayerAsset evaluate(long leagueId, Account account, List<Holding> holdings,
            Map<Long, LeagueStock> stocks) {
        AssetValuation valuation = AssetValuation.evaluate(account, holdings,
                currentPrices(leagueId, holdings, stocks),
                treasuryStockId(account.getPlayerId(), stocks));
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
                valuation.totalReturnRate(), holdings);
    }

    private static HoldingAsset toHoldingAsset(HoldingValuation holding, LeagueStock stock) {
        return new HoldingAsset(holding.stockId(), stock.name(), holding.treasuryStock(),
                holding.quantity(), holding.reservedQuantity(), holding.orderableQuantity(),
                holding.evaluationPrice(), holding.averagePrice(), holding.valuation(),
                holding.profit(), holding.returnRate());
    }
}
