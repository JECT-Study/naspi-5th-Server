package kr.ject.naspi.contents.api.disclosure;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.contents.api.comment.CommentResponse;
import kr.ject.naspi.contents.api.feed.FeedItemResponse;
import kr.ject.naspi.contents.application.DisclosureListItem;
import kr.ject.naspi.contents.application.FeedItemType;
import kr.ject.naspi.contents.domain.DisclosureReactionType;

/**
 * 공시 목록의 공시 1건. 종목별 공시 목록과 통합 피드가 같이 쓴다. 연결된 뉴스는 상세에만 있다.
 *
 * @param type 항목 종류. 항상 {@code DISCLOSURE}
 * @param disclosureId 공시 ID
 * @param stockId 종목 ID
 * @param title 제목
 * @param body 본문
 * @param tradingDay 작성한 거래일 번호 (1~7)
 * @param createdAt 작성 일시 (KST)
 * @param authorPlayerId 작성자(상장자) 플레이어 ID
 * @param mine 내가 작성한 공시인지
 * @param images 첨부 이미지 (첨부 순서). 없으면 빈 목록
 * @param likeCount 좋아요(호재) 수
 * @param dislikeCount 싫어요(악재) 수
 * @param myReaction 내 반응. 없으면 null
 * @param commentCount 댓글 수
 * @param previewComment 미리보기 댓글 (가장 먼저 등록된 댓글). 없으면 null
 */
public record DisclosureListItemResponse(FeedItemType type, long disclosureId, long stockId,
                                         String title, String body, int tradingDay,
                                         LocalDateTime createdAt, long authorPlayerId,
                                         boolean mine, List<DisclosureImageResponse> images,
                                         long likeCount, long dislikeCount,
                                         DisclosureReactionType myReaction, long commentCount,
                                         CommentResponse previewComment)
        implements FeedItemResponse {

    /**
     * 공시 목록 항목을 응답으로 바꾼다.
     *
     * @param item 공시 목록 항목
     * @return 응답
     */
    public static DisclosureListItemResponse from(DisclosureListItem item) {
        return new DisclosureListItemResponse(item.type(), item.disclosureId(), item.stockId(),
                item.title(), item.body(), item.tradingDay(), item.createdAt(),
                item.authorPlayerId(), item.mine(),
                item.images().stream().map(DisclosureImageResponse::from).toList(),
                item.reaction().likeCount(), item.reaction().dislikeCount(),
                item.reaction().myReaction(), item.comments().commentCount(),
                CommentResponse.from(item.comments().previewComment()));
    }
}
