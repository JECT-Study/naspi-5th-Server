package kr.ject.naspi.member.infra;

import kr.ject.naspi.member.domain.Member;
import kr.ject.naspi.member.domain.MemberRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link MemberRepository}의 Spring Data 구현.
 *
 * <p>도메인 인터페이스의 메서드는 Spring Data가 메서드 이름으로 쿼리를 만들어 구현한다.
 * JPQL 등 저장 기술에 묶인 쿼리가 필요하면 도메인 인터페이스가 아니라 여기에 둔다.
 */
interface MemberJpaRepository extends Repository<Member, Long>, MemberRepository {
}
