package kr.ject.naspi.contents.domain;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * 리그의 게시 중인(삭제되지 않은) 공시를 작성 일시 최신순(같으면 ID 내림차순)으로 기준 위치 다음부터 찾는다.
     * 작성 일시가 {@code before}보다 이르거나, 같으면서 ID가 {@code idBefore}보다 작은 공시만 찾는다.
     *
     * @param leagueId 리그 ID
     * @param stockId 종목 ID. null이면 리그의 모든 종목
     * @param before 기준 작성 일시. 첫 페이지면 null
     * @param idBefore 작성 일시가 기준과 같은 공시의 ID 상한 (이 값 미만). 첫 페이지면 쓰지 않는다
     * @param limit 최대 개수
     * @return 공시 목록 (작성 일시 내림차순, ID 내림차순)
     */
    List<Disclosure> findPublishedInLeague(long leagueId, Long stockId, LocalDateTime before,
                                           long idBefore, int limit);

    /**
     * 종목에서 기준 공시보다 먼저 작성된 공시들의 AI 요약을 오래된 순으로 찾는다. 삭제된 공시도 포함하고,
     * 요약이 없는 공시는 제외한다.
     *
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param disclosureId 기준 공시 ID. 이 공시는 포함하지 않는다
     * @return AI 요약 목록 (작성 순)
     */
    List<String> findAiSummariesBefore(long leagueId, long stockId, long disclosureId);
}
