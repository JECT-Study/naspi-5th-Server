package kr.ject.naspi.contents.application;

/**
 * 공시 게시 이벤트. 생성 중(PENDING)인 공시 기반 AI 뉴스의 내용을 채우는 데 쓴다.
 *
 * <p>공시 저장 트랜잭션 안에서 발행된다. 구독하는 쪽은 커밋 후 비동기로 받아 공시 저장에 영향을 주지 않아야 한다.
 *
 * @param disclosureId 공시 ID
 * @param leagueId 리그 ID
 * @param stockId 공시를 작성한 종목 ID
 * @param authorPlayerId 작성자(상장자) 플레이어 ID
 */
public record DisclosurePublished(long disclosureId, long leagueId, long stockId,
                                  long authorPlayerId) {
}
