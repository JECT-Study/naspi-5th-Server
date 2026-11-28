package kr.ject.naspi.contents.domain;

/**
 * 뉴스 생성 상태 (ERD: news.생성상태). {@link #PENDING}에서 나머지 중 하나로 끝난다.
 */
public enum NewsStatus {

    /** 생성 중. 아직 참여자에게 보이지 않는다. */
    PENDING,

    /** 생성되어 발행됨. 제목·본문·호재/악재가 채워져 있다. */
    PUBLISHED,

    /** 원본(공시·제보)에서 기사 소재를 찾지 못해 AI가 만들지 않음. 재시도하지 않는다. */
    SKIPPED,

    /** 자동 재시도까지 모두 실패. 수동 재시도 대상이다 (CNT-07). */
    FAILED
}
