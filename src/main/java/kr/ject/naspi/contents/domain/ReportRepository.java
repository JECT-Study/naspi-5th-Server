package kr.ject.naspi.contents.domain;

import java.util.Optional;

/**
 * 제보 저장소. 구현은 infra의 {@code ReportJpaRepository}가 맡는다.
 */
public interface ReportRepository {

    /**
     * 제보를 저장한다.
     *
     * @param report 제보
     * @return 저장된 제보 (ID 발급됨)
     */
    Report save(Report report);

    /**
     * 제보를 ID로 찾는다.
     *
     * @param id 제보 ID
     * @return 제보
     */
    Optional<Report> findById(Long id);
}
