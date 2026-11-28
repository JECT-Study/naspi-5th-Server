package kr.ject.naspi.contents.domain;

/**
 * 뉴스 유형 (ERD: news.뉴스유형).
 */
public enum NewsType {

    /** 공시 기반 AI 뉴스. 공시 1건에 1개만 만든다. */
    DISCLOSURE,

    /** 사용자 제보 기반 뉴스. 원본 공시가 없다. */
    REPORT
}
