package kr.ject.naspi.contents.api.news;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.contents.api.comment.CommentResponse;
import kr.ject.naspi.contents.application.NewsListItem;
import kr.ject.naspi.contents.application.NewsPage;
import kr.ject.naspi.contents.domain.NewsSentiment;

/**
 * 종목별 뉴스 목록 응답.
 *
 * @param items 뉴스 목록 (최신순)
 * @param nextCursor 다음 페이지 요청 시 {@code cursor}로 보낼 값. 더 없으면 null
 */
record NewsPageResponse(List<Item> items, Long nextCursor) {

    static NewsPageResponse from(NewsPage page) {
        return new NewsPageResponse(page.items().stream().map(Item::from).toList(),
                page.nextCursor());
    }

    /**
     * 뉴스 목록의 뉴스 1건. 원본 공시는 상세에만 있다.
     *
     * @param newsId 뉴스 ID
     * @param stockId 종목 ID
     * @param title 제목
     * @param body 본문
     * @param sentiment 호재(POSITIVE)·악재(NEGATIVE)
     * @param tradingDay 원본 공시를 작성한 거래일 번호 (1~7)
     * @param createdAt 뉴스 생성 요청 일시 (KST)
     * @param publishedAt 발행 일시 (KST)
     * @param commentCount 댓글 수
     * @param latestComment 가장 최근 댓글. 없으면 null
     */
    record Item(long newsId, long stockId, String title, String body, NewsSentiment sentiment,
                int tradingDay, LocalDateTime createdAt, LocalDateTime publishedAt,
                long commentCount, CommentResponse latestComment) {

        static Item from(NewsListItem item) {
            return new Item(item.newsId(), item.stockId(), item.title(), item.body(),
                    item.sentiment(), item.tradingDay(), item.createdAt(), item.publishedAt(),
                    item.comments().commentCount(),
                    CommentResponse.from(item.comments().latestComment()));
        }
    }
}
