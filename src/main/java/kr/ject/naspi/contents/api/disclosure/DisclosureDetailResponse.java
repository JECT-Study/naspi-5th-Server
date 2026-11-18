package kr.ject.naspi.contents.api.disclosure;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.contents.application.DisclosureDetail;
import kr.ject.naspi.contents.domain.DisclosureReactionType;

/**
 * 공시 상세 응답.
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
 */
record DisclosureDetailResponse(long disclosureId, long stockId, String title, String body,
                                int tradingDay, LocalDateTime createdAt, long authorPlayerId,
                                boolean mine, List<DisclosureImageResponse> images,
                                long likeCount, long dislikeCount,
                                DisclosureReactionType myReaction) {

    static DisclosureDetailResponse from(DisclosureDetail detail) {
        return new DisclosureDetailResponse(detail.disclosureId(), detail.stockId(),
                detail.title(), detail.body(), detail.tradingDay(), detail.createdAt(),
                detail.authorPlayerId(), detail.mine(),
                detail.images().stream().map(DisclosureImageResponse::from).toList(),
                detail.reaction().likeCount(), detail.reaction().dislikeCount(),
                detail.reaction().myReaction());
    }
}
