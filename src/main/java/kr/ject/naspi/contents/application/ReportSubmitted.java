package kr.ject.naspi.contents.application;

/**
 * 제보 작성 이벤트. 생성 중(PENDING)인 제보 기반 AI 뉴스의 내용을 채우는 데 쓴다.
 *
 * <p>제보 저장 트랜잭션 안에서 발행된다. 구독하는 쪽은 커밋 후 비동기로 받아 제보 저장에 영향을 주지 않아야 한다.
 *
 * @param reportId 제보 ID
 * @param leagueId 리그 ID
 * @param stockId 대상 종목 ID
 * @param reporterPlayerId 제보자 플레이어 ID
 */
public record ReportSubmitted(long reportId, long leagueId, long stockId,
                              long reporterPlayerId) {
}
