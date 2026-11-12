package kr.ject.naspi.asset.application;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 회원 본인의 자산 조회 (AST-02).
 *
 * <p>정책 (docs/policies/v1/domain/asset.md "자산 조회"): 로그인한 사용자는 참여 중인 리그에서 본인의 자산만
 * 조회할 수 있다. 플레이어 ID를 요청으로 받지 않고 로그인 회원 ID로 league에서 찾으므로, 찾은 플레이어가 곧
 * 본인이다. 확인을 마친 뒤 계산은 {@link AssetQueryService}가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class MyAssetQueryService {

    private final LeagueQueryService leagueQueryService;
    private final AssetQueryService assetQueryService;

    /**
     * 로그인 회원 본인의 리그 자산을 조회한다.
     *
     * @param memberId 로그인 회원 ID
     * @param leagueId 리그 ID
     * @return 자산 조회 결과
     * @throws NaspiBizException 리그에 입장하지 않은 회원인 경우 ({@link ErrorCode#FORBIDDEN}),
     *     자산이 없는 경우 ({@link ErrorCode#ASSET_NOT_FOUND})
     */
    @Transactional(readOnly = true)
    public PlayerAsset getMyAsset(long memberId, long leagueId) {
        long playerId = leagueQueryService.findPlayerId(memberId, leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.FORBIDDEN));
        return assetQueryService.getPlayerAsset(leagueId, playerId);
    }
}
