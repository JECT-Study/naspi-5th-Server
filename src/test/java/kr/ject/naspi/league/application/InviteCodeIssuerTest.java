package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.random.RandomGenerator;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.LeagueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InviteCodeIssuerTest {

    private final LeagueRepository leagueRepository = mock(LeagueRepository.class);

    @Test
    @DisplayName("초대 코드는 대기실 리그가 쓰고 있는 코드와 겹치면 다시 발급한다")
    void reissueWhenDuplicatedAmongWaitingLeagues() {
        // 첫 코드는 AAAAAAA, 두 번째 코드는 BBBBBBB가 나오도록 고정한다
        InviteCodeIssuer issuer = new InviteCodeIssuer(leagueRepository, sequence(0, 1));
        given(leagueRepository.existsByInviteCode(InviteCode.of("AAAAAAA"))).willReturn(true);

        assertThat(issuer.issue()).isEqualTo(InviteCode.of("BBBBBBB"));
    }

    @Test
    @DisplayName("겹치지 않는 코드를 끝내 찾지 못하면 서버 오류로 처리한다")
    void failAfterMaxAttempts() {
        InviteCodeIssuer issuer = new InviteCodeIssuer(leagueRepository, sequence(0));
        given(leagueRepository.existsByInviteCode(any())).willReturn(true);

        assertThatThrownBy(issuer::issue).isInstanceOf(NaspiRuntimeException.class);
    }

    /**
     * 코드 하나(7글자)마다 주어진 문자 인덱스를 차례로 돌려주는 난수 생성기. 마지막 값은 계속 반복한다.
     */
    private static RandomGenerator sequence(int... charIndexPerCode) {
        return new RandomGenerator() {
            private int calls;

            @Override
            public int nextInt(int bound) {
                int codeIndex = Math.min(calls++ / InviteCode.LENGTH, charIndexPerCode.length - 1);
                return charIndexPerCode[codeIndex];
            }

            @Override
            public long nextLong() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
