package kr.ject.naspi.contents.api.disclosure;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.contents.application.DisclosureListItem;
import kr.ject.naspi.contents.application.DisclosurePage;
import kr.ject.naspi.contents.domain.DisclosureReactionType;

/**
 * 종목별 공시 목록 응답.
 *
 * @param items 공시 목록 (최신순)
 * @param nextCursor 다음 페이지 요청 시 {@code cursor}로 보낼 값. 더 없으면 null
 */
record DisclosurePageResponse(List<Item> items, Long nextCursor) {

    static DisclosurePageResponse from(DisclosurePage page) {
        return new DisclosurePageResponse(page.items().stream().map(Item::from).toList(),
                page.nextCursor());
    }

    /**
     * 공시 목록의 공시 1건.
     *
     * @param disclosureId 공시 ID
     * @param stockId 종목 ID
     * @param title 제목
     * @param body 본문
     * @param tradingDay 작성한 거래일 번호 (1~7)
     * @param createdAt 작성 일시 (KST)
     * @param authorPlayerId 작성자(상장자) 플레이어 ID
     * @param mine 내가 작성한 공시인지
     * @param imageCount 첨부 이미지 수
     * @param thumbnail 첫 번째 이미지. 없으면 null
     * @param likeCount 좋아요(호재) 수
     * @param dislikeCount 싫어요(악재) 수
     * @param myReaction 내 반응. 없으면 null
     */
    record Item(long disclosureId, long stockId, String title, String body, int tradingDay,
                LocalDateTime createdAt, long authorPlayerId, boolean mine, int imageCount,
                DisclosureImageResponse thumbnail, long likeCount, long dislikeCount,
                DisclosureReactionType myReaction) {

        static Item from(DisclosureListItem item) {
            return new Item(item.disclosureId(), item.stockId(), item.title(), item.body(),
                    item.tradingDay(), item.createdAt(), item.authorPlayerId(), item.mine(),
                    item.imageCount(), DisclosureImageResponse.from(item.thumbnail()),
                    item.reaction().likeCount(), item.reaction().dislikeCount(),
                    item.reaction().myReaction());
        }
    }
}
