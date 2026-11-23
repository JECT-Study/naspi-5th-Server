package kr.ject.naspi.contents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 뉴스 댓글 (ERD: news_comment). 필드와 규칙은 {@link Comment}를 따른다.
 */
@Entity
@Table(name = "news_comment",
        indexes = @Index(name = "idx_news_comment_news_id", columnList = "news_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsComment extends Comment {

    @Column(name = "news_id", nullable = false, updatable = false)
    private Long newsId;

    private NewsComment(long leagueId, long newsId, long playerId, CommentContent content,
                        LocalDateTime createdAt) {
        super(leagueId, playerId, content, createdAt);
        this.newsId = newsId;
    }

    /**
     * 뉴스에 댓글을 작성한다.
     *
     * @param news 댓글을 달 뉴스 (저장되어 ID가 있어야 한다)
     * @param playerId 작성자 플레이어 ID
     * @param content 댓글 내용
     * @param createdAt 작성 일시
     * @return 댓글 (저장 전)
     */
    public static NewsComment write(News news, long playerId, CommentContent content,
                                    LocalDateTime createdAt) {
        return new NewsComment(news.getLeagueId(), news.getId(), playerId, content, createdAt);
    }

    @Override
    public long getTargetId() {
        return newsId;
    }
}
