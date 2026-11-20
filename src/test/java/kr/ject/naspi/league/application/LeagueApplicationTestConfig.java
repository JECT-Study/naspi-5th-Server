package kr.ject.naspi.league.application;

import kr.ject.naspi.member.application.MemberService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 다른 모듈의 테스트가 league application을 쓸 때 필요한 빈 묶음. {@link LeagueJoinLimit}처럼
 * package-private인 구현 클래스를 다른 패키지의 테스트에서 직접 {@code @Import}할 수 없어서 둔다.
 */
@TestConfiguration
@Import({LeagueService.class, LeagueQueryService.class, LeagueJoinLimit.class,
        MemberService.class})
public class LeagueApplicationTestConfig {
}
