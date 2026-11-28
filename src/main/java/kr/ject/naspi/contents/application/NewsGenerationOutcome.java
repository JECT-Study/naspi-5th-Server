package kr.ject.naspi.contents.application;

/** AI 뉴스 생성 결과. */
public enum NewsGenerationOutcome {

    /** 뉴스가 게시됨. */
    PUBLISHED,
    /** 원문 내용이 부족해 뉴스를 생성하지 않음. */
    SKIPPED,
    /** 생성에 실패함. */
    FAILED
}
