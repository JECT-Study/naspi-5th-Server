package kr.ject.naspi.contents.api.disclosure;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.contents.api.comment.CommentResponse;
import kr.ject.naspi.contents.application.DisclosureDetail;
import kr.ject.naspi.contents.domain.DisclosureReactionType;

/**
 * 공시 상세 응답. 목록 항목과 같은 필드에서 가장 최근 댓글 대신 댓글 전체를 주고, 연결된 뉴스가 더해진다.
 *
 * @param disclosureId 공시 ID
 * @param stockId 종목 ID
 * @param title 제목
 * @param body 본문
 * @param tradingDay 작성한 거래일 번호 (1~7)
 * @param createdAt 작성 일시 (KST)
 * @param authorPlayerId 작성자(상장자) 플레이어 ID
 * @param mine 내가 작성한 공시인지
 * @param images 첨부 이미지 (첨부 순서)
 * @param likeCount 좋아요(호재) 수
 * @param dislikeCount 싫어요(악재) 수
 * @param myReaction 내 반응. 없으면 null
 * @param commentCount 댓글 수
 * @param comments 댓글 전체 (최신순). 댓글이 없으면 빈 목록
 * @param news 공시로 만든 AI 뉴스. 없으면 null
 */
record DisclosureDetailResponse(long disclosureId, long stockId, String title, String body,
                                int tradingDay, LocalDateTime createdAt, long authorPlayerId,
                                boolean mine, List<DisclosureImageResponse> images,
                                long likeCount, long dislikeCount,
                                DisclosureReactionType myReaction, long commentCount,
                                List<CommentResponse> comments, DisclosureNewsResponse news) {

    static DisclosureDetailResponse from(DisclosureDetail detail) {
        return new DisclosureDetailResponse(detail.disclosureId(), detail.stockId(),
                detail.title(), detail.body(), detail.tradingDay(), detail.createdAt(),
                detail.authorPlayerId(), detail.mine(),
                detail.images().stream().map(DisclosureImageResponse::from).toList(),
                detail.reaction().likeCount(), detail.reaction().dislikeCount(),
                detail.reaction().myReaction(), detail.comments().size(),
                detail.comments().stream().map(CommentResponse::from).toList(),
                DisclosureNewsResponse.from(detail.news()));
    }
}
