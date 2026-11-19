package kr.ject.naspi.contents.infra;

import kr.ject.naspi.contents.domain.Image;
import kr.ject.naspi.contents.domain.ImageRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link ImageRepository}의 Spring Data 구현.
 */
interface ImageJpaRepository extends Repository<Image, Long>, ImageRepository {
}
