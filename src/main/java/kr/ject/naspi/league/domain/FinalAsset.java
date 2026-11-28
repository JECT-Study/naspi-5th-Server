package kr.ject.naspi.league.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 리그 종료 시 참여자 한 명의 최종 자산 (LEA-13). 금액은 원 단위.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 결과"):
 * {@code 최종 총자산 = 최종 현금 + Σ(각 종목 보유 수량 × 해당 종목 종료 평가 가격)},
 * {@code 최종 손익 = 최종 총자산 − 1억 원}, {@code 최종 수익률 = 최종 손익 ÷ 1억 원 × 100}
 * (소수점 둘째 자리에서 반올림, 음수는 절댓값 기준). 계산은 asset 총자산 집계(AST-08)가 맡고, 이 값은 그
 * 결과를 옮겨 담는다.
 *
 * @param playerId 플레이어 ID
 * @param cash 최종 현금 (예약 해제 후 전체 현금)
 * @param totalAsset 최종 총자산 (자사주 포함)
 * @param profit 최종 손익
 * @param returnRate 최종 수익률(%). 소수점 첫째 자리까지
 * @param holdings 보유 수량이 있는 종목. 종목 ID 순
 */
public record FinalAsset(long playerId, long cash, long totalAsset, long profit,
        BigDecimal returnRate, List<FinalHolding> holdings) {

    /**
     * 값을 검증하고 목록을 복사해 변경할 수 없게 만든다.
     *
     * @throws IllegalArgumentException 현금이나 총자산이 음수인 경우
     */
    public FinalAsset {
        Objects.requireNonNull(returnRate, "returnRate");
        holdings = List.copyOf(holdings);
        if (cash < 0) {
            throw new IllegalArgumentException("최종 현금은 음수일 수 없습니다. cash=" + cash);
        }
        if (totalAsset < 0) {
            throw new IllegalArgumentException(
                    "최종 총자산은 음수일 수 없습니다. totalAsset=" + totalAsset);
        }
    }
}
