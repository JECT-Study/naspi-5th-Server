package kr.ject.naspi.contents.application;

/**
 * AI 뉴스 생성 결과 반영 이벤트. 원문 작성자에게 결과를 알리는 데 쓴다.
 *
 * <p>결과를 뉴스에 반영하는 트랜잭션 안에서 발행된다. 구독하는 쪽은 커밋 후 비동기로 받아 결과 반영에 영향을
 * 주지 않아야 한다.
 *
 * @param leagueId 리그 ID
 * @param newsId 뉴스 ID
 * @param authorPlayerId 원문 작성자(공시 작성자, 제보자) 플레이어 ID
 * @param type 원문 종류
 * @param outcome 생성 결과
 */
public record NewsGenerationCompleted(long leagueId, long newsId, long authorPlayerId,
                                      NewsGenerationType type, NewsGenerationOutcome outcome) {
}
