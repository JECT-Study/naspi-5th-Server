package kr.ject.naspi.contents.api.news;

import kr.ject.naspi.contents.application.RegeneratedNews;
import kr.ject.naspi.contents.domain.NewsStatus;

/**
 * 뉴스 재생성 요청 응답.
 *
 * @param newsId 뉴스 ID
 * @param status 요청 직후 상태 (생성 중 PENDING)
 */
record RegeneratedNewsResponse(long newsId, NewsStatus status) {

    static RegeneratedNewsResponse from(RegeneratedNews news) {
        return new RegeneratedNewsResponse(news.newsId(), news.status());
    }
}
