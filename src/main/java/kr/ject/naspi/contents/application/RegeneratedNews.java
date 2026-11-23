package kr.ject.naspi.contents.application;

import kr.ject.naspi.contents.domain.NewsStatus;

/**
 * 재생성을 요청한 뉴스.
 *
 * @param newsId 뉴스 ID
 * @param status 요청 직후 상태 (생성 중 PENDING)
 */
public record RegeneratedNews(long newsId, NewsStatus status) {
}
