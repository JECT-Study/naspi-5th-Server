package kr.ject.naspi.contents.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.ject.naspi.contents.domain.Comment;
import kr.ject.naspi.contents.domain.CommentContent;
import kr.ject.naspi.contents.domain.CommentRepository;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 작성·조회·삭제의 공통 절차. 공시 댓글과 뉴스 댓글은 테이블은 따로지만 이 절차를 그대로 쓰고, 댓글을 달
 * 대상을 찾는 방법만 다르다.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "댓글 (공통)"): 리그 참여자는 최대 100자 댓글을 쓸 수 있고
 * (이미지·대댓글 불가) 작성자 이름이 표시되며, 작성자 본인만 삭제할 수 있다. 결정 (docs/issues/v1/contents.md
 * CNT-09·CNT-10): 작성은 진행 중인 리그에서만, 조회·삭제는 리그 상태와 무관하다. 종목과 관계없이 리그 참여자면
 * 쓸 수 있다.
 *
 * @param <T> 댓글 타입
 * @param <S> 댓글을 다는 대상 타입 (공시·뉴스)
 */
public abstract class CommentService<T extends Comment, S> {

    private final CommentRepository<T> commentRepository;
    private final LeagueQueryService leagueQueryService;
    private final Clock clock;

    protected CommentService(CommentRepository<T> commentRepository,
                             LeagueQueryService leagueQueryService, Clock clock) {
        this.commentRepository = commentRepository;
        this.leagueQueryService = leagueQueryService;
        this.clock = clock;
    }

    /**
     * 경로의 리그·종목에 속하고 댓글을 달거나 볼 수 있는 대상을 찾는다.
     *
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param targetId 대상 ID
     * @return 대상
     * @throws NaspiBizException 대상이 없거나 댓글을 달 수 없는 상태인 경우
     */
    protected abstract S findTarget(long leagueId, long stockId, long targetId);

    /**
     * 대상에 달 댓글을 만든다.
     *
     * @param target 대상
     * @param playerId 작성자 플레이어 ID
     * @param content 댓글 내용
     * @param createdAt 작성 일시
     * @return 댓글 (저장 전)
     */
    protected abstract T newComment(S target, long playerId, CommentContent content,
                                    LocalDateTime createdAt);

    /**
     * 댓글을 작성한다.
     *
     * @param memberId 작성하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 대상이 속한 종목 ID
     * @param targetId 대상 ID
     * @param content 댓글 내용
     * @return 작성한 댓글
     * @throws NaspiBizException 내용이 올바르지 않거나, 리그가 진행 중이 아니거나, 리그에 입장하지 않았거나,
     *         대상이 없는 경우
     */
    @Transactional
    public CommentItem write(long memberId, long leagueId, long stockId, long targetId,
                             String content) {
        CommentContent commentContent = CommentContent.of(content);
        // 리그가 없으면 여기서 오류
        if (leagueQueryService.findCurrentTradingDay(leagueId).isEmpty()) {
            throw new NaspiBizException(ErrorCode.COMMENT_LEAGUE_NOT_STARTED);
        }
        long playerId = viewerPlayerId(memberId, leagueId);
        S target = findTarget(leagueId, stockId, targetId);

        T saved = commentRepository.save(newComment(target, playerId, commentContent,
                LocalDateTime.now(clock)));
        return toItems(leagueId, List.of(saved), playerId).get(0);
    }

    /**
     * 대상의 댓글을 오래된 순으로 모두 조회한다. 삭제된 댓글은 빠진다. 댓글이 많지 않아 페이징하지 않는다.
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 대상이 속한 종목 ID
     * @param targetId 대상 ID
     * @return 댓글 목록 (오래된 순)
     * @throws NaspiBizException 리그에 입장하지 않았거나, 대상이 없는 경우
     */
    @Transactional(readOnly = true)
    public List<CommentItem> findByTarget(long memberId, long leagueId, long stockId,
                                          long targetId) {
        // 조회는 리그 상태와 무관하므로 입장 여부만 확인한다 (리그가 없어도 미입장으로 FORBIDDEN)
        long playerId = viewerPlayerId(memberId, leagueId);
        findTarget(leagueId, stockId, targetId);
        return findAll(leagueId, targetId, playerId);
    }

