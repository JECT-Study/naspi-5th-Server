package kr.ject.naspi.contents.infra;

import kr.ject.naspi.contents.domain.DisclosureReaction;
import kr.ject.naspi.contents.domain.DisclosureReactionRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link DisclosureReactionRepository}의 Spring Data 구현.
 */
interface DisclosureReactionJpaRepository
        extends Repository<DisclosureReaction, Long>, DisclosureReactionRepository {
}
