package kr.ject.naspi.contents.api.disclosure;

import kr.ject.naspi.contents.application.DisclosureNews;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.contents.domain.NewsStatus;

/**
 * 공시로 만든 AI 뉴스. 제목·본문·호재/악재는 {@code status}가 {@code PUBLISHED}일 때만 있다.
 *
 * @param newsId 뉴스 ID
 * @param status 생성 상태 ({@code PUBLISHED}, {@code FAILED})
 * @param title 제목. 생성 실패면 null
 * @param body 본문. 생성 실패면 null
 * @param sentiment 호재({@code POSITIVE})·악재({@code NEGATIVE}). 생성 실패면 null
 */
record DisclosureNewsResponse(long newsId, NewsStatus status, String title, String body,
                              NewsSentiment sentiment) {

    static DisclosureNewsResponse from(DisclosureNews news) {
        return news == null ? null : new DisclosureNewsResponse(news.newsId(), news.status(),
                news.title(), news.body(), news.sentiment());
    }
}
