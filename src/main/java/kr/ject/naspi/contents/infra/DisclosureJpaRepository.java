package kr.ject.naspi.contents.infra;

import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link DisclosureRepository}의 Spring Data 구현.
 */
interface DisclosureJpaRepository extends Repository<Disclosure, Long>, DisclosureRepository {
}
