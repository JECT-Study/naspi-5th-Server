package kr.ject.naspi.contents.application;

/**
 * 공시 게시 이벤트.
 *
 * <p>공시와 이미지가 저장된 같은 트랜잭션 안에서 발행된다. 공시 기반 AI 뉴스 생성(CNT-05)이 이 이벤트를
 * 커밋 이후 비동기로 받아 뉴스 생성을 시작한다. 정책상 뉴스 생성이 실패해도 공시는 유지되므로, 구독하는 쪽은
 * 공시 저장 트랜잭션에 영향을 주면 안 된다 ({@code @TransactionalEventListener(AFTER_COMMIT)} + {@code @Async}).
 *
 * @param disclosureId 공시 ID
 * @param leagueId 리그 ID
 * @param stockId 공시를 작성한 종목 ID
 * @param authorPlayerId 작성자(상장자) 플레이어 ID
 */
public record DisclosurePublished(long disclosureId, long leagueId, long stockId,
                                  long authorPlayerId) {
}
