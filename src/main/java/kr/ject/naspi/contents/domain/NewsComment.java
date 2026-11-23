package kr.ject.naspi.contents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 뉴스 댓글 (ERD: news_comment).
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "댓글 (공통)"): 최대 100자, 이미지 첨부 불가, 대댓글 불가,
 * 작성자 본인만 삭제할 수 있다. 부모 댓글·이미지 필드를 두지 않아 구조로 대댓글·이미지를 막는다.
 */
@Entity
@Table(name = "news_comment",
        indexes = @Index(name = "idx_news_comment_news_id", columnList = "news_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false, updatable = false)
    private Long leagueId;

    @Column(name = "news_id", nullable = false, updatable = false)
    private Long newsId;

    @Column(name = "player_id", nullable = false, updatable = false)
    private Long playerId;

    @Embedded
    private CommentContent content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private NewsComment(long leagueId, long newsId, long playerId, CommentContent content,
                        LocalDateTime createdAt) {
        this.leagueId = leagueId;
        this.newsId = newsId;
        this.playerId = playerId;
        this.content = Objects.requireNonNull(content, "content");
        this.status = CommentStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
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

    /**
     * 작성자가 댓글을 삭제한다. 정책: 댓글 작성자 본인만 삭제할 수 있다.
     *
     * @param playerId 삭제하려는 플레이어 ID
     * @throws NaspiBizException 이미 삭제된 댓글이거나 작성자가 아닌 경우
     */
    public void delete(long playerId) {
        if (isDeleted()) {
            throw new NaspiBizException(ErrorCode.COMMENT_NOT_FOUND);
        }
        if (!this.playerId.equals(playerId)) {
            throw new NaspiBizException(ErrorCode.COMMENT_NOT_AUTHOR);
        }
        this.status = CommentStatus.DELETED;
    }

    /**
     * 삭제된 댓글인지 확인한다.
     *
     * @return 삭제되었으면 true
     */
    public boolean isDeleted() {
        return status == CommentStatus.DELETED;
    }
}
