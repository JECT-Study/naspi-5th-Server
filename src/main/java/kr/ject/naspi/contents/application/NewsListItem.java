package kr.ject.naspi.contents.application;

import java.time.LocalDateTime;
import kr.ject.naspi.contents.domain.NewsSentiment;

/**
 * 뉴스 목록의 발행된 뉴스 1건. 원본 공시는 상세({@link NewsDetail})에만 있다.
 *
 * @param newsId 뉴스 ID
 * @param stockId 종목 ID
 * @param stockName 종목 이름
 * @param title 제목
 * @param body 본문
 * @param sentiment 호재(POSITIVE)·악재(NEGATIVE)
 * @param tradingDay 원본 공시를 작성한 거래일 번호 (1~7)
 * @param createdAt 뉴스 생성을 요청한 일시 (공시 작성 일시, KST)
 * @param publishedAt 발행 일시 (KST)
 * @param comments 댓글 수와 가장 먼저 등록된 댓글
 */
public record NewsListItem(long newsId, long stockId, String stockName,
                           String title, String body,
                           NewsSentiment sentiment, int tradingDay, LocalDateTime createdAt,
                           LocalDateTime publishedAt, CommentPreview comments)
        implements FeedItem {

    @Override
    public FeedItemType type() {
        return FeedItemType.NEWS;
    }
}
