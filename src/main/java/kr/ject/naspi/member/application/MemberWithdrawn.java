package kr.ject.naspi.member.application;

/**
 * 회원 탈퇴 이벤트.
 *
 * <p>회원 정보를 삭제하기 직전에 같은 트랜잭션 안에서 발행된다. 다른 모듈은 이 이벤트를 받아 탈퇴 후속 처리를 한다
 * (order: 미체결 주문 취소, asset: 현금 폐기, market: 종목 연결 해제, league: 리그 결과 처리).
 *
 * @param memberId 탈퇴한 회원 ID
 */
public record MemberWithdrawn(long memberId) {
}
