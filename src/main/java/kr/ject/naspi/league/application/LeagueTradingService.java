package kr.ject.naspi.league.application;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 단위 거래 직렬화. order가 주문을 접수·취소·만료할 때 가장 먼저 호출한다.
 *
 * <p>리그 행에 비관적 쓰기 락을 걸어 같은 리그의 주문 처리를 한 번에 하나씩 진행한다. 여러 서버가
 * 떠 있어도 DB 락이므로 동작한다. 락은 호출한 트랜잭션이 끝날 때 풀리고, 호출한 쪽은 락을 잡은 뒤에
 * 호가창을 조회하고 주문을 저장해야 주문 {@code id} 순서가 처리 순서와 같아진다. 락 순서는 리그 락,
 * asset 락(예약·정산) 순이다 (docs/issues/v1/order.md ORD-03).
 */
@Service
@RequiredArgsConstructor
public class LeagueTradingService {

    private final LeagueRepository leagueRepository;

    /**
     * 리그에 거래 락을 건다. 주문을 처리하는 트랜잭션 안에서 호출해야 한다.
     *
     * @param leagueId 리그 ID
     * @throws NaspiBizException 리그가 없거나 ({@link ErrorCode#LEAGUE_NOT_FOUND}), 거래할 수 있는 상태가
     *     아닌 경우 ({@link ErrorCode#LEAGUE_NOT_TRADABLE})
     * @throws org.springframework.transaction.IllegalTransactionStateException 진행 중인 트랜잭션이
     *     없는 경우 (락이 바로 풀려 직렬화가 되지 않는다)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForTrading(long leagueId) {
        League league = leagueRepository.findWithLockById(leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.LEAGUE_NOT_FOUND));
        if (!league.isTradable()) {
            throw new NaspiBizException(ErrorCode.LEAGUE_NOT_TRADABLE);
        }
    }
}
