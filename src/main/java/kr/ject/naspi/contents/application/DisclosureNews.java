package kr.ject.naspi.contents.application;

import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.contents.domain.NewsStatus;

/**
 * 공시로 만든 AI 뉴스. 발행({@link NewsStatus#PUBLISHED})·생성 실패({@link NewsStatus#FAILED}) 뉴스만 담고,
 * 제목·본문·호재/악재는 발행된 뉴스에만 있다.
 *
 * @param newsId 뉴스 ID
 * @param status 생성 상태 (PUBLISHED, FAILED)
 * @param title 제목. 생성 실패면 null
 * @param body 본문. 생성 실패면 null
 * @param sentiment 호재·악재. 생성 실패면 null
 */
public record DisclosureNews(long newsId, NewsStatus status, String title, String body,
                             NewsSentiment sentiment) {

    static DisclosureNews from(News news) {
        return new DisclosureNews(news.getId(), news.getStatus(), news.getTitle(),
                news.getBody(), news.getSentiment());
    }
}
