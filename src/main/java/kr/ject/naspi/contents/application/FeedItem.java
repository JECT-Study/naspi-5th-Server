package kr.ject.naspi.contents.application;

/**
 * 통합 피드의 항목 1건. 종목별 공시 목록·뉴스 목록의 항목이 그대로 피드 항목이 된다.
 */
public sealed interface FeedItem permits DisclosureListItem, NewsListItem {

    /**
     * 항목 종류.
     *
     * @return 항목 종류
     */
    FeedItemType type();
}
