package kr.ject.naspi.contents.domain;

/**
 * 제보로 대상 종목의 뉴스 1개를 생성한다.
 */
public interface ReportNewsGenerator {

    /**
     * 제보 뉴스를 생성한다. 외부 호출을 하므로 트랜잭션 밖에서 호출한다.
     *
     * @return 생성, 미생성, 실패 중 하나. 예외를 던지지 않고 실패는 {@code Failed} 로 반환한다
     */
    ReportNewsGenerationResult generate(ReportNewsSource source);
}
