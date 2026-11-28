package kr.ject.naspi.league.application;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ject.naspi.asset.application.AssetQueryService;
import kr.ject.naspi.asset.application.PlayerTotalAsset;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.domain.FinalAsset;
import kr.ject.naspi.league.domain.FinalHolding;
import kr.ject.naspi.league.domain.LeagueResult;
import kr.ject.naspi.league.domain.LeagueResultHolding;
import kr.ject.naspi.league.domain.LeagueResultHoldingRepository;
import kr.ject.naspi.league.domain.LeagueResultPlayer;
import kr.ject.naspi.league.domain.LeagueResultPlayerRepository;
import kr.ject.naspi.league.domain.LeagueResultRepository;
import kr.ject.naspi.league.domain.LeagueResultStock;
import kr.ject.naspi.league.domain.LeagueResultStockRepository;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.PlayerRepository;
import kr.ject.naspi.league.domain.Ranked;
import kr.ject.naspi.league.domain.Ranking;
import kr.ject.naspi.league.domain.TradingSchedule;
import kr.ject.naspi.market.application.FinalEvaluationPriceUseCase;
import kr.ject.naspi.member.application.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 결과 산정 (LEA-13·INT-05). 종료 처리 중인 리그의 최종 총자산과 순위를 산정해 결과 스냅샷으로 저장한다.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 결과", member.md "회원 탈퇴"):
 * <ul>
 *   <li>{@code 최종 총자산 = 최종 현금 + Σ(각 종목 보유 수량 × 해당 종목 종료 평가 가격)}, 원 미만 절사. 종료 평가
 *       가격(MKT-07)이 이미 원 단위로 절사된 정수이고 수량도 정수라 원 미만이 생기지 않으므로 따로 절사하지
 *       않는다. 계산은 asset 총자산 집계(AST-08)가 맡는다.</li>
 *   <li>자사주를 포함한 최종 총자산 기준으로 순위를 매기며, 같으면 공동 순위이고 다음 순위는 동점자 수만큼
 *       건너뛴다. 공동 순위인 참여자는 상장 일시가 이른 순(같으면 플레이어 ID 순)으로 나열한다.</li>
 *   <li>최종 손익·수익률은 asset 총자산 집계가 자산 조회(4.4)와 같은 기준으로 계산한 값을 저장한다.</li>
 *   <li>종료 전 탈퇴자({@link Player#isWithdrawnBefore})는 순위·결과에서 제외한다. 그가 상장한 종목은 계속
 *       거래되므로 종목 평가는 저장한다.</li>
 * </ul>
 *
 * <p>최종 집계는 7일차 거래일별 자산 집계(LEA-18)로도 저장한다. 종료 전 탈퇴자를 포함한 집계 전체를
 * 저장하고, 조회하는 쪽이 거른다 ({@link DailyAssetSnapshotUseCase}).
 *
 * <p>하위 모듈 market(종료 평가 가격)·asset(총자산 집계)을 호출하므로 UseCase다. 리그 종료
 * ({@link LeagueEndUseCase})가 리그 락을 잡은 트랜잭션에서 미체결 취소 뒤, 종료 상태 전환 전에 호출한다.
 */
@Service
@RequiredArgsConstructor
public class LeagueResultUseCase {

    private final PlayerRepository playerRepository;
    private final LeagueResultRepository leagueResultRepository;
    private final LeagueResultStockRepository leagueResultStockRepository;
    private final LeagueResultPlayerRepository leagueResultPlayerRepository;
    private final LeagueResultHoldingRepository leagueResultHoldingRepository;
    private final FinalEvaluationPriceUseCase finalEvaluationPriceUseCase;
    private final AssetQueryService assetQueryService;
    private final MemberService memberService;
    private final DailyAssetSnapshotUseCase dailyAssetSnapshotUseCase;

    /**
     * 종료 처리 중인 리그의 최종 결과를 산정해 저장한다. 리그 락을 잡은 리그 종료 트랜잭션 안에서만 호출한다
     * (락 밖에서 호출하면 산정 중 체결이 생길 수 있다).
     *
     * <p>결과 대상 참여자가 없으면(전원 종료 전 탈퇴) 리그 결과와 종목 평가만 저장한다.
     *
     * @param leagueId 리그 ID
     * @param endsAt 리그 종료 시각. 이 시각 전에 탈퇴한 참여자를 제외한다
     * @param finalizedAt 결과 확정 일시 (종료 처리 실행 시각)
     * @throws IllegalStateException 리그 결과나 7일차 거래일 자산 집계가 이미 있는 경우
     * @throws NaspiRuntimeException 결과 대상 참여자의 자산이 없거나 집계할 수 없는 경우
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void calculate(long leagueId, LocalDateTime endsAt, LocalDateTime finalizedAt) {
        if (leagueResultRepository.existsByLeagueId(leagueId)) {
            throw new IllegalStateException("리그 결과가 이미 있습니다. leagueId=" + leagueId);
        }
        Map<Long, Long> prices = finalEvaluationPriceUseCase.getFinalEvaluationPrices(leagueId,
                TradingSchedule.TRADING_DAYS);
        List<PlayerTotalAsset> allTotals = assetQueryService.aggregateTotalAssets(leagueId, prices);
        // 7일차 거래일별 자산 집계는 종료 전 탈퇴자를 거르기 전 집계 전체로 저장한다
        dailyAssetSnapshotUseCase.save(leagueId, TradingSchedule.TRADING_DAYS, allTotals);
        Map<Long, PlayerTotalAsset> totals = allTotals.stream()
                .collect(Collectors.toMap(PlayerTotalAsset::playerId, Function.identity()));

        Map<Long, Player> targets = playerRepository.findAllByLeagueId(leagueId).stream()
                .filter(Player::isListed)
                .filter(player -> !player.isWithdrawnBefore(endsAt))
                .collect(Collectors.toMap(Player::getId, Function.identity()));
        List<FinalAsset> finalAssets = targets.keySet().stream()
                .map(playerId -> toFinalAsset(leagueId, playerId, totals.get(playerId)))
                .toList();
        // 공동 순위는 상장 일시가 이른 참여자부터, 같으면 플레이어 ID 순으로 나열한다
        Comparator<FinalAsset> tieOrder = Comparator
                .comparing((FinalAsset asset) -> targets.get(asset.playerId()).getListedAt())
                .thenComparingLong(FinalAsset::playerId);
        List<Ranked<FinalAsset>> ranking = Ranking.rank(finalAssets, FinalAsset::totalAsset,
                tieOrder);

        LeagueResult result = leagueResultRepository.save(LeagueResult.of(leagueId, finalizedAt));
        leagueResultStockRepository.saveAll(prices.entrySet().stream()
                .map(price -> LeagueResultStock.of(result.getId(), leagueId, price.getKey(),
                        price.getValue()))
                .toList());
        Map<Long, String> names = memberService.getNames(targets.values().stream()
                .map(Player::getMemberId)
                .filter(Objects::nonNull)
                .toList());
        for (Ranked<FinalAsset> ranked : ranking) {
            FinalAsset asset = ranked.entry();
            LeagueResultPlayer resultPlayer = leagueResultPlayerRepository.save(
                    LeagueResultPlayer.of(result.getId(), leagueId, ranked,
                            displayNameOf(targets.get(asset.playerId()), names)));
            leagueResultHoldingRepository.saveAll(asset.holdings().stream()
                    .map(holding -> LeagueResultHolding.of(resultPlayer.getId(), leagueId,
                            holding))
                    .toList());
        }
    }

    private static FinalAsset toFinalAsset(long leagueId, long playerId, PlayerTotalAsset total) {
        if (total == null) {
            // 리그 시작 때 참여자 전원에게 자산이 배분되므로 없으면 데이터 오류다
            throw new NaspiRuntimeException("결과 대상 참여자의 자산이 없습니다: leagueId=" + leagueId
                    + ", playerId=" + playerId);
        }
        List<FinalHolding> holdings = total.holdings().stream()
                .map(holding -> new FinalHolding(holding.stockId(), holding.quantity(),
                        holding.valuation()))
                .toList();
        return new FinalAsset(total.playerId(), total.deposit(), total.totalAsset(),
                total.totalProfit(), total.totalReturnRate(), holdings);
    }

    /**
     * 리그 종료 시점의 표시 이름. 종료 시각 이후·종료 처리 전에 탈퇴해 회원 정보가 이미 삭제된 경우에만
     * '탈퇴한 사용자'로 저장한다.
     */
    private static String displayNameOf(Player player, Map<Long, String> names) {
        String name = player.getMemberId() == null ? null : names.get(player.getMemberId());
        if (name != null) {
            return name;
        }
        if (player.isWithdrawn()) {
            return Player.WITHDRAWN_DISPLAY_NAME;
        }
        throw new NaspiRuntimeException("결과 대상 참여자의 회원 이름이 없습니다: playerId="
                + player.getId() + ", memberId=" + player.getMemberId());
    }
}
