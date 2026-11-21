package kr.ject.naspi.league.application;

import java.time.Clock;
import java.time.LocalDateTime;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.TradingDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 단위 거래 직렬화. order가 주문을 접수·취소·만료할 때 가장 먼저 호출한다. 회원 탈퇴 주문 취소는
 * 거래 가능 판정 없이 잠그는 {@link #lockForClosing}을 호출한다.
 *
 * <p>리그 행에 비관적 쓰기 락을 걸어 같은 리그의 주문 처리를 한 번에 하나씩 진행한다. 여러 서버가
 * 떠 있어도 DB 락이므로 동작한다. 락은 호출한 트랜잭션이 끝날 때 풀리고, 호출한 쪽은 락을 잡은 뒤에
 * 호가창을 조회하고 주문을 저장해야 주문 {@code id} 순서가 처리 순서와 같아진다. 락 순서는 리그 락,
 * asset 락(예약·정산) 순이다 (docs/issues/v1/order.md ORD-03).
 * 주문 접수는 락 → 거래일 확인({@link #requireTradingDayOpened}) 순으로 호출한다.
 */
@Service
@RequiredArgsConstructor
public class LeagueTradingService {

    private final LeagueRepository leagueRepository;
    private final TradingDayRepository tradingDayRepository;
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

    /**
     * 거래 가능 판정 없이 리그에 락만 건다. 접수·취소·체결·만료와 같은 리그 락으로 직렬화한다. 종료 시각이
     * 지났지만 상태가 아직 시작인 리그도 잠글 수 있어야 하는 일괄 취소(회원 탈퇴 ORD-11, 리그 종료 ORD-10)에서 쓴다.
     *
     * @param leagueId 리그 ID
     * @return 락을 잡은 뒤의 시각. 호출한 쪽이 취소 시각으로 쓴다
     * @throws NaspiBizException 리그가 없는 경우({@link ErrorCode#LEAGUE_NOT_FOUND})
     * @throws org.springframework.transaction.IllegalTransactionStateException 진행 중인 트랜잭션이
     *     없는 경우 (락이 바로 풀려 직렬화가 되지 않는다)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDateTime lockForClosing(long leagueId) {
        leagueRepository.findWithLockById(leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.LEAGUE_NOT_FOUND));
        return LocalDateTime.now(clock);
    }

    /**
     * 판정 시각의 거래일 번호를 정하고, 그 거래일로 전환이 끝났는지 확인한다. 09:00:00 이후 전환 처리 전에는
     * 새 거래일 시세(기준가·상·하한가)가 없고 직전 거래일 종가도 확정되지 않았으므로 주문 접수를 거절한다.
     * {@link #lockForTrading} 뒤 같은 트랜잭션에서 호출해야 전환 커밋을 볼 수 있다.
     *
     * @param leagueId 리그 ID
     * @param at {@link #lockForTrading}이 돌려준 판정 시각
     * @return 1~7 사이의 거래일 번호
     * @throws NaspiBizException 거래일 전환 처리 전인 경우({@link ErrorCode#TRADING_DAY_CHANGE_PENDING})
     * @throws IllegalStateException 직전 거래일 행이 없는 경우 (리그 시작이 7일치를 저장하므로 불변식 위반)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int requireTradingDayOpened(long leagueId, LocalDateTime at) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.LEAGUE_NOT_FOUND));
        int dayNumber = league.tradingSchedule().dayNumberAt(at);
        if (dayNumber == 1) {
            return dayNumber;
        }
        boolean pending = tradingDayRepository.findByLeagueIdAndDayNumber(leagueId, dayNumber - 1)
                .orElseThrow(() -> new IllegalStateException("거래일이 없습니다: leagueId="
                        + leagueId + ", dayNumber=" + (dayNumber - 1)))
                .awaitsChangeAt(at);
        if (pending) {
            throw new NaspiBizException(ErrorCode.TRADING_DAY_CHANGE_PENDING);
        }
        return dayNumber;
    }
}
