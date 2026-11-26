package kr.ject.naspi.contents.api.news;

import java.time.LocalDateTime;
import kr.ject.naspi.contents.api.comment.CommentResponse;
import kr.ject.naspi.contents.api.feed.FeedItemResponse;
import kr.ject.naspi.contents.application.FeedItemType;
import kr.ject.naspi.contents.application.NewsListItem;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.contents.domain.NewsType;

/**
 * 뉴스 목록의 뉴스 1건. 종목별 뉴스 목록과 통합 피드가 같이 쓴다. 원본 공시는 상세에만 있다.
 *
 * @param type 항목 종류. 항상 {@code NEWS}
 * @param newsId 뉴스 ID
 * @param stockId 종목 ID
 * @param stockName 종목 이름
 * @param newsType 뉴스 유형. 공시 기반 {@code DISCLOSURE}, 사용자 제보 기반 {@code TIP}
 * @param title 제목
 * @param body 본문
 * @param sentiment 호재(POSITIVE)·악재(NEGATIVE)
 * @param tradingDay 원본 공시를 작성한 거래일 번호 (1~7)
 * @param createdAt 뉴스 생성 요청 일시 (KST)
 * @param publishedAt 발행 일시 (KST)
 * @param commentCount 댓글 수
 * @param previewComment 미리보기 댓글 (가장 먼저 등록된 댓글). 없으면 null
 */
public record NewsListItemResponse(FeedItemType type, long newsId, long stockId,
                                   String stockName, NewsType newsType, String title,
                                   String body, NewsSentiment sentiment, int tradingDay,
                                   LocalDateTime createdAt, LocalDateTime publishedAt,
                                   long commentCount, CommentResponse previewComment)
        implements FeedItemResponse {

    /**
     * 뉴스 목록 항목을 응답으로 바꾼다.
     *
     * @param item 뉴스 목록 항목
     * @return 응답
     */
    public static NewsListItemResponse from(NewsListItem item) {
        return new NewsListItemResponse(item.type(), item.newsId(), item.stockId(),
                item.stockName(), item.newsType(), item.title(), item.body(), item.sentiment(),
                item.tradingDay(), item.createdAt(),
                item.publishedAt(), item.comments().commentCount(),
                CommentResponse.from(item.comments().previewComment()));
    }
}
