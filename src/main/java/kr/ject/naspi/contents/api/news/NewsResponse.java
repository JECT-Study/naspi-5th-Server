package kr.ject.naspi.contents.api.news;

import java.time.LocalDateTime;
import kr.ject.naspi.contents.application.NewsItem;
import kr.ject.naspi.contents.domain.NewsSentiment;

/**
 * 발행된 뉴스 응답 (목록 항목·상세 공통).
 *
 * @param newsId 뉴스 ID
 * @param stockId 종목 ID
 * @param disclosureId 원본 공시 ID. 공시가 삭제됐으면 공시 상세는 조회할 수 없다
 * @param title 제목
 * @param body 본문
 * @param sentiment 호재(POSITIVE)·악재(NEGATIVE)
 * @param tradingDay 원본 공시를 작성한 거래일 번호 (1~7)
 * @param createdAt 뉴스 생성 요청 일시 (KST)
 * @param publishedAt 발행 일시 (KST)
 */
record NewsResponse(long newsId, long stockId, Long disclosureId, String title, String body,
                    NewsSentiment sentiment, int tradingDay, LocalDateTime createdAt,
                    LocalDateTime publishedAt) {

    static NewsResponse from(NewsItem n) {
        return new NewsResponse(n.newsId(), n.stockId(), n.disclosureId(), n.title(), n.body(),
                n.sentiment(), n.tradingDay(), n.createdAt(), n.publishedAt());
    }
}
