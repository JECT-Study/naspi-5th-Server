package kr.ject.naspi.contents.domain;

import java.util.List;

/**
 * 제보 이미지 저장소. 구현은 infra의 {@code ReportImageJpaRepository}가 맡는다.
 */
public interface ReportImageRepository {

    /**
     * 이미지를 한 번에 저장한다.
     *
     * @param images 이미지 목록
     * @return 저장된 이미지 목록 (ID 발급됨)
     */
    <S extends ReportImage> List<S> saveAll(Iterable<S> images);

    /**
     * 제보에 첨부한 이미지를 첨부 순서대로 찾는다.
     *
     * @param reportId 제보 ID
     * @return 이미지 목록, 없으면 빈 목록
     */
    List<ReportImage> findAllByReportIdOrderBySortOrderAsc(Long reportId);
}
