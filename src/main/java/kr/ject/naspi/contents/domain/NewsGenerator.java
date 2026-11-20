package kr.ject.naspi.contents.domain;

/**
 * 공시를 바탕으로 AI 공시브리핑(뉴스)을 생성한다.
 *
 * <p>정책 (docs/ai-news/README.md "2.3. 재시도 정책"): 호출 오류·응답 처리 오류·출력 검증 실패는 최초 요청 포함 최대 3회 시도한다.
 * 미생성 판단은 정상 결과이므로 재시도하지 않는다. 구현은 이 한도를 지키고, 모두 실패하면
 * {@link NewsGenerationResult.Failed}를 반환한다 (예외를 던지지 않는다).
 *
 * <p>공시 게시 후 호출과 결과 저장은 {@code NewsGenerationService}가 맡는다.
 *
 * <p>TODO: 수동 재시도 API(CNT-07). 공시 미삭제이고 리그 진행 중일 때만 허용
 */
public interface NewsGenerator {

    /**
     * 뉴스를 생성한다. 외부 AI를 호출하므로 트랜잭션 밖에서 호출한다.
     *
     * @param source 공시 원문과 과거 공시 요약본
     * @return 생성, 미생성, 실패 중 하나
     */
    NewsGenerationResult generate(NewsSource source);
}
