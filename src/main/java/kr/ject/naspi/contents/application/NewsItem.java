package kr.ject.naspi.contents.application;

import java.time.LocalDateTime;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsSentiment;

/**
 * 발행된 뉴스. 목록 항목과 상세에 같이 쓴다.
 *
 * @param newsId 뉴스 ID
 * @param stockId 종목 ID
 * @param disclosureId 원본 공시 ID (공시가 삭제됐을 수 있다)
 * @param title 제목
 * @param body 본문
 * @param sentiment 호재(POSITIVE)·악재(NEGATIVE)
 * @param tradingDay 원본 공시를 작성한 거래일 번호 (1~7)
 * @param createdAt 뉴스 생성을 요청한 일시 (공시 작성 일시, KST)
 * @param publishedAt 발행 일시 (KST)
 */
public record NewsItem(long newsId, long stockId, Long disclosureId, String title, String body,
                       NewsSentiment sentiment, int tradingDay, LocalDateTime createdAt,
                       LocalDateTime publishedAt) {

    static NewsItem from(News n) {
        return new NewsItem(n.getId(), n.getStockId(), n.getDisclosureId(), n.getTitle(),
                n.getBody(), n.getSentiment(), n.getTradingDay(), n.getCreatedAt(),
                n.getPublishedAt());
    }
}
