package kr.ject.naspi.league.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import kr.ject.naspi.asset.application.AssetQueryService;
import kr.ject.naspi.asset.application.PlayerTotalAsset;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.domain.DailyAssetSnapshot;
import kr.ject.naspi.league.domain.DailyAssetSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래일별 자산 집계 (LEA-18·INT-06). 거래일이 끝날 때마다 참여자별 현금·보유 주식 평가금액·총자산을 저장한다.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 결과", asset.md "총자산 집계"):
 * <ul>
 *   <li>각 거래일 종료 시점의 전체 현금과 종목별 보유 수량을 기준으로 집계하고, 보유 주식은 해당 거래일의 종가로
 *       평가한다. 마지막 거래일은 종료 평가 가격으로 평가한 최종 총자산으로 집계한다.</li>
 *   <li>{@code 총자산 = 현금 + Σ(각 종목 보유 수량 × 평가 가격)}, 자사주 포함. 계산은 asset 총자산
 *       집계(AST-08)가 맡는다. 평가 가격이 원 단위 정수라 원 미만이 생기지 않는다.</li>
 * </ul>
 *
 * <p>탈퇴한 참여자도 저장하고 조회하는 쪽이 거른다. 1~6일차는 거래일 전환({@link TradingDayChangeUseCase})이
 * market 확정 종가로 {@link #record}를, 7일차는 리그 결과 산정({@link LeagueResultUseCase})이 이미 집계한 최종
 * 결과로 {@link #save}를 호출한다. 하위 모듈 asset을 호출하므로 UseCase다. 리그 락을 잡은 거래일 전환·리그 종료
 * 트랜잭션 안에서만 호출한다.
 */
@Service
@RequiredArgsConstructor
public class DailyAssetSnapshotUseCase {

    private final DailyAssetSnapshotRepository dailyAssetSnapshotRepository;
    private final AssetQueryService assetQueryService;

    /**
     * 마감하는 거래일의 참여자별 자산을 종가로 집계해 저장한다 (1~6일차).
     *
     * @param leagueId 리그 ID
     * @param tradingDay 마감하는 거래일 번호
     * @param closePrices 종목 ID별 확정 종가
     * @throws IllegalStateException 그 거래일의 집계가 이미 있는 경우
     * @throws NaspiRuntimeException 보유 종목의 종가가 없는 경우
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(long leagueId, int tradingDay, Map<Long, Long> closePrices) {
        save(leagueId, tradingDay, assetQueryService.aggregateTotalAssets(leagueId, closePrices));
    }

    /**
     * 이미 집계한 참여자별 총자산을 거래일 자산 집계로 저장한다 (7일차는 리그 결과 산정의 집계). 탈퇴자도
     * 저장한다. 집계 결과가 없으면(자산이 배분되지 않은 리그) 저장하지 않는다.
     *
     * @param leagueId 리그 ID
     * @param tradingDay 거래일 번호
     * @param totals 참여자별 총자산 집계
     * @throws IllegalStateException 그 거래일의 집계가 이미 있는 경우
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(long leagueId, int tradingDay, Collection<PlayerTotalAsset> totals) {
        if (dailyAssetSnapshotRepository.existsByLeagueIdAndTradingDay(leagueId, tradingDay)) {
            throw new IllegalStateException("거래일 자산 집계가 이미 있습니다. leagueId=" + leagueId
                    + ", tradingDay=" + tradingDay);
        }
        if (totals.isEmpty()) {
            return;
        }
        List<DailyAssetSnapshot> snapshots = totals.stream()
                .map(total -> DailyAssetSnapshot.of(leagueId, tradingDay, total.playerId(),
                        total.deposit(), total.stockValuation(), total.totalAsset()))
                .toList();
        dailyAssetSnapshotRepository.saveAll(snapshots);
    }
}
