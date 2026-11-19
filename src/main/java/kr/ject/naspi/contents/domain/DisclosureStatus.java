package kr.ject.naspi.contents.domain;

/**
 * 공시 상태 (ERD: disclosure.상태).
 *
 * <p>공시 삭제는 행을 지우지 않고 상태만 바꾼다. 공시 기반 AI 뉴스는 삭제 후에도 유지되고 뉴스 자동 재시도도
 * 계속하므로 (docs/policies/v1/domain/contents.md "삭제"), 뉴스가 참조하는 공시 행을 남겨 둔다.
 */
public enum DisclosureStatus {

    /** 게시되어 리그 참여자가 볼 수 있다. */
    PUBLISHED,

    /** 작성자가 삭제했다. 공시와 공시에 달린 댓글은 조회할 수 없다. */
    DELETED
}
