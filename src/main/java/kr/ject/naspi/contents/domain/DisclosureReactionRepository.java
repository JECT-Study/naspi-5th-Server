package kr.ject.naspi.contents.domain;

import java.util.Optional;

/**
 * 공시 반응 저장소. 구현은 infra의 {@code DisclosureReactionJpaRepository}가 맡는다.
 */
public interface DisclosureReactionRepository {

    /**
     * 반응을 저장하고 바로 DB에 반영한다. 같은 플레이어의 반응이 동시에 들어와 유니크 제약을 어기면 이 호출에서
     * 예외가 난다.
     *
     * @param reaction 반응
     * @return 저장된 반응
     */
    DisclosureReaction saveAndFlush(DisclosureReaction reaction);

    /**
     * 플레이어가 공시에 남긴 반응을 찾는다.
     *
     * @param disclosureId 공시 ID
     * @param playerId 플레이어 ID
     * @return 반응
     */
    Optional<DisclosureReaction> findByDisclosureIdAndPlayerId(Long disclosureId, Long playerId);

    /**
     * 공시의 반응 수를 구분별로 센다.
     *
     * @param disclosureId 공시 ID
     * @param type 호재 또는 악재
     * @return 반응 수
     */
    long countByDisclosureIdAndType(Long disclosureId, DisclosureReactionType type);

    /**
     * 반응을 삭제한다 (반응 취소).
     *
     * @param reaction 반응
     */
    void delete(DisclosureReaction reaction);
}
