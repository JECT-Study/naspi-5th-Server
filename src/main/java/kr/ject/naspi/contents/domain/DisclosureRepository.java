package kr.ject.naspi.contents.domain;

import java.util.Optional;

/**
 * 공시 저장소. 구현은 infra의 {@code DisclosureJpaRepository}가 맡는다.
 */
public interface DisclosureRepository {

    /**
     * 공시를 저장한다.
     *
     * @param disclosure 공시
     * @return 저장된 공시 (ID 발급됨)
     */
    Disclosure save(Disclosure disclosure);

    /**
     * 공시를 ID로 찾는다. 삭제된 공시도 돌려주므로 조회 용도라면 호출하는 쪽이 삭제 여부를 확인한다.
     *
     * @param id 공시 ID
     * @return 공시
     */
    Optional<Disclosure> findById(Long id);
}
