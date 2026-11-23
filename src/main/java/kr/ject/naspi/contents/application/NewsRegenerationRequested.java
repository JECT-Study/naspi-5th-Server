package kr.ject.naspi.contents.application;

/**
 * 뉴스 재생성이 요청되어 커밋되었음을 알리는 모듈 내부 이벤트. 공시 게시({@link DisclosurePublished})와
 * 구분하려고 따로 둔다.
 *
 * @param newsId 뉴스 ID
 * @param disclosureId 원본 공시 ID
 */
record NewsRegenerationRequested(long newsId, long disclosureId) {
}
