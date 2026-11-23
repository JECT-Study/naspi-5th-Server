package kr.ject.naspi.contents.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kr.ject.naspi.contents.domain.CommentContent;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsComment;
import kr.ject.naspi.contents.domain.NewsCommentRepository;
import kr.ject.naspi.contents.domain.NewsRepository;
import kr.ject.naspi.contents.domain.NewsStatus;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 뉴스 댓글 작성·조회·삭제.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "댓글 (공통)"): 리그 참여자는 뉴스에 최대 100자 댓글을 쓸 수
 * 있고(이미지·대댓글 불가) 작성자 이름이 표시되며, 작성자 본인만 삭제할 수 있다. 결정 (docs/issues/v1/contents.md
 * CNT-09·CNT-10): 작성은 진행 중인 리그에서만, 조회·삭제는 리그 상태와 무관하다. 발행(PUBLISHED)된 뉴스에만
 * 댓글을 쓰고, 종목과 관계없이 리그 참여자면 쓸 수 있다.
 */
@Service
public class NewsCommentService {

    private final NewsCommentRepository commentRepository;
    private final NewsRepository newsRepository;
    private final LeagueQueryService leagueQueryService;
    private final Clock clock;

    /**
     * 뉴스 댓글 서비스를 만든다.
     *
     * @param commentRepository 댓글 저장소
     * @param newsRepository 뉴스 저장소
     * @param leagueQueryService 리그 상태·플레이어 조회
     * @param clock 작성 일시 기준 시계
     */
    public NewsCommentService(NewsCommentRepository commentRepository,
                              NewsRepository newsRepository,
                              LeagueQueryService leagueQueryService, Clock clock) {
        this.commentRepository = commentRepository;
        this.newsRepository = newsRepository;
        this.leagueQueryService = leagueQueryService;
        this.clock = clock;
    }

    /**
     * 뉴스에 댓글을 작성한다.
     *
     * @param memberId 작성하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 뉴스가 속한 종목 ID
     * @param newsId 뉴스 ID
     * @param content 댓글 내용
     * @return 작성한 댓글
     * @throws NaspiBizException 내용이 올바르지 않거나, 리그가 진행 중이 아니거나, 리그에 입장하지 않았거나,
     *         뉴스가 없거나 발행되지 않은 경우
     */
    @Transactional
    public NewsCommentItem write(long memberId, long leagueId, long stockId, long newsId,
                                 String content) {
        CommentContent commentContent = CommentContent.of(content);
        // 리그가 없으면 여기서 오류
        if (leagueQueryService.findCurrentTradingDay(leagueId).isEmpty()) {
            throw new NaspiBizException(ErrorCode.COMMENT_LEAGUE_NOT_STARTED);
        }
        long playerId = viewerPlayerId(memberId, leagueId);
        News news = findPublishedNews(leagueId, stockId, newsId);

        NewsComment saved = commentRepository.save(NewsComment.write(news, playerId,
                commentContent, LocalDateTime.now(clock)));
        String name = leagueQueryService.getPlayerNames(leagueId, List.of(playerId)).get(playerId);
        return toItem(saved, name, playerId);
    }

    /**
     * 뉴스의 댓글을 최신순으로 한 페이지 조회한다. 삭제된 댓글은 빠진다.
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 뉴스가 속한 종목 ID
     * @param newsId 뉴스 ID
     * @param cursor 이전 페이지의 {@link NewsCommentPage#nextCursor()}. 첫 페이지면 null
     * @param size 한 페이지 개수
     * @return 댓글 목록 한 페이지
     * @throws NaspiBizException 리그에 입장하지 않았거나, 뉴스가 없거나 발행되지 않은 경우
     */
    @Transactional(readOnly = true)
    public NewsCommentPage findByNews(long memberId, long leagueId, long stockId, long newsId,
                                      Long cursor, int size) {
        // 조회는 리그 상태와 무관하므로 입장 여부만 확인한다 (리그가 없어도 미입장으로 FORBIDDEN)
        long playerId = viewerPlayerId(memberId, leagueId);
        findPublishedNews(leagueId, stockId, newsId);

        // 다음 페이지가 있는지 알기 위해 1개 더 읽는다
        List<NewsComment> fetched = commentRepository.findActiveByNews(newsId, cursor, size + 1);
        boolean hasNext = fetched.size() > size;
        List<NewsComment> comments = hasNext ? fetched.subList(0, size) : fetched;
        Long nextCursor = hasNext ? comments.get(comments.size() - 1).getId() : null;
        Map<Long, String> names = leagueQueryService.getPlayerNames(leagueId,
                comments.stream().map(NewsComment::getPlayerId).distinct().toList());
        return new NewsCommentPage(comments.stream()
                .map(c -> toItem(c, names.get(c.getPlayerId()), playerId))
                .toList(), nextCursor);
    }

    /**
     * 작성자 본인의 댓글을 삭제한다. 리그 상태와 무관하다.
     *
     * @param memberId 삭제하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 뉴스가 속한 종목 ID
     * @param newsId 뉴스 ID
     * @param commentId 댓글 ID
     * @throws NaspiBizException 리그에 입장하지 않았거나, 댓글·뉴스가 없거나, 작성자가 아닌 경우
     */
    @Transactional
    public void delete(long memberId, long leagueId, long stockId, long newsId,
                       long commentId) {
        long playerId = viewerPlayerId(memberId, leagueId);
        NewsComment comment = commentRepository.findById(commentId)
                .filter(c -> c.getLeagueId() == leagueId && c.getNewsId() == newsId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.COMMENT_NOT_FOUND));
        newsRepository.findById(newsId)
                .filter(n -> n.getStockId() == stockId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.NEWS_NOT_FOUND));
        comment.delete(playerId);
    }

    private long viewerPlayerId(long memberId, long leagueId) {
        return leagueQueryService.findPlayerId(memberId, leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.FORBIDDEN));
    }

    /** 경로의 리그·종목에 속한 발행된 뉴스만 댓글 대상으로 본다. */
    private News findPublishedNews(long leagueId, long stockId, long newsId) {
        return newsRepository.findById(newsId)
                .filter(n -> n.getLeagueId() == leagueId && n.getStockId() == stockId
                        && n.getStatus() == NewsStatus.PUBLISHED)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.NEWS_NOT_FOUND));
    }

    private static NewsCommentItem toItem(NewsComment c, String name, long viewerPlayerId) {
        return new NewsCommentItem(c.getId(), c.getContent().getValue(), c.getPlayerId(), name,
                c.getPlayerId() == viewerPlayerId, c.getCreatedAt());
    }
}
