package kr.ject.naspi.member.domain;

import java.util.Optional;

/**
 * 회원 저장소.
 *
 * <p>도메인이 필요로 하는 저장·조회만 정의한다. 구현은 infra의 {@code MemberJpaRepository}가 맡는다.
 */
public interface MemberRepository {

    /**
     * 회원을 저장한다.
     *
     * @param member 회원
     * @return 저장된 회원 (ID 발급됨)
     */
    Member save(Member member);

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
