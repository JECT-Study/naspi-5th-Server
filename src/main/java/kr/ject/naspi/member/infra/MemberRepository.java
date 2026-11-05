package kr.ject.naspi.member.infra;

import java.util.Optional;
import kr.ject.naspi.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원 저장소.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 카카오 회원 번호로 회원을 찾는다.
     *
     * @param kakaoId 카카오 회원 번호
     * @return 회원, 가입하지 않았으면 빈 값
     */
    Optional<Member> findByKakaoId(Long kakaoId);

    /**
     * 카카오 회원 번호로 가입한 회원이 있는지 확인한다.
     *
     * @param kakaoId 카카오 회원 번호
     * @return 가입 여부
     */
    boolean existsByKakaoId(Long kakaoId);
}
