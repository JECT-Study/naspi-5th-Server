package kr.ject.naspi.contents.domain;

/**
 * 공시를 바탕으로 AI 공시브리핑(뉴스)을 생성한다.
 *
 * <p>정책 (docs/ai-news/README.md "2.3. 재시도 정책"): 호출 오류·응답 처리 오류·출력 검증 실패는 최초 요청 포함 최대 3회 시도한다.
 * 미생성 판단은 정상 결과이므로 재시도하지 않는다. 구현은 이 한도를 지키고, 모두 실패하면
 * {@link NewsGenerationResult.Failed}를 반환한다 (예외를 던지지 않는다).
 *
 * <p>TODO: 공시 기능 구현 시 application 계층에서 아래를 이어 붙인다.
 * <ul>
 *   <li>공시 게시 이벤트를 커밋 후({@code AFTER_COMMIT}) 비동기로 받아, 같은 리그·같은 종목의 과거 공시 요약본을
 *       오래된 순으로 조회해 {@link #generate} 호출</li>
 *   <li>결과 저장: Generated → 뉴스 저장·게시, 공시의 요약 컬럼에 summary 저장 / Skipped → 미생성 기록
 *       / Failed → 수동 재시도 가능 상태</li>
 *   <li>공시 1건당 뉴스 1개: 중복 발행 방지</li>
 *   <li>수동 재시도 API: 공시 미삭제이고 리그 진행 중일 때만 허용</li>
 *   <li>이미지 URL: 이미지 저장소가 정해지면 AI 제공자가 내려받을 수 있는 URL(또는 data URL)로 넘긴다</li>
 *   <li>과거 요약 조회 범위 결정: 삭제된 공시 포함 여부, 요약이 없는 공시(미생성·실패) 처리</li>
 * </ul>
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
