package kr.ject.naspi.league.application;

import java.time.Clock;
import java.time.LocalDateTime;
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
    private final Clock clock;

    /**
     * 리그에 거래 락을 걸고 거래 가능 여부를 판정한다. 주문을 처리하는 트랜잭션 안에서 호출해야 한다.
     * 시작 상태 리그는 초기 자산 배분이 끝난 것이므로(LeagueStartUseCase가 같은 트랜잭션으로 처리) 별도로 보지 않는다.
     *
     * @param leagueId 리그 ID
     * @return 거래 가능하다고 판정한 시각. 호출한 쪽이 주문 접수·체결 시각으로 쓴다
     * @throws NaspiBizException 리그가 없거나 만료·삭제된 대기실인 경우({@link ErrorCode#LEAGUE_NOT_FOUND}),
     *     종료되었거나 종료 시각 이후인 경우({@link ErrorCode#LEAGUE_ALREADY_ENDED}), 아직 시작하지 않은 경우
     *     ({@link ErrorCode#LEAGUE_NOT_TRADABLE})
     * @throws org.springframework.transaction.IllegalTransactionStateException 진행 중인 트랜잭션이
     *     없는 경우 (락이 바로 풀려 직렬화가 되지 않는다)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDateTime lockForTrading(long leagueId) {
        League league = leagueRepository.findWithLockById(leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.LEAGUE_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        league.requireTradable(now);
        return now;
    }
}
