package kr.ject.naspi.league.application;

/**
 * 리그 생성 이벤트.
 *
 * <p>리그와 방장 플레이어가 저장된 같은 트랜잭션 안에서 발행된다. market이 이 이벤트를 동기로 받아
 * 방장의 종목을 상장하며, 상장이 실패하면 예외가 전파되어 리그 생성도 롤백된다.
 * 정책상 방장의 상장까지 마쳐야 리그 생성이 완료되므로 방장이 입력한 종목 정보를 함께 담는다.
 *
 * @param leagueId 생성된 리그 ID
 * @param hostPlayerId 방장 플레이어 ID
 * @param stockName 방장이 입력한 종목 이름
 * @param stockDescription 방장이 입력한 종목 소개
 */
public record LeagueCreated(long leagueId, long hostPlayerId, String stockName,
        String stockDescription) {
}
