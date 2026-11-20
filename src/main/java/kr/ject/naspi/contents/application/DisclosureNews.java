package kr.ject.naspi.contents.application;

import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.contents.domain.NewsStatus;

/**
 * 공시로 만든 AI 뉴스. 제목·본문·호재/악재는 발행된({@link NewsStatus#PUBLISHED}) 뉴스에만 있다.
 *
 * @param newsId 뉴스 ID
 * @param status 생성 상태
 * @param title 제목. 발행 전이면 null
 * @param body 본문. 발행 전이면 null
 * @param sentiment 호재·악재. 발행 전이면 null
 */
public record DisclosureNews(long newsId, NewsStatus status, String title, String body,
                             NewsSentiment sentiment) {

    static DisclosureNews from(News news) {
        return new DisclosureNews(news.getId(), news.getStatus(), news.getTitle(),
                news.getBody(), news.getSentiment());
    }
}
