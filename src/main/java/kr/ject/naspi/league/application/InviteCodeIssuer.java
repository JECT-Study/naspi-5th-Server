package kr.ject.naspi.league.application;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.LeagueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 대기실 초대 코드를 발급한다.
 *
 * <p>지금 쓰이고 있는 코드(대기실 리그의 코드)와 겹치지 않는 코드를 고른다. 시작·만료된 리그는 코드를 비우므로
 * 그 코드는 다시 발급될 수 있다. 동시 요청이 같은 코드를 뽑는 경우는 DB UNIQUE
 * 제약이 중복 저장을 막는다.
 */
@Component
class InviteCodeIssuer {

    /** 겹치는 코드가 연달아 나올 때 다시 뽑는 최대 횟수. 36^7개 중에서 뽑으므로 사실상 한 번에 끝난다. */
    static final int MAX_ATTEMPTS = 10;

    private final LeagueRepository leagueRepository;
    private final RandomGenerator random;

    @Autowired
    InviteCodeIssuer(LeagueRepository leagueRepository) {
        this(leagueRepository, new SecureRandom());
    }

    InviteCodeIssuer(LeagueRepository leagueRepository, RandomGenerator random) {
        this.leagueRepository = leagueRepository;
        this.random = random;
    }

    /**
     * 대기실 리그와 겹치지 않는 초대 코드를 발급한다.
     *
     * @return 초대 코드
     * @throws NaspiRuntimeException 정해진 횟수 안에 겹치지 않는 코드를 찾지 못한 경우
     */
    InviteCode issue() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            InviteCode code = InviteCode.random(random);
            if (!leagueRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new NaspiRuntimeException(
                "대기실 초대 코드를 " + MAX_ATTEMPTS + "번 연속으로 중복 발급해 생성에 실패했습니다.");
    }
}