    /**
     * 상세 화면에 보여 줄 대상의 댓글을 오래된 순으로 모두 조회한다. 조회 권한과 대상 상태는 호출하는 쪽이
     * 확인한다.
     *
     * @param leagueId 리그 ID
     * @param targetId 대상 ID
     * @param viewerPlayerId 조회하는 플레이어 ID (내 댓글 여부 표시)
     * @return 댓글 목록 (오래된 순)
     */
    @Transactional(readOnly = true)
    public List<CommentItem> findAll(long leagueId, long targetId, long viewerPlayerId) {
        return toItems(leagueId, commentRepository.findActiveByTarget(targetId),
                viewerPlayerId);
    }

    /**
     * 작성자 본인의 댓글을 삭제한다. 리그 상태와 무관하다.
     *
     * @param memberId 삭제하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 대상이 속한 종목 ID
     * @param targetId 대상 ID
     * @param commentId 댓글 ID
     * @throws NaspiBizException 리그에 입장하지 않았거나, 댓글·대상이 없거나, 작성자가 아닌 경우
     */
    @Transactional
    public void delete(long memberId, long leagueId, long stockId, long targetId,
                       long commentId) {
        long playerId = viewerPlayerId(memberId, leagueId);
        T comment = commentRepository.findById(commentId)
                .filter(c -> c.getLeagueId() == leagueId && c.getTargetId() == targetId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.COMMENT_NOT_FOUND));
        findTarget(leagueId, stockId, targetId);
        comment.delete(playerId);
    }

    /**
     * 대상들의 댓글 수와 가장 먼저 등록된 댓글을 한 번에 조회한다. 목록·상세 조회에서 대상마다 따로 조회하지 않도록
     * 쓴다. 조회 권한과 대상 상태는 호출하는 쪽이 확인한다.
     *
     * @param leagueId 리그 ID
     * @param targetIds 대상 ID 목록
     * @param viewerPlayerId 조회하는 플레이어 ID (내 댓글 여부 표시)
     * @return 대상 ID별 미리보기. 댓글이 없는 대상도 0개로 담는다
     */
    @Transactional(readOnly = true)
    public Map<Long, CommentPreview> previews(long leagueId, Collection<Long> targetIds,
                                              long viewerPlayerId) {
        Map<Long, Long> counts = commentRepository.countActiveByTargets(targetIds);
        List<T> earliest = commentRepository.findEarliestActiveByTargets(targetIds);
        List<CommentItem> earliestItems = toItems(leagueId, earliest, viewerPlayerId);

        Map<Long, CommentPreview> result = new HashMap<>();
        targetIds.forEach(id -> result.put(id, CommentPreview.EMPTY));
        for (int i = 0; i < earliest.size(); i++) {
            long targetId = earliest.get(i).getTargetId();
            result.put(targetId, new CommentPreview(counts.getOrDefault(targetId, 0L),
                    earliestItems.get(i)));
        }
        return result;
    }

    private long viewerPlayerId(long memberId, long leagueId) {
        return leagueQueryService.requireAccessiblePlayer(memberId, leagueId);
    }

    /** 댓글들을 응답 항목으로 바꾼다. 작성자 이름은 댓글에 나온 플레이어만 한 번에 조회한다. */
    private List<CommentItem> toItems(long leagueId, List<T> comments, long viewerPlayerId) {
        if (comments.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = leagueQueryService.getPlayerNames(leagueId,
                comments.stream().map(Comment::getPlayerId).distinct().toList());
        return comments.stream()
                .map(c -> new CommentItem(c.getId(), c.getContent().getValue(), c.getPlayerId(),
                        names.get(c.getPlayerId()), c.getPlayerId() == viewerPlayerId,
                        c.getCreatedAt()))
                .toList();
    }
}
