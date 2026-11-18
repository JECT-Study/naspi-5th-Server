package kr.ject.naspi.contents.domain;

import java.util.Collection;
import java.util.List;

/**
 * 공시 이미지 저장소. 구현은 infra의 {@code ImageJpaRepository}가 맡는다.
 */
public interface ImageRepository {

    /**
     * 이미지를 한 번에 저장한다.
     *
     * @param images 이미지 목록
     * @return 저장된 이미지 목록 (ID 발급됨)
     */
    <S extends Image> List<S> saveAll(Iterable<S> images);

    /**
     * 공시에 첨부한 이미지를 첨부 순서대로 찾는다.
     *
     * @param disclosureId 공시 ID
     * @return 이미지 목록, 없으면 빈 목록
     */
    List<Image> findAllByDisclosureIdOrderBySortOrderAsc(Long disclosureId);

    /**
     * 여러 공시의 이미지를 한 번에 찾는다 (공시 목록용). 공시별로 첨부 순서대로 정렬한다.
     *
     * @param disclosureIds 공시 ID 목록
     * @return 이미지 목록, 없으면 빈 목록
     */
    List<Image> findAllByDisclosureIdInOrderByDisclosureIdAscSortOrderAsc(
            Collection<Long> disclosureIds);

    /**
     * 이미지를 한 번에 삭제한다.
     *
     * @param images 이미지 목록
     */
    void deleteAll(Iterable<? extends Image> images);
}
