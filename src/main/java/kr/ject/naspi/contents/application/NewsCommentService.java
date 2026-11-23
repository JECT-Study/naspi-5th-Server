package kr.ject.naspi.contents.application;

import java.time.Clock;
import java.time.LocalDateTime;
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

/**
 * 뉴스 댓글 작성·조회·삭제. 절차는 {@link CommentService}를 따르고, 발행(PUBLISHED)된 뉴스에만 댓글을 단다.
 */
@Service
public class NewsCommentService extends CommentService<NewsComment, News> {

    private final NewsRepository newsRepository;

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
        super(commentRepository, leagueQueryService, clock);
        this.newsRepository = newsRepository;
    }

    /** 경로의 리그·종목에 속한 발행된 뉴스만 댓글 대상으로 본다. */
    @Override
    protected News findTarget(long leagueId, long stockId, long newsId) {
        return newsRepository.findById(newsId)
                .filter(n -> n.getLeagueId() == leagueId && n.getStockId() == stockId
                        && n.getStatus() == NewsStatus.PUBLISHED)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.NEWS_NOT_FOUND));
    }

    @Override
    protected NewsComment newComment(News news, long playerId, CommentContent content,
                                     LocalDateTime createdAt) {
        return NewsComment.write(news, playerId, content, createdAt);
    }
}
