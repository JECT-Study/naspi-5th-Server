package kr.ject.naspi.contents.domain;

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
}
