package kr.ject.naspi.contents.api.feed;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.ject.naspi.contents.api.disclosure.DisclosureListItemResponse;
import kr.ject.naspi.contents.api.news.NewsListItemResponse;
import kr.ject.naspi.contents.application.DisclosureListItem;
import kr.ject.naspi.contents.application.FeedItem;
import kr.ject.naspi.contents.application.FeedItemType;
import kr.ject.naspi.contents.application.NewsListItem;

/**
 * 통합 피드 항목 응답. 종목별 공시 목록·뉴스 목록의 항목 응답이 그대로 피드 항목이 되고, {@code type}으로 구분한다.
 */
@Schema(oneOf = {DisclosureListItemResponse.class, NewsListItemResponse.class},
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "DISCLOSURE", schema = DisclosureListItemResponse.class),
            @DiscriminatorMapping(value = "NEWS", schema = NewsListItemResponse.class)
        })
public interface FeedItemResponse {

    /**
     * 항목 종류.
     *
     * @return {@code DISCLOSURE}(공시) 또는 {@code NEWS}(뉴스)
     */
    FeedItemType type();

    /**
     * 피드 항목을 응답으로 바꾼다.
     *
     * @param item 피드 항목
     * @return 종류에 맞는 항목 응답
     */
    static FeedItemResponse from(FeedItem item) {
        return switch (item) {
            case DisclosureListItem disclosure -> DisclosureListItemResponse.from(disclosure);
            case NewsListItem news -> NewsListItemResponse.from(news);
        };
    }
}
