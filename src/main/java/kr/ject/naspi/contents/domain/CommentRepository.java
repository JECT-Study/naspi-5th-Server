package kr.ject.naspi.contents.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 댓글 저장소의 공통 조회. 대상별 저장소({@link DisclosureCommentRepository}, {@link NewsCommentRepository})가
 * 같은 방식으로 구현한다.
 *
 * @param <T> 댓글 타입
 */
public interface CommentRepository<T extends Comment> {

    /**
     * 댓글을 저장한다.
     *
     * @param comment 댓글
     * @return 저장된 댓글 (ID 발급됨)
     */
    T save(T comment);

    /**
     * 댓글을 ID로 찾는다.
     *
     * @param id 댓글 ID
     * @return 댓글 (삭제 여부와 관계없이)
     */
    Optional<T> findById(Long id);

    /**
     * 대상의 삭제되지 않은 댓글을 모두 오래된 순(ID 오름차순)으로 찾는다.
     *
     * @param targetId 대상(공시·뉴스) ID
     * @return 댓글 목록
     */
    List<T> findActiveByTarget(long targetId);

    /**
     * 대상별 삭제되지 않은 댓글 수를 센다.
     *
     * @param targetIds 대상 ID 목록
     * @return 대상 ID별 댓글 수. 댓글이 없는 대상은 빠진다
     */
    Map<Long, Long> countActiveByTargets(Collection<Long> targetIds);

    /**
     * 대상별 삭제되지 않은 가장 먼저 등록된 댓글을 찾는다.
     *
     * @param targetIds 대상 ID 목록
     * @return 대상마다 최대 1개의 댓글. 댓글이 없는 대상은 빠진다
     */
    List<T> findEarliestActiveByTargets(Collection<Long> targetIds);
}
