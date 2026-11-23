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
 * 공시 댓글 (ERD: disclosure_comment). 필드와 규칙은 {@link Comment}를 따른다.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "공시"): 공시가 삭제되면 달린 댓글도 조회할 수 없다.
 */
@Entity
@Table(name = "disclosure_comment",
        indexes = @Index(name = "idx_disclosure_comment_disclosure_id",
                columnList = "disclosure_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureComment extends Comment {

    @Column(name = "disclosure_id", nullable = false, updatable = false)
    private Long disclosureId;

    private DisclosureComment(long leagueId, long disclosureId, long playerId,
                              CommentContent content, LocalDateTime createdAt) {
        super(leagueId, playerId, content, createdAt);
        this.disclosureId = disclosureId;
    }

    /**
     * 공시에 댓글을 작성한다.
     *
     * @param disclosure 댓글을 달 공시 (저장되어 ID가 있어야 한다)
     * @param playerId 작성자 플레이어 ID
     * @param content 댓글 내용
     * @param createdAt 작성 일시
     * @return 댓글 (저장 전)
     */
    public static DisclosureComment write(Disclosure disclosure, long playerId,
                                          CommentContent content, LocalDateTime createdAt) {
        return new DisclosureComment(disclosure.getLeagueId(), disclosure.getId(), playerId,
                content, createdAt);
    }

    @Override
    public long getTargetId() {
        return disclosureId;
    }
}
