package kr.ject.naspi.contents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import java.util.Objects;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공시·뉴스 댓글의 공통 부분. 대상별 테이블({@link DisclosureComment}, {@link NewsComment})이 이 필드와 규칙을
 * 그대로 쓴다.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "댓글 (공통)"): 최대 100자, 이미지 첨부 불가, 대댓글 불가,
 * 작성자 본인만 삭제할 수 있다. 부모 댓글·이미지 필드를 두지 않아 구조로 대댓글·이미지를 막는다.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false, updatable = false)
    private Long leagueId;

    @Column(name = "player_id", nullable = false, updatable = false)
    private Long playerId;

    @Embedded
    private CommentContent content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Comment(long leagueId, long playerId, CommentContent content,
                      LocalDateTime createdAt) {
        this.leagueId = leagueId;
        this.playerId = playerId;
        this.content = Objects.requireNonNull(content, "content");
        this.status = CommentStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * 댓글이 달린 대상(공시·뉴스)의 ID.
     *
     * @return 대상 ID
     */
    public abstract long getTargetId();

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
